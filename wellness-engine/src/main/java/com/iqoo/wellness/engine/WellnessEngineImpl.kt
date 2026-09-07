package com.iqoo.wellness.engine

import android.content.Context
import android.graphics.Bitmap
import com.iqoo.wellness.engine.activity.ActivitySummary
import com.iqoo.wellness.engine.activity.StepSensorTracker
import com.iqoo.wellness.engine.food.FoodRecognizer
import com.iqoo.wellness.engine.food.FoodSeedDatabase
import com.iqoo.wellness.engine.food.OnDeviceFoodRecognizer
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.food.TFLiteFoodRecognizer
import com.iqoo.wellness.engine.personalization.FoodPersonalizationEngine
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.BicepCurlStateMachine
import com.iqoo.wellness.engine.posture.ExerciseStateMachine
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.LungeStateMachine
import com.iqoo.wellness.engine.posture.ONNXExercisePoseDetector
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
        if (count < FoodSeedDatabase.SEED_FOODS.size) {
            database.foodDao().insertAllFoods(FoodSeedDatabase.SEED_FOODS)
        }
        // Run rolling retention purge on start
        stepTracker?.enforceRetentionPolicy()
        Unit
    }

    override suspend fun analyzeScene(frameData: ByteArray?): SceneType = withContext(Dispatchers.Default) {
        val detection = sceneClassifier.classifyScene(frameData)
        android.util.Log.d("IQOO_WELLNESS", "[SCENE_ROUTER] Classifier=${sceneClassifier.javaClass.simpleName}, result=${detection.detectedScene}, confidence=${detection.confidence}")
        detection.detectedScene
    }

    override suspend fun analyzeFood(bitmap: Bitmap): PersonalizedNutritionResult? = withContext(Dispatchers.Default) {
        val recognizedDishes = foodRecognizer.recognizeFood(bitmap)
        processRecognizedDishes(recognizedDishes)
    }

    override suspend fun analyzeFood(frameData: ByteArray?): PersonalizedNutritionResult? = withContext(Dispatchers.Default) {
        val recognizedDishes = foodRecognizer.recognizeFood(frameData)
        processRecognizedDishes(recognizedDishes)
    }

    private suspend fun processRecognizedDishes(recognizedDishes: List<RecognizedFoodItem>): PersonalizedNutritionResult? {
        val primaryDish = recognizedDishes.firstOrNull() ?: return null
        android.util.Log.d("IQOO_WELLNESS", "[FOOD_PIPELINE] Recognizer=${foodRecognizer.javaClass.simpleName}, recognized=${primaryDish.name} (id=${primaryDish.foodId}), state=${primaryDish.state}, confidence=${primaryDish.confidence}")

        when (primaryDish.state) {
            com.iqoo.wellness.engine.food.FoodResultState.NOT_FOOD -> {
                return PersonalizedNutritionResult(
                    foodItem = primaryDish,
                    context = com.iqoo.wellness.engine.personalization.PersonalizedFoodContext(
                        foodId = "not_food",
                        foodName = "Not Food",
                        hasHistory = false,
                        typicalPortionGrams = 0.0,
                        explanation = primaryDish.stateMessage.ifBlank { "This doesn't look like a food item. Try scanning a dish or meal." }
                    ),
                    nutrition = com.iqoo.wellness.engine.food.NutritionProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                    isPersonalized = false,
                    displayHeading = "Not Food",
                    displaySubtext = primaryDish.stateMessage.ifBlank { "This doesn't look like a food item. Try scanning a dish or meal." }
                )
            }
            com.iqoo.wellness.engine.food.FoodResultState.LOW_CONFIDENCE -> {
                return PersonalizedNutritionResult(
                    foodItem = primaryDish,
                    context = com.iqoo.wellness.engine.personalization.PersonalizedFoodContext(
                        foodId = "unknown",
                        foodName = "Unknown Food",
                        hasHistory = false,
                        typicalPortionGrams = 0.0,
                        explanation = primaryDish.stateMessage.ifBlank { "Hold steady and point the camera at a food item." }
                    ),
                    nutrition = com.iqoo.wellness.engine.food.NutritionProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                    isPersonalized = false,
                    displayHeading = "Unknown Food",
                    displaySubtext = primaryDish.stateMessage.ifBlank { "Hold steady and point the camera at a food item." }
                )
            }
            com.iqoo.wellness.engine.food.FoodResultState.SCANNING -> {
                return PersonalizedNutritionResult(
                    foodItem = primaryDish,
                    context = com.iqoo.wellness.engine.personalization.PersonalizedFoodContext(
                        foodId = "scanning",
                        foodName = "Scanning food...",
                        hasHistory = false,
                        typicalPortionGrams = 0.0,
                        explanation = "Scanning food..."
                    ),
                    nutrition = com.iqoo.wellness.engine.food.NutritionProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                    isPersonalized = false,
                    displayHeading = "Scanning food...",
                    displaySubtext = "Hold steady for best results"
                )
            }
            com.iqoo.wellness.engine.food.FoodResultState.FOOD_DETECTED -> {
                // Query seed nutrition database for baseline per-100g data
                val seedFood = withContext(Dispatchers.IO) {
                    database.foodDao().getFoodById(primaryDish.foodId)
                        ?: database.foodDao().getFoodByName(primaryDish.name)
                }

                // Execute Structured RAG: Local history retrieval + Personalized nutrition calculation
                return withContext(Dispatchers.IO) {
                    val result = personalizationEngine.getPersonalizedNutrition(
                        foodItem = primaryDish,
                        seedFood = seedFood
                    )
                    android.util.Log.d("IQOO_WELLNESS", "[NUTRITION_ENGINE] Result for ${primaryDish.name}: ${result.nutrition.calories} kcal, personalized=${result.isPersonalized}, heading='${result.displayHeading}'")
                    result
                }
            }
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

    override suspend fun calculateNutritionForPortion(
        foodId: String,
        foodName: String,
        grams: Double
    ): com.iqoo.wellness.engine.food.NutritionProfile? = withContext(Dispatchers.IO) {
        val seedFood = database.foodDao().getFoodById(foodId)
            ?: database.foodDao().getFoodByName(foodName)
            ?: return@withContext null

        val prepContext = database.foodPreparationContextDao().getLatestContextForFood(foodId)
            ?: database.foodPreparationContextDao().getLatestContextByFoodName(foodName)

        if (prepContext != null) {
            val context = com.iqoo.wellness.engine.personalization.PersonalizedFoodContext(
                foodId = foodId,
                foodName = foodName,
                hasHistory = true,
                typicalPortionGrams = grams,
                ingredientQuantities = emptyMap(),
                cookingMethod = prepContext.cookingMethod,
                oilFatLevel = prepContext.oilFatLevel,
                oilGrams = prepContext.oilGrams,
                fryingMethod = prepContext.fryingMethod,
                explanation = "Preview"
            )
            com.iqoo.wellness.engine.food.NutritionCalculator.calculatePersonalized(seedFood, context)
        } else {
            com.iqoo.wellness.engine.food.NutritionCalculator.calculateBaseline(seedFood, grams)
        }
    }

    override suspend fun getFoodEntity(foodIdOrName: String): com.iqoo.wellness.engine.storage.FoodEntity? = withContext(Dispatchers.IO) {
        database.foodDao().getFoodById(foodIdOrName)
            ?: database.foodDao().getFoodByName(foodIdOrName)
    }

    override suspend fun getAllSupportedFoods(): List<com.iqoo.wellness.engine.storage.FoodEntity> = withContext(Dispatchers.IO) {
        database.foodDao().getAllFoods()
    }

    override suspend fun analyzePose(
        frameData: ByteArray?,
        exerciseType: ExerciseType
    ): PostureFeedback? = withContext(Dispatchers.Default) {
        val landmarks = poseDetector.detectPose(frameData)
        if (landmarks.isEmpty()) return@withContext null

        val fsm = stateMachines[exerciseType] ?: return@withContext null
        val feedback = fsm.processLandmarks(landmarks)
        android.util.Log.d("IQOO_WELLNESS", "[POSTURE_PIPELINE] Detector=${poseDetector.javaClass.simpleName}, landmarks=${landmarks.size}, exercise=$exerciseType, reps=${feedback.repCount}, angle=${feedback.primaryAngleDegrees}")
        feedback
    }

    override suspend fun analyzePose(
        bitmap: Bitmap,
        exerciseType: ExerciseType
    ): PostureFeedback? = withContext(Dispatchers.Default) {
        if (poseDetector is ONNXExercisePoseDetector) {
            poseDetector.processFrame(bitmap, exerciseType)
        } else {
            analyzePose(null, exerciseType)
        }
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
            val tfliteRecognizer = TFLiteFoodRecognizer(context)
            val onnxPoseDetector = ONNXExercisePoseDetector(context)
            return WellnessEngineImpl(
                database = db,
                foodRecognizer = tfliteRecognizer,
                poseDetector = onnxPoseDetector,
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
