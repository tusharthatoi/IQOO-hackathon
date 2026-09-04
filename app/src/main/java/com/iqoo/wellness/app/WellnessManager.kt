package com.iqoo.wellness.app

import android.content.Context
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
        width: Int,
        height: Int,
        rotationDegrees: Int,
        planes: Array<ByteBuffer>
    ) {
        scope.launch {
            try {
                // Cascaded frame dispatching according to active mode
                when (activeMode) {
                    SceneType.FOOD -> {
                        val result = engine.analyzeFood()
                        onFoodAnalyzed?.invoke(result)
                    }
                    SceneType.EXERCISE -> {
                        val feedback = engine.analyzePose()
                        onPostureAnalyzed?.invoke(feedback)
                    }
                    SceneType.NORMAL -> {
                        // Cascaded scene check
                        val scene = engine.analyzeScene()
                        onSceneDetected?.invoke(scene)
                    }
                }
            } catch (_: Exception) {
                // Ensure frame analysis failure never crashes the camera stream
            }
        }
    }
}
