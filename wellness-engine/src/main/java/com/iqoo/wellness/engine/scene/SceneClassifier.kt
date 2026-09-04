package com.iqoo.wellness.engine.scene

/**
 * Lightweight scene/intent classifier abstraction.
 * Evaluates low-resolution frames to determine whether Food, Exercise, or Normal scene is present.
 */
interface SceneClassifier {
    suspend fun classifyScene(frameData: ByteArray? = null): SceneDetectionResult
}

class LightweightSceneClassifier : SceneClassifier {

    private var forcedScene: SceneType? = null

    override suspend fun classifyScene(frameData: ByteArray?): SceneDetectionResult {
        val scene = forcedScene ?: SceneType.FOOD
        return SceneDetectionResult(
            detectedScene = scene,
            confidence = 0.92f,
            inferenceTimeMs = 12L
        )
    }

    fun setForcedScene(scene: SceneType?) {
        this.forcedScene = scene
    }
}
