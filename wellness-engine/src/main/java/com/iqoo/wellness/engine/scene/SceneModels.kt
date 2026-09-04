package com.iqoo.wellness.engine.scene

enum class SceneType {
    NORMAL,
    FOOD,
    EXERCISE
}

data class SceneDetectionResult(
    val detectedScene: SceneType,
    val confidence: Float,
    val inferenceTimeMs: Long
)
