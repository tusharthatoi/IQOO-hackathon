package com.iqoo.wellness.engine.posture

enum class ExerciseType(val displayName: String) {
    SQUAT("Squat"),
    PUSH_UP("Push-up"),
    BICEP_CURL("Bicep Curl"),
    LUNGE("Lunge"),
    SHOULDER_PRESS("Shoulder Press")
}

enum class ExerciseState {
    READY,
    IN_REP,
    PEAK,
    COMPLETED
}

data class BodyLandmark(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f
)

data class JointAngle(
    val jointName: String,
    val angleDegrees: Double
)

data class PostureFeedback(
    val exerciseType: ExerciseType,
    val repCount: Int,
    val currentState: ExerciseState,
    val primaryAngleDegrees: Double,
    val isFormCorrect: Boolean,
    val feedbackMessage: String,
    val landmarks: List<BodyLandmark> = emptyList()
)
