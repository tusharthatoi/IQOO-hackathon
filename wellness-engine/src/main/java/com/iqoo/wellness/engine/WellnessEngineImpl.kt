package com.iqoo.wellness.engine

import android.content.Context
import com.iqoo.wellness.engine.activity.ActivitySummary
import com.iqoo.wellness.engine.activity.StepSensorTracker
import com.iqoo.wellness.engine.food.FoodRecognizer
import com.iqoo.wellness.engine.food.FoodSeedDatabase
import com.iqoo.wellness.engine.food.OnDeviceFoodRecognizer
import com.iqoo.wellness.engine.personalization.FoodPersonalizationEngine
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.BicepCurlStateMachine
import com.iqoo.wellness.engine.posture.ExerciseStateMachine
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.LungeStateMachine
import com.iqoo.wellness.engine.posture.OnDevicePoseDetector
import com.iqoo.wellness.engine.posture.PoseDetector
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.posture.PushUpStateMachine
import com.iqoo.wellness.engine.posture.ShoulderPressStateMachine
import com.iqoo.wellness.engine.posture.SquatStateMachine
import com.iqoo.wellness.engine.scene.LightweightSceneClassifier
import com.iqoo.wellness.engine.scene.SceneClassifier
import com.iqoo.wellness.engine.scene.SceneType
import com.iqoo.wellness.engine.storage.WellnessDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of the WellnessEngine coordinating vision,
 * structured retrieval, posture trigonometry, activity tracking, and Room persistence.
 */
