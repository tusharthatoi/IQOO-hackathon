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
import org.json.JSONObject

/**
 * Local Structured Retrieval & Personalization Engine (Structured RAG).
 *
 * Implements the core pipeline:
 * Fixed Food Recognition Model -> Local User History Retrieval -> Personalized Context -> Nutrition Calculation Engine -> Personalized Estimate.
 */
class FoodPersonalizationEngine(
    private val portionHistoryDao: PortionHistoryDao,
    private val preparationContextDao: FoodPreparationContextDao,
    private val foodHistoryDao: FoodHistoryDao
) {

    /**
     * Retrieves user history for the recognized food and computes the personalized nutrition result.
     * If no history exists, returns a default estimate based on the seed food database.
     */
    suspend fun getPersonalizedNutrition(
        foodItem: RecognizedFoodItem,
        seedFood: FoodEntity?
    ): PersonalizedNutritionResult {
        val foodId = foodItem.foodId
        val foodName = foodItem.name

        // 1. Structured Retrieval from SQLite/Room
        val prepContext = preparationContextDao.getLatestContextForFood(foodId)
            ?: preparationContextDao.getLatestContextByFoodName(foodName)
        val avgPortion = portionHistoryDao.getAverageRecentPortion(foodId)

        // 2. Determine if meaningful user history exists
        if (prepContext != null || avgPortion != null) {
            val typicalGrams = prepContext?.typicalPortionGrams
                ?: avgPortion
                ?: seedFood?.defaultGrams
                ?: foodItem.estimatedAreaPortionGrams

            // Parse ingredients if stored
            val ingredientMap = parseJsonMap(prepContext?.ingredientQuantitiesJson)
            val substitutionsMap = parseJsonStringMap(prepContext?.substitutionsJson)

            val context = PersonalizedFoodContext(
                foodId = foodId,
                foodName = foodName,
                hasHistory = true,
                typicalPortionGrams = typicalGrams,
                userConfirmedQuantity = prepContext?.userConfirmedQuantity,
                ingredientQuantities = ingredientMap,
                cookingMethod = prepContext?.cookingMethod,
                oilFatLevel = prepContext?.oilFatLevel,
                oilGrams = prepContext?.oilGrams,
                fryingMethod = prepContext?.fryingMethod,
                substitutions = substitutionsMap,
                userCorrections = prepContext?.userCorrections,
                recurrenceCount = prepContext?.recurrenceCount ?: 1,
                explanation = buildExplanation(
                    foodName = foodName,
                    portion = typicalGrams,
                    cookingMethod = prepContext?.cookingMethod,
                    oilLevel = prepContext?.oilFatLevel,
                    recurrence = prepContext?.recurrenceCount ?: 1
                )
            )

            // 3. Compute personalized nutrition using retrieved context
            val nutrition = if (seedFood != null) {
                NutritionCalculator.calculatePersonalized(seedFood, context)
            } else {
                // Fallback baseline heuristic if dish is not in seed DB
                generateFallbackNutrition(typicalGrams)
            }

            return PersonalizedNutritionResult(
                foodItem = foodItem,
                context = context,
                nutrition = nutrition,
                isPersonalized = true,
                displayHeading = "$foodName — Personalized estimate",
                displaySubtext = context.explanation
            )
        } else {
            // First encounter: No history found -> use seed defaults
            val defaultGrams = seedFood?.defaultGrams ?: foodItem.estimatedAreaPortionGrams
            val defaultNutrition = if (seedFood != null) {
                NutritionCalculator.calculateBaseline(seedFood, defaultGrams)
            } else {
                generateFallbackNutrition(defaultGrams)
            }

            val context = PersonalizedFoodContext(
                foodId = foodId,
                foodName = foodName,
                hasHistory = false,
                typicalPortionGrams = defaultGrams,
                explanation = "First scan of $foodName: using standard ~${defaultGrams.toInt()}g portion. Confirm or adjust to personalize."
            )

            return PersonalizedNutritionResult(
                foodItem = foodItem,
                context = context,
                nutrition = defaultNutrition,
                isPersonalized = false,
                displayHeading = "$foodName — Standard estimate",
                displaySubtext = context.explanation
            )
        }
    }

    /**
     * Stores user confirmation or correction locally in SQLite/Room.
     * Future scans retrieve this data immediately.
     */
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
        // 1. Log to portion history table
        portionHistoryDao.insertPortion(
            PortionHistoryEntity(
                foodId = foodId,
                foodName = foodName,
                portionGrams = confirmedPortionGrams,
                timestamp = System.currentTimeMillis()
            )
        )

        // 2. Query existing context to update recurrence count and rolling preferences
        val existing = preparationContextDao.getLatestContextForFood(foodId)
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
            timestamp = System.currentTimeMillis()
        )

        preparationContextDao.insertContext(contextEntity)

        // 3. Log to general food history log
        if (calculatedNutrition != null) {
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
                    timestamp = System.currentTimeMillis()
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
        details.add("Your typical ~${portion.toInt()}g")
        if (!cookingMethod.isNullOrBlank()) {
            details.add(cookingMethod)
        }
        if (!oilLevel.isNullOrBlank()) {
            details.add("$oilLevel oil")
        }
        val detailStr = details.joinToString(", ")
        return "Learned from $recurrence scan(s): $detailStr"
    }

    private fun generateFallbackNutrition(grams: Double): NutritionProfile {
        val factor = grams / 100.0
        return NutritionProfile(
            calories = 150.0 * factor,
            protein = 5.0 * factor,
            carbohydrates = 20.0 * factor,
            fat = 5.0 * factor,
            fiber = 2.0 * factor,
            servingGrams = grams
        )
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
