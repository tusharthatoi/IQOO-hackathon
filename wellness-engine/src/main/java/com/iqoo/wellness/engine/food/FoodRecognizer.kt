package com.iqoo.wellness.engine.food

import android.graphics.Bitmap

/**
 * On-device food recognition abstraction.
 * Decouples the computer vision inference layer from personalization and business logic.
 */
interface FoodRecognizer {
    suspend fun recognizeFood(imageData: ByteArray? = null): List<RecognizedFoodItem>
    suspend fun recognizeFood(bitmap: Bitmap): List<RecognizedFoodItem> = recognizeFood(null)
}

/**
 * Default on-device food recognizer with mock/offline capability for testing and demonstration.
 */
class OnDeviceFoodRecognizer(
    private val candidateDishes: List<RecognizedFoodItem> = listOf(
        RecognizedFoodItem(
            foodId = "biryani_01",
            name = "Biryani",
            confidence = 0.94f,
            boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
            estimatedAreaPortionGrams = 250.0
        ),
        RecognizedFoodItem(
            foodId = "dosa_01",
            name = "Dosa",
            confidence = 0.91f,
            boundingBox = BoundingBox(0.1f, 0.25f, 0.9f, 0.75f),
            estimatedAreaPortionGrams = 150.0
        ),
        RecognizedFoodItem(
            foodId = "paneer_tikka_01",
            name = "Paneer Tikka",
            confidence = 0.88f,
            boundingBox = BoundingBox(0.2f, 0.3f, 0.8f, 0.7f),
            estimatedAreaPortionGrams = 180.0
        )
    )
) : FoodRecognizer {

    private var activeIndex = 0

    override suspend fun recognizeFood(imageData: ByteArray?): List<RecognizedFoodItem> {
        if (candidateDishes.isEmpty()) return emptyList()
        val item = candidateDishes[activeIndex % candidateDishes.size]
        return listOf(item)
    }

    fun setRecognizedFood(dish: RecognizedFoodItem) {
        // Allows deterministic test and runtime simulation
        (candidateDishes as? MutableList)?.let {
            it.clear()
            it.add(dish)
        }
    }
}
