package com.iqoo.wellness.engine

import android.graphics.Bitmap
import com.iqoo.wellness.engine.activity.ActivitySummary
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.personalization.FoodPersonalizationEngine
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.scene.SceneType
import com.iqoo.wellness.engine.storage.DailyNutritionSummary
import com.iqoo.wellness.engine.storage.FoodHistoryEntity
import com.iqoo.wellness.engine.storage.FoodMemoryRecord
import com.iqoo.wellness.engine.storage.WellnessDatabase

/**
 * Public contract for the iQOO Wellness AI Engine.
 * Decoupled from Android UI and Camera implementations.
 */
interface WellnessEngine {

    val database: WellnessDatabase
    val personalizationEngine: FoodPersonalizationEngine

    suspend fun analyzeScene(frameData: ByteArray? = null): SceneType

    suspend fun analyzeFood(frameData: ByteArray? = null): PersonalizedNutritionResult?

    suspend fun analyzeFood(bitmap: Bitmap): PersonalizedNutritionResult? = analyzeFood(null)

    fun resetFoodScanning()

    fun resetPoseState()

    suspend fun confirmFoodPortion(
        foodId: String,
        foodName: String,
        confirmedPortionGrams: Double,
        ingredientQuantities: Map<String, Double> = emptyMap(),
        cookingMethod: String? = null,
        oilFatLevel: String? = null,
        oilGrams: Double? = null,
        fryingMethod: String? = null,
        substitutions: Map<String, String> = emptyMap(),
        userCorrections: String? = null
    )

    suspend fun calculateNutritionForPortion(
        foodId: String,
        foodName: String,
        grams: Double
    ): com.iqoo.wellness.engine.food.NutritionProfile?

    suspend fun getFoodEntity(foodIdOrName: String): com.iqoo.wellness.engine.storage.FoodEntity?

    suspend fun getAllSupportedFoods(): List<com.iqoo.wellness.engine.storage.FoodEntity>

    suspend fun analyzePose(
        frameData: ByteArray? = null,
        exerciseType: ExerciseType = ExerciseType.SQUAT
    ): PostureFeedback?

    /**
     * Executes exercise posture pipeline using a camera-derived Bitmap frame.
     */
    suspend fun analyzePose(
        bitmap: Bitmap,
        exerciseType: ExerciseType = ExerciseType.SQUAT
    ): PostureFeedback? = analyzePose(null, exerciseType)

    /**
     * Retrieves daily physical activity summary (steps and calories burned).
     * Enforces strict 21-day local SQLite retention policy.
     */
    suspend fun getActivitySummary(): ActivitySummary

    suspend fun getDailyNutritionSummary(dateTimestamp: Long = System.currentTimeMillis()): DailyNutritionSummary

    suspend fun getTodayConfirmedMeals(dateTimestamp: Long = System.currentTimeMillis()): List<FoodHistoryEntity>

    suspend fun getFoodMemory(): List<FoodMemoryRecord>

    suspend fun initializeOfflineData()
}