package com.iqoo.wellness.app

import android.content.Context
import android.graphics.Bitmap
import com.iqoo.wellness.app.camera.FrameListener
import com.iqoo.wellness.engine.WellnessEngine
import com.iqoo.wellness.engine.WellnessEngineImpl
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.storage.WorkoutSessionEntity
import com.iqoo.wellness.engine.scene.SceneType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Facade bridging camera frame delivery to the underlying WellnessEngine.
 * Implements the architecture: Camera -> WellnessManager -> WellnessEngine.
 */
class WellnessManager(
    context: Context,
    val engine: WellnessEngine = WellnessEngineImpl.create(context)
) : FrameListener {

    data class ActiveWorkout(
        val exerciseType: ExerciseType,
        val startedAt: Long,
        var latestFeedback: PostureFeedback? = null,
        var personDetected: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frameInFlight = AtomicBoolean(false)

    var onSceneDetected: ((SceneType) -> Unit)? = null
    var onFoodAnalyzed: ((PersonalizedNutritionResult?) -> Unit)? = null
    var onPostureAnalyzed: ((PostureFeedback?) -> Unit)? = null

    @Volatile
    private var activeMode: SceneType = SceneType.NORMAL
    private val foodScanGeneration = AtomicLong(0L)
    var activeExerciseType: ExerciseType = ExerciseType.SQUAT
    @Volatile var activeWorkout: ActiveWorkout? = null

    fun canAcceptFrame(): Boolean = !frameInFlight.get()

    fun startWorkout(exerciseType: ExerciseType) {
        activeExerciseType = exerciseType
        activeWorkout = ActiveWorkout(exerciseType, System.currentTimeMillis())
        engine.resetPoseState()
        setMode(SceneType.EXERCISE)
    }

    fun cancelWorkout() {
        activeWorkout = null
        engine.resetPoseState()
        setMode(SceneType.NORMAL)
    }

    suspend fun saveActiveWorkout(): Boolean {
        val workout = activeWorkout ?: return false
        val feedback = workout.latestFeedback
        android.util.Log.i(
            "POSTURE_SAVE",
            "exercise=${workout.exerciseType.name} posture=${feedback?.isFormCorrect} confidence=${feedback?.confidence}"
        )
        engine.saveWorkoutSession(
            WorkoutSessionEntity(
                exerciseType = workout.exerciseType.name,
                reps = feedback?.repCount ?: 0,
                durationSeconds = ((System.currentTimeMillis() - workout.startedAt) / 1000L).coerceAtLeast(0L),
                formScore = feedback?.confidence,
                exerciseConfidence = feedback?.exerciseConfidence
            )
        )
        activeWorkout = null
        engine.resetPoseState()
        setMode(SceneType.NORMAL)
        return true
    }

    fun setMode(mode: SceneType) {
        if (activeMode != mode && activeWorkout == null) {
            engine.resetPoseState()
            android.util.Log.i("CAMERA", "Mode changed $activeMode -> $mode; pose state reset")
        } else if (activeMode != mode) {
            android.util.Log.i("CAMERA", "Mode changed $activeMode -> $mode; active workout preserved")
        }
        activeMode = mode
    }

    fun resetFoodScanning() {
        foodScanGeneration.incrementAndGet()
        engine.resetFoodScanning()
        android.util.Log.i("FOOD_SCAN", "Food scan reset; CameraX remains active")
    }

    override fun onFrameAvailable(
        bitmap: Bitmap?,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        planes: Array<ByteBuffer>
    ) {
        if (!frameInFlight.compareAndSet(false, true)) {
            android.util.Log.d("IQOO_WELLNESS", "[PIPELINE] Dropping frame while inference is in flight")
            return
        }
        scope.launch {
            try {
                android.util.Log.d("IQOO_WELLNESS", "[PIPELINE] Active mode: $activeMode")
                // Cascaded frame dispatching according to active mode
                when (activeMode) {
                    SceneType.FOOD -> {
                        val generation = foodScanGeneration.get()
                        android.util.Log.d(
                            "WELLNESS_MANAGER",
                            "FOOD frame generation=$generation bitmapId=${bitmap?.let { System.identityHashCode(it) }} size=${width}x${height} rotation=$rotationDegrees"
                        )
                        val result = if (bitmap != null) {
                            engine.analyzeFood(bitmap)
                        } else {
                            engine.analyzeFood()
                        }
                        android.util.Log.d("WELLNESS_MANAGER", "FOOD result generation=$generation heading=${result?.displayHeading}")
                        if (result != null && activeMode == SceneType.FOOD && generation == foodScanGeneration.get()) {
                            onFoodAnalyzed?.invoke(result)
                        } else {
                            android.util.Log.d("FOOD_SCAN", "Skipped null or stale food result from generation=$generation")
                        }
                    }
                    SceneType.EXERCISE -> {
                        android.util.Log.d("POSE_ANALYZER", "frameId=async mode=EXERCISE bitmap=${bitmap != null}")
                        val feedback = if (bitmap != null) {
                            engine.analyzePose(bitmap, activeExerciseType)
                        } else {
                            engine.analyzePose(null, activeExerciseType)
                        }
                        activeWorkout?.let {
                            if (feedback?.poseStatus == com.iqoo.wellness.engine.posture.PoseStatus.VALID) {
                                it.latestFeedback = feedback
                                it.personDetected = true
                            } else {
                                it.personDetected = false
                            }
                        }
                        android.util.Log.i("POSTURE_TRACE", "manager confidence=${feedback?.confidence} status=${feedback?.poseStatus}")
                        android.util.Log.d(
                            "EXERCISE_CONFIDENCE_TRACE",
                            "detector=${feedback?.exerciseConfidence} engine=${feedback?.exerciseConfidence} " +
                                "manager=${feedback?.exerciseConfidence} selectedExercise=${activeExerciseType.displayName} " +
                                "detectedExercise=${feedback?.detectedActivity} confidence=${feedback?.exerciseConfidence}"
                        )
                        android.util.Log.d(
                            "UI_STATE",
                            "exercise=${feedback?.detectedActivity} confidence=${feedback?.exerciseConfidence} " +
                                "posture=${feedback?.confidence} reps=${feedback?.repCount} status=${feedback?.poseStatus}"
                        )
                        android.util.Log.d("IQOO_WELLNESS", "[PIPELINE] Posture output: rep=${feedback?.repCount}, state=${feedback?.currentState}, angle=${feedback?.primaryAngleDegrees}")
                        onPostureAnalyzed?.invoke(feedback)
                    }
                    SceneType.NORMAL -> {
                        // Cascaded scene check
                        val scene = engine.analyzeScene()
                        android.util.Log.d("IQOO_WELLNESS", "[PIPELINE] Scene classified: $scene")
                        onSceneDetected?.invoke(scene)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IQOO_WELLNESS", "[PIPELINE] Error in frame pipeline: ${e.message}", e)
            } finally {
                frameInFlight.set(false)
            }
        }
    }

    fun clearCallbacks() {
        onSceneDetected = null
        onFoodAnalyzed = null
        onPostureAnalyzed = null
    }
}
