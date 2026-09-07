package com.iqoo.wellness.engine.food

import android.graphics.Bitmap

enum class FoodResultState {
    SCANNING,
    FOOD_DETECTED,
    LOW_CONFIDENCE,
    NOT_FOOD
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class RecognizedFoodItem(
    val foodId: String,
    val name: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
    val estimatedAreaPortionGrams: Double = 200.0,
    val state: FoodResultState = FoodResultState.FOOD_DETECTED,
    val stateMessage: String = "",
    val thumbnail: Bitmap? = null
)

data class NutritionProfile(
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double,
    val servingGrams: Double,
    val isAvailable: Boolean = true
)
