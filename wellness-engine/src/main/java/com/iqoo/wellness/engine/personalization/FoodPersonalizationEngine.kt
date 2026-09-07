package com.iqoo.wellness.engine.personalization

import com.iqoo.wellness.engine.food.NutritionCalculator
import com.iqoo.wellness.engine.food.NutritionProfile
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.storage.FoodEntity
import com.iqoo.wellness.engine.storage.FoodHistoryDao
import com.iqoo.wellness.engine.storage.FoodHistoryEntity
import com.iqoo.wellness.engine.storage.FoodPreparationContextDao
import com.iqoo.wellness.engine.storage.FoodPreparationContextEntity
import com.iqoo.wellness.engine.storage.PortionHistoryDao
import com.iqoo.wellness.engine.storage.PortionHistoryEntity
import com.iqoo.wellness.engine.storage.WellnessDatabase
import org.json.JSONObject

class FoodPersonalizationEngine(
    private val portionHistoryDao: PortionHistoryDao,
    private val preparationContextDao: FoodPreparationContextDao,
    private val foodHistoryDao: FoodHistoryDao
) {

    constructor(database: WellnessDatabase) : this(
        portionHistoryDao = database.portionHistoryDao(),
        preparationContextDao = database.foodPreparationContextDao(),
        foodHistoryDao = database.foodHistoryDao()
    )

    suspend fun getPersonalizedNutrition(
        foodItem: RecognizedFoodItem,
        seedFood: FoodEntity?
    ): PersonalizedNutritionResult {
        val foodId = foodItem.foodId
        val foodName = foodItem.name

        val avgPortion = portionHistoryDao.getAverageRecentPortion(foodId, foodName)
        val prepContext = preparationContextDao.getLatestContextForFood(foodId)
            ?: preparationContextDao.getLatestContextByFoodName(foodName)

        val hasHistory = (avgPortion != null && avgPortion > 0.0) || prepContext != null

        if (hasHistory) {
            val typicalGrams = avgPortion ?: prepContext?.typicalPortionGrams ?: seedFood?.defaultGrams ?: foodItem.estimatedAreaPortionGrams
            val ingredientMap = parseJsonMap(prepContext?.ingredientQuantitiesJson)
            val substitutionsMap = parseJsonStringMap(prepContext?.substitutionsJson)

            val explanation = buildExplanation(
                foodName = foodName,
                portion = typicalGrams,
                cookingMethod = prepContext?.cookingMethod,
                oilLevel = prepContext?.oilFatLevel,
                recurrence = prepContext?.recurrenceCount ?: 1
            )

            val context = PersonalizedFoodContext(
                foodId = foodId,
                foodName = foodName,
                hasHistory = true,
                typicalPortionGrams = typicalGrams,
                ingredientQuantities = ingredientMap,
                cookingMethod = prepContext?.cookingMethod,
                oilFatLevel = prepContext?.oilFatLevel,
                oilGrams = prepContext?.oilGrams,
                fryingMethod = prepContext?.fryingMethod,
                substitutions = substitutionsMap,
                userCorrections = prepContext?.userCorrections,
                recurrenceCount = prepContext?.recurrenceCount ?: 1,
                explanation = explanation
            )

            val nutrition = if (seedFood != null) {
                NutritionCalculator.calculatePersonalized(seedFood, context)
            } else {
                NutritionCalculator.unavailable(typicalGrams)
            }

            println("[PERSONALIZATION_RAG] Retrieved portion for $foodName: $typicalGrams g (source: ${if (avgPortion != null) "portion_history" else "prep_context"}), cooking: ${prepContext?.cookingMethod ?: "baseline"}")

            return PersonalizedNutritionResult(
                foodItem = foodItem,
                context = context,
                nutrition = nutrition,
                isPersonalized = seedFood != null,
                displayHeading = if (seedFood != null) "$foodName" else "$foodName — Uncatalogued",
                displaySubtext = context.explanation
            )
        } else {
            val defaultGrams = seedFood?.defaultGrams ?: foodItem.estimatedAreaPortionGrams
            val defaultNutrition = if (seedFood != null) {
                NutritionCalculator.calculateBaseline(seedFood, defaultGrams)
            } else {
                NutritionCalculator.unavailable(defaultGrams)
            }

            val explanation = if (seedFood != null) {
                "Suggested serving: ~${defaultGrams.toInt()}g (Standard estimate)"
            } else {
                "Nutrition data unavailable for this food."
            }

            val context = PersonalizedFoodContext(
                foodId = foodId,
                foodName = foodName,
                hasHistory = false,
                typicalPortionGrams = defaultGrams,
                explanation = explanation
            )

            println("[PERSONALIZATION_RAG] No prior memory for $foodName. Falling back to baseline portion: $defaultGrams g")

            return PersonalizedNutritionResult(
                foodItem = foodItem,
                context = context,
                nutrition = defaultNutrition,
                isPersonalized = false,
                displayHeading = if (seedFood != null) "$foodName" else "$foodName — Uncatalogued",
                displaySubtext = context.explanation
            )
        }
    }

    suspend fun saveUserConfirmation(
        foodId: String,
        foodName: String,
        confirmedPortionGrams: Double,
        ingredientQuantities: Map<String, Double> = emptyMap(),
        cookingMethod: String? = null,
        oilFatLevel: String? = null,
        oilGrams: Double? = null,
        fryingMethod: String? = null,
        substitutions: Map<String, String> = emptyMap(),
        userCorrections: String? = null,
        calculatedNutrition: NutritionProfile? = null
    ) {
        val now = System.currentTimeMillis()

        portionHistoryDao.insertPortion(
            PortionHistoryEntity(
                foodId = foodId,
                foodName = foodName,
                portionGrams = confirmedPortionGrams,
                timestamp = now
            )
        )

        val existing = preparationContextDao.getLatestContextForFood(foodId)
            ?: preparationContextDao.getLatestContextByFoodName(foodName)
        val newRecurrence = (existing?.recurrenceCount ?: 0) + 1

        val contextEntity = FoodPreparationContextEntity(
            id = existing?.id ?: 0,
            foodId = foodId,
            foodName = foodName,
            typicalPortionGrams = confirmedPortionGrams,
            userConfirmedQuantity = confirmedPortionGrams,
            ingredientQuantitiesJson = if (ingredientQuantities.isNotEmpty()) serializeJsonMap(ingredientQuantities) else existing?.ingredientQuantitiesJson,
            cookingMethod = cookingMethod ?: existing?.cookingMethod,
            oilFatLevel = oilFatLevel ?: existing?.oilFatLevel,
            oilGrams = oilGrams ?: existing?.oilGrams,
            fryingMethod = fryingMethod ?: existing?.fryingMethod,
            substitutionsJson = if (substitutions.isNotEmpty()) serializeJsonStringMap(substitutions) else existing?.substitutionsJson,
            userCorrections = userCorrections ?: existing?.userCorrections,
            recurrenceCount = newRecurrence,
            lastCaloriesEstimate = calculatedNutrition?.calories,
            lastProteinEstimate = calculatedNutrition?.protein,
            lastCarbsEstimate = calculatedNutrition?.carbohydrates,
            lastFatEstimate = calculatedNutrition?.fat,
            timestamp = now
        )
        preparationContextDao.insertContext(contextEntity)

        if (calculatedNutrition != null && calculatedNutrition.isAvailable) {
            foodHistoryDao.insertFoodHistory(
                FoodHistoryEntity(
                    foodId = foodId,
                    foodName = foodName,
                    portionGrams = confirmedPortionGrams,
                    calories = calculatedNutrition.calories,
                    protein = calculatedNutrition.protein,
                    carbs = calculatedNutrition.carbohydrates,
                    fat = calculatedNutrition.fat,
                    fiber = calculatedNutrition.fiber,
                    isUserConfirmed = true,
                    timestamp = now
                )
            )
        }
    }

    private fun buildExplanation(
        foodName: String,
        portion: Double,
        cookingMethod: String?,
        oilLevel: String?,
        recurrence: Int
    ): String {
        val details = mutableListOf<String>()
        details.add("Your usual portion: ~${portion.toInt()}g")
        if (!cookingMethod.isNullOrBlank()) {
            details.add(cookingMethod)
        }
        if (!oilLevel.isNullOrBlank()) {
            details.add("$oilLevel oil")
        }
        details.add("Based on your previous meals")
        return details.joinToString(" • ")
    }

    private fun serializeJsonMap(map: Map<String, Double>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun serializeJsonStringMap(map: Map<String, String>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun parseJsonMap(jsonStr: String?): Map<String, Double> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, Double>()
        return try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optDouble(key, 0.0)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseJsonStringMap(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        return try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optString(key, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}