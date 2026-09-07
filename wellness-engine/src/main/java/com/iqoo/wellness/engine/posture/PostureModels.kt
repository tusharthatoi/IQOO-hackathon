package com.iqoo.wellness.engine.posture

enum class ExerciseType(val displayName: String) {
    JUMPING_JACKS("Jumping Jacks"),
    PULL_UPS("Pull ups"),
    PUSH_UPS("Push Ups"),
    RUSSIAN_TWISTS("Russian twists"),
    SQUAT("Squat"),
    PUSH_UP("Push-up"),
    BICEP_CURL("Bicep Curl"),
    LUNGE("Lunge"),
    SHOULDER_PRESS("Shoulder Press"),
    ARM_RAISE("Arm Raise"),
    KNEE_EXTENSION("Knee Extension"),
    SIT_TO_STAND("Sit To Stand")
}

enum class ExerciseState {
    READY,
    IN_REP,
    PEAK,
    COMPLETED
}

enum class PoseStatus {
    VALID,
    NO_PERSON,
    INSUFFICIENT,
    NO_EXERCISE
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
    val landmarks: List<BodyLandmark> = emptyList(),
    val confidence: Float = 0f,
    val exerciseConfidence: Float = 0f,
    val detectedActivity: String = exerciseType.displayName,
    val poseStatus: PoseStatus = PoseStatus.VALID
)