class WellnessEngineImpl(
    override val database: WellnessDatabase,
    private val foodRecognizer: FoodRecognizer = OnDeviceFoodRecognizer(),
    private val poseDetector: PoseDetector = OnDevicePoseDetector(),
    private val sceneClassifier: SceneClassifier = LightweightSceneClassifier(),
    private val stepTracker: StepSensorTracker? = null
) : WellnessEngine {

    override val personalizationEngine: FoodPersonalizationEngine = FoodPersonalizationEngine(
        portionHistoryDao = database.portionHistoryDao(),
        preparationContextDao = database.foodPreparationContextDao(),
        foodHistoryDao = database.foodHistoryDao()
    )

    // Cached state machines for the 5 supported exercises
    private val stateMachines: Map<ExerciseType, ExerciseStateMachine> = mapOf(
        ExerciseType.SQUAT to SquatStateMachine(),
        ExerciseType.PUSH_UP to PushUpStateMachine(),
        ExerciseType.BICEP_CURL to BicepCurlStateMachine(),
        ExerciseType.LUNGE to LungeStateMachine(),
        ExerciseType.SHOULDER_PRESS to ShoulderPressStateMachine()
    )

    override suspend fun initializeOfflineData() = withContext(Dispatchers.IO) {
        val count = database.foodDao().getAllFoods().size
        if (count == 0) {
            database.foodDao().insertAllFoods(FoodSeedDatabase.SEED_FOODS)
        }
        // Run rolling retention purge on start
        stepTracker?.enforceRetentionPolicy()
        Unit
    }

    override suspend fun analyzeScene(frameData: ByteArray?): SceneType = withContext(Dispatchers.Default) {
        val detection = sceneClassifier.classifyScene(frameData)
        detection.detectedScene
    }

    override suspend fun analyzeFood(frameData: ByteArray?): PersonalizedNutritionResult? = withContext(Dispatchers.Default) {
        // 1. Food Recognition via on-device model
        val recognizedDishes = foodRecognizer.recognizeFood(frameData)
        val primaryDish = recognizedDishes.firstOrNull() ?: return@withContext null

        // 2. Query seed nutrition database for baseline per-100g data
        val seedFood = withContext(Dispatchers.IO) {
            database.foodDao().getFoodById(primaryDish.foodId)
                ?: database.foodDao().getFoodByName(primaryDish.name)
        }

        // 3. Execute Structured RAG: Local history retrieval + Personalized nutrition calculation
        withContext(Dispatchers.IO) {
            personalizationEngine.getPersonalizedNutrition(
                foodItem = primaryDish,
                seedFood = seedFood
            )
        }
    }

    override suspend fun confirmFoodPortion(
        foodId: String,
        foodName: String,
        confirmedPortionGrams: Double,
        ingredientQuantities: Map<String, Double>,
        cookingMethod: String?,
        oilFatLevel: String?,
        oilGrams: Double?,
        fryingMethod: String?,
        substitutions: Map<String, String>,
        userCorrections: String?
    ) = withContext(Dispatchers.IO) {
        val seedFood = database.foodDao().getFoodById(foodId)
            ?: database.foodDao().getFoodByName(foodName)

        val tempContext = com.iqoo.wellness.engine.personalization.PersonalizedFoodContext(
            foodId = foodId,
            foodName = foodName,
            hasHistory = true,
            typicalPortionGrams = confirmedPortionGrams,
            ingredientQuantities = ingredientQuantities,
            cookingMethod = cookingMethod,
            oilFatLevel = oilFatLevel,
            oilGrams = oilGrams,
            fryingMethod = fryingMethod,
            substitutions = substitutions,
            userCorrections = userCorrections,
            explanation = "User confirmed"
        )

        val nutrition = if (seedFood != null) {
            com.iqoo.wellness.engine.food.NutritionCalculator.calculatePersonalized(seedFood, tempContext)
        } else null

        personalizationEngine.saveUserConfirmation(
            foodId = foodId,
            foodName = foodName,
            confirmedPortionGrams = confirmedPortionGrams,
            ingredientQuantities = ingredientQuantities,
            cookingMethod = cookingMethod,
            oilFatLevel = oilFatLevel,
            oilGrams = oilGrams,
            fryingMethod = fryingMethod,
            substitutions = substitutions,
            userCorrections = userCorrections,
            calculatedNutrition = nutrition
        )
    }

    override suspend fun analyzePose(
        frameData: ByteArray?,
        exerciseType: ExerciseType
    ): PostureFeedback? = withContext(Dispatchers.Default) {
        val landmarks = poseDetector.detectPose(frameData)
        if (landmarks.isEmpty()) return@withContext null

        val fsm = stateMachines[exerciseType] ?: return@withContext null
        fsm.processLandmarks(landmarks)
    }

    override suspend fun getActivitySummary(): ActivitySummary = withContext(Dispatchers.IO) {
        stepTracker?.getActivitySummary() ?: ActivitySummary(
            steps = 0,
            caloriesBurned = 0.0,
            estimatedDistanceMeters = 0.0,
            averageSpeedKmh = 0.0,
            activeDurationMinutes = 0,
            retentionPolicyDays = 21,
            isEstimated = true
        )
    }

    fun getStateMachine(type: ExerciseType): ExerciseStateMachine? {
        return stateMachines[type]
    }

    companion object {
        fun create(context: Context): WellnessEngine {
            val db = WellnessDatabase.getInstance(context)
            val tracker = StepSensorTracker(context, db.dailyActivityDao())
            tracker.startTracking()
            return WellnessEngineImpl(
                database = db,
                stepTracker = tracker
            )
        }

        fun createForTesting(
            database: WellnessDatabase,
            foodRecognizer: FoodRecognizer = OnDeviceFoodRecognizer(),
            poseDetector: PoseDetector = OnDevicePoseDetector(),
            sceneClassifier: SceneClassifier = LightweightSceneClassifier(),
            stepTracker: StepSensorTracker? = null
        ): WellnessEngine {
            return WellnessEngineImpl(
                database = database,
                foodRecognizer = foodRecognizer,
                poseDetector = poseDetector,
                sceneClassifier = sceneClassifier,
                stepTracker = stepTracker
            )
        }
    }
}
