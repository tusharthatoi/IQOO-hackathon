package com.iqoo.wellness.engine

import android.graphics.Bitmap
import com.iqoo.wellness.engine.activity.ActivitySummary
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.personalization.FoodPersonalizationEngine
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.scene.SceneType
import com.iqoo.wellness.engine.storage.WellnessDatabase

/**
 * Public contract for the iQOO Wellness AI Engine.
 * Decoupled from Android UI and Camera implementations.
 */
interface WellnessEngine {

    val database: WellnessDatabase
    val personalizationEngine: FoodPersonalizationEngine

    /**
     * Executes lightweight cascaded scene classification (Food vs Exercise vs Normal).
     * Prevents running heavy models continuously to preserve iQOO device thermals and battery.
     */
    suspend fun analyzeScene(frameData: ByteArray? = null): SceneType

    /**
     * Executes the food intelligence pipeline:
     * Vision Model -> Recognized Food -> Structured RAG Retrieval -> Nutrition Calculator -> Personalized Result
     */
    suspend fun analyzeFood(frameData: ByteArray? = null): PersonalizedNutritionResult?

    /**
     * Executes food intelligence pipeline using a camera-derived Bitmap frame.
     */
    suspend fun analyzeFood(bitmap: Bitmap): PersonalizedNutritionResult? = analyzeFood(null)

    /**
     * Confirms or corrects the recognized food portion, ingredient breakdown, and cooking method.
     * Updates local Room/SQLite history for immediate personalization on subsequent scans.
     */
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

    /**
     * Deterministically calculates nutrition for a given portion without persisting to database.
     * Used for real-time live preview while user adjusts portion controls.
     */
    suspend fun calculateNutritionForPortion(
        foodId: String,
        foodName: String,
        grams: Double
    ): com.iqoo.wellness.engine.food.NutritionProfile?

    /**
     * Looks up food entity by ID or name.
     */
    suspend fun getFoodEntity(foodIdOrName: String): com.iqoo.wellness.engine.storage.FoodEntity?

    /**
     * Retrieves all supported/seeded foods from local database.
     */
    suspend fun getAllSupportedFoods(): List<com.iqoo.wellness.engine.storage.FoodEntity>

    /**
     * Executes the explainable exercise posture pipeline:
     * Pose Model -> 17 Landmarks -> Joint Angle Trigonometry -> Exercise FSM -> Form Feedback
     */
    suspend fun analyzePose(
        frameData: ByteArray? = null,
        exerciseType: ExerciseType = ExerciseType.SQUAT
    ): PostureFeedback?

    /**
     * Retrieves daily physical activity summary (steps and calories burned).
     * Enforces strict 21-day local SQLite retention policy.
     */
    suspend fun getActivitySummary(): ActivitySummary

    /**
     * Seeds initial offline foods database if empty.
     */
    suspend fun initializeOfflineData()
}
