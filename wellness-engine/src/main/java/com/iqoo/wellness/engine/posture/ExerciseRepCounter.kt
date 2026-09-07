package com.iqoo.wellness.engine.posture

import kotlin.math.abs

data class RepSnapshot(
    val count: Int,
    val state: ExerciseState,
    val angleDegrees: Double,
    val instruction: String
)

class ExerciseRepCounter(
    private val stableFramesRequired: Int = 3,
    private val invalidFramesBeforeReset: Int = 10
) {
    private enum class Phase { NEUTRAL, ACTIVE }

    private var exerciseType: ExerciseType? = null
    private var phase = Phase.NEUTRAL
    private var candidatePhase: Phase? = null
    private var candidateFrames = 0
    private var invalidFrames = 0
    private var repCount = 0
    private var lastAngle = 0.0
    private var lastInstruction = "Hold a steady position"

    fun update(type: ExerciseType, landmarks: List<BodyLandmark>): RepSnapshot {
        if (exerciseType != type) reset(type)
        invalidFrames = 0

        val points = landmarks.associateBy { it.id }
        val measurement = measurement(type, points)
        lastAngle = measurement.angle
        lastInstruction = measurement.instruction

        if (candidatePhase == measurement.phase) {
            candidateFrames++
        } else {
            candidatePhase = measurement.phase
            candidateFrames = 1
        }

        if (candidateFrames >= stableFramesRequired && phase != measurement.phase) {
            val previous = phase
            phase = measurement.phase
            if (measurement.countOnNeutral && previous == Phase.ACTIVE && phase == Phase.NEUTRAL) {
                repCount++
            } else if (measurement.countOnActive && previous == Phase.NEUTRAL && phase == Phase.ACTIVE) {
                repCount++
            }
        }

        if (type == ExerciseType.RUSSIAN_TWISTS && measurement.twistSide != 0) {
            if (candidateTwistSide == measurement.twistSide) {
                candidateTwistFrames++
            } else {
                candidateTwistSide = measurement.twistSide
                candidateTwistFrames = 1
            }
            if (candidateTwistFrames >= stableFramesRequired && lastTwistSide != measurement.twistSide) {
                if (lastTwistSide != 0) repCount++
                lastTwistSide = measurement.twistSide
            }
        }

        val state = if (phase == Phase.ACTIVE) ExerciseState.PEAK else ExerciseState.IN_REP
        return RepSnapshot(repCount, state, lastAngle, lastInstruction)
    }

    fun onInvalidPose() {
        invalidFrames++
        if (invalidFrames >= invalidFramesBeforeReset) {
            reset(exerciseType)
        }
    }

    fun reset(type: ExerciseType? = exerciseType) {
        exerciseType = type
        phase = Phase.NEUTRAL
        candidatePhase = null
        candidateFrames = 0
        invalidFrames = 0
        repCount = 0
        lastAngle = 0.0
        lastInstruction = "Hold a steady position"
        lastTwistSide = 0
        candidateTwistSide = 0
        candidateTwistFrames = 0
    }

    private data class Measurement(
        val phase: Phase,
        val angle: Double,
        val instruction: String,
        val countOnNeutral: Boolean = true,
        val countOnActive: Boolean = false,
        val twistSide: Int = 0
    )

    private fun measurement(type: ExerciseType, p: Map<Int, BodyLandmark>): Measurement {
        return when (type) {
            ExerciseType.SQUAT, ExerciseType.SIT_TO_STAND -> {
                val angle = angle(p, 24, 26, 28)
                if (type == ExerciseType.SIT_TO_STAND) {
                    Measurement(if (angle > 155.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle > 155.0) "Stand tall" else "Sit down before the next rep", countOnNeutral = false, countOnActive = true)
                } else {
                    Measurement(if (angle < 105.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 105.0) "Good depth - drive up" else "Stand tall to begin the next squat")
                }
            }
            ExerciseType.LUNGE -> {
                val angle = angle(p, 24, 26, 28)
                Measurement(if (angle < 100.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 100.0) "Drive through the front heel" else "Return to the starting stance")
            }
            ExerciseType.PUSH_UPS, ExerciseType.PUSH_UP -> {
                val angle = angle(p, 12, 14, 16)
                Measurement(if (angle < 95.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 95.0) "Press the floor away" else "Return to plank position")
            }
            ExerciseType.KNEE_EXTENSION -> {
                val angle = angle(p, 24, 26, 28)
                Measurement(if (angle < 115.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 115.0) "Extend the knee" else "Return to the starting position")
            }
            ExerciseType.BICEP_CURL -> {
                val angle = angle(p, 12, 14, 16)
                Measurement(if (angle < 65.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 65.0) "Squeeze at the top" else "Lower with control")
            }
            ExerciseType.SHOULDER_PRESS -> {
                val angle = angle(p, 12, 14, 16)
                Measurement(if (angle > 165.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle > 165.0) "Lower under control" else "Press overhead")
            }
            ExerciseType.ARM_RAISE -> {
                val shoulderY = averageY(p, 11, 12)
                val wristY = averageY(p, 15, 16)
                val raised = wristY < shoulderY - 0.08f
                Measurement(if (raised) Phase.ACTIVE else Phase.NEUTRAL, 0.0, if (raised) "Lower your arms" else "Raise both arms")
            }
            ExerciseType.JUMPING_JACKS -> {
                val shoulderWidth = distanceX(p, 11, 12)
                val hipWidth = distanceX(p, 23, 24)
                val ankleWidth = distanceX(p, 27, 28)
                val wristsRaised = averageY(p, 15, 16) < averageY(p, 11, 12) - 0.05f
                val open = wristsRaised && ankleWidth > hipWidth * 1.25f
                Measurement(if (open) Phase.ACTIVE else Phase.NEUTRAL, 0.0, if (open) "Bring arms and feet together" else "Open arms and feet")
            }
            ExerciseType.PULL_UPS -> {
                val angle = angle(p, 12, 14, 16)
                Measurement(if (angle < 100.0) Phase.ACTIVE else Phase.NEUTRAL, angle, if (angle < 100.0) "Lower under control" else "Pull your chest toward the bar")
            }
            ExerciseType.RUSSIAN_TWISTS -> {
                val centerX = averageX(p, 23, 24)
                val handX = averageX(p, 15, 16)
                val side = if (handX < centerX - 0.05f) -1 else if (handX > centerX + 0.05f) 1 else 0
                Measurement(Phase.NEUTRAL, 0.0, "Rotate left and right with control", countOnNeutral = false, twistSide = side)
            }
        }
    }

    private var lastTwistSide = 0
    private var candidateTwistSide = 0
    private var candidateTwistFrames = 0

    private fun angle(p: Map<Int, BodyLandmark>, a: Int, b: Int, c: Int): Double {
        val first = p[a] ?: return 0.0
        val middle = p[b] ?: return 0.0
        val last = p[c] ?: return 0.0
        return JointAngleCalculator.calculateJointAngle(first, middle, last)
    }

    private fun averageX(p: Map<Int, BodyLandmark>, first: Int, second: Int): Float =
        ((p[first]?.x ?: 0f) + (p[second]?.x ?: 0f)) / 2f

    private fun averageY(p: Map<Int, BodyLandmark>, first: Int, second: Int): Float =
        ((p[first]?.y ?: 0f) + (p[second]?.y ?: 0f)) / 2f

    private fun distanceX(p: Map<Int, BodyLandmark>, first: Int, second: Int): Float =
        abs((p[first]?.x ?: 0f) - (p[second]?.x ?: 0f))
}
