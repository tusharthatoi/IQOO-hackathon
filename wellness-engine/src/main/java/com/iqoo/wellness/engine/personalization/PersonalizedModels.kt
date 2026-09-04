package com.iqoo.wellness.engine.personalization

import com.iqoo.wellness.engine.food.NutritionProfile
import com.iqoo.wellness.engine.food.RecognizedFoodItem

/**
 * Structured personal context retrieved from local SQLite/Room history.
 */
data class PersonalizedFoodContext(
    val foodId: String,
    val foodName: String,
    val hasHistory: Boolean,
    val typicalPortionGrams: Double,
    val userConfirmedQuantity: Double? = null,
    val ingredientQuantities: Map<String, Double> = emptyMap(),
    val cookingMethod: String? = null,
    val oilFatLevel: String? = null,
    val oilGrams: Double? = null,
    val fryingMethod: String? = null,
    val substitutions: Map<String, String> = emptyMap(),
    val userCorrections: String? = null,
    val recurrenceCount: Int = 0,
    val explanation: String
)

/**
 * Result emitted to UI overlays and camera HUD.
 */
data class PersonalizedNutritionResult(
    val foodItem: RecognizedFoodItem,
    val context: PersonalizedFoodContext,
    val nutrition: NutritionProfile,
    val isPersonalized: Boolean,
    val displayHeading: String,
    val displaySubtext: String
)
