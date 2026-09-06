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

    fun setMode(mode: SceneType) {
        activeMode = mode
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
                        val result = if (bitmap != null) {
                            engine.analyzeFood(bitmap)
                        } else {
                            engine.analyzeFood()
                        }
                        android.util.Log.d("IQOO_WELLNESS", "[PIPELINE] Food analysis output: ${result?.displayHeading}, calories=${result?.nutrition?.calories}")
                        onFoodAnalyzed?.invoke(result)
                    }
                    SceneType.EXERCISE -> {
                        val feedback = engine.analyzePose()
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
