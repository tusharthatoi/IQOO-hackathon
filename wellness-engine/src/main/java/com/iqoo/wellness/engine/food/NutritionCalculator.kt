package com.iqoo.wellness.engine.food

import com.iqoo.wellness.engine.personalization.PersonalizedFoodContext
import com.iqoo.wellness.engine.storage.FoodEntity
import kotlin.math.roundToInt

/**
 * Deterministic, explainable nutrition calculator.
 * Computes macronutrients and calories from baseline seed data scaled by grams,
 * incorporating personalized context (ingredients, cooking method, oil level, substitutions).
 */
object NutritionCalculator {

    fun calculateBaseline(food: FoodEntity, grams: Double): NutritionProfile {
        val factor = grams / 100.0
        return NutritionProfile(
            calories = roundToOneDecimal(food.caloriesPer100g * factor),
            protein = roundToOneDecimal(food.proteinPer100g * factor),
            carbohydrates = roundToOneDecimal(food.carbsPer100g * factor),
            fat = roundToOneDecimal(food.fatPer100g * factor),
            fiber = roundToOneDecimal(food.fiberPer100g * factor),
            servingGrams = grams
        )
    }

    fun calculatePersonalized(
        food: FoodEntity,
        context: PersonalizedFoodContext
    ): NutritionProfile {
        val targetGrams = context.typicalPortionGrams
        val baseline = calculateBaseline(food, targetGrams)

        var calories = baseline.calories
        var protein = baseline.protein
        var carbs = baseline.carbohydrates
        var fat = baseline.fat
        var fiber = baseline.fiber

        // 1. Ingredient Breakdown Adjustment (e.g. rice vs chicken ratio)
        val riceGrams = context.ingredientQuantities["rice"] ?: context.ingredientQuantities["rice_g"]
        val chickenGrams = context.ingredientQuantities["chicken"] ?: context.ingredientQuantities["chicken_g"]
        if (riceGrams != null && chickenGrams != null && (riceGrams + chickenGrams) > 0) {
            // Cooked white rice: ~130 kcal/100g, 2.7g P, 28g C, 0.3g F
            // Cooked chicken breast/thigh blend: ~165 kcal/100g, 25g P, 0g C, 7g F
            val riceCalories = (riceGrams / 100.0) * 130.0
            val riceProtein = (riceGrams / 100.0) * 2.7
            val riceCarbs = (riceGrams / 100.0) * 28.2
            val riceFat = (riceGrams / 100.0) * 0.3

            val chickenCalories = (chickenGrams / 100.0) * 165.0
            val chickenProtein = (chickenGrams / 100.0) * 25.0
            val chickenCarbs = 0.0
            val chickenFat = (chickenGrams / 100.0) * 7.0

            calories = riceCalories + chickenCalories
            protein = riceProtein + chickenProtein
            carbs = riceCarbs + chickenCarbs
            fat = riceFat + chickenFat
        }

        // 2. Oil / Fat Adjustment
        // 1g oil/ghee = 9 kcal, 1g fat
        if (context.oilGrams != null && context.oilGrams > 0) {
            val oilCal = context.oilGrams * 9.0
            val oilFat = context.oilGrams * 1.0
            // Incorporate known oil amount
            calories += oilCal
            fat += oilFat
        } else if (context.oilFatLevel != null) {
            when (context.oilFatLevel.uppercase()) {
                "LIGHT" -> {
                    // 20% less fat than baseline
                    fat *= 0.8
                    calories -= (baseline.fat * 0.2 * 9.0)
                }
                "HEAVY" -> {
                    // 30% more fat than baseline
                    fat *= 1.3
                    calories += (baseline.fat * 0.3 * 9.0)
                }
                "NONE" -> {
                    calories -= (fat * 9.0)
                    fat = 0.5 // residual fat
                }
            }
        }

        // 3. Cooking / Frying Method Adjustment
        when (context.fryingMethod?.uppercase() ?: context.cookingMethod?.uppercase()) {
            "DEEP_FRIED" -> {
                // Absorbs roughly 8-12% additional fat by weight
                val addedFat = targetGrams * 0.08
                fat += addedFat
                calories += addedFat * 9.0
            }
            "AIR_FRIED", "BAKED", "STEAMED", "PRESSURE_COOKED", "PRESSURE-COOKED" -> {
                // Lean cooking methods preserve nutrients without excess oil absorption
            }
        }

        // 4. Ingredient Substitutions (e.g. brown rice adds dietary fiber)
        val grainSub = context.substitutions["grain"] ?: context.substitutions["rice"]
        if (grainSub?.contains("brown", ignoreCase = true) == true) {
            fiber += (targetGrams / 100.0) * 1.8 // +1.8g fiber per 100g
        }

        return NutritionProfile(
            calories = roundToOneDecimal(calories),
            protein = roundToOneDecimal(protein),
            carbohydrates = roundToOneDecimal(carbs),
            fat = roundToOneDecimal(fat),
            fiber = roundToOneDecimal(fiber),
            servingGrams = targetGrams
        )
    }

    private fun roundToOneDecimal(value: Double): Double {
        return (value * 10.0).roundToInt() / 10.0
    }
}
