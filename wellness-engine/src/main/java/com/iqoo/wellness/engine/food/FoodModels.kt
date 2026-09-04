package com.iqoo.wellness.engine.food

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
    val estimatedAreaPortionGrams: Double = 200.0
)

data class NutritionProfile(
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double,
    val servingGrams: Double
)
