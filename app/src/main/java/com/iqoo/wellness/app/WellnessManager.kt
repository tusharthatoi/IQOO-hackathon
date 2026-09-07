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
import com.iqoo.wellness.engine.scene.SceneType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Facade bridging camera frame delivery to the underlying WellnessEngine.
 * Implements the architecture: Camera -> WellnessManager -> WellnessEngine.
 */
class WellnessManager(
    context: Context,
    val engine: WellnessEngine = WellnessEngineImpl.create(context)
) : FrameListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var onSceneDetected: ((SceneType) -> Unit)? = null
    var onFoodAnalyzed: ((PersonalizedNutritionResult?) -> Unit)? = null
    var onPostureAnalyzed: ((PostureFeedback?) -> Unit)? = null

    private var activeMode: SceneType = SceneType.NORMAL
    private val foodScanGeneration = AtomicLong(0L)
    var activeExerciseType: ExerciseType = ExerciseType.SQUAT

    fun setMode(mode: SceneType) {
        if (activeMode != mode) {
            engine.resetPoseState()
            android.util.Log.i("CAMERA", "Mode changed $activeMode -> $mode; pose state reset")
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
                        val feedback = if (bitmap != null) {
                            engine.analyzePose(bitmap, activeExerciseType)
                        } else {
                            engine.analyzePose(null, activeExerciseType)
                        }
                        android.util.Log.i("POSTURE_TRACE", "manager confidence=${feedback?.confidence} status=${feedback?.poseStatus}")
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
            }
        }
    }
}
