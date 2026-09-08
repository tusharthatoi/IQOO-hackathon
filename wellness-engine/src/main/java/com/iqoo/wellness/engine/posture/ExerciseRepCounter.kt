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
    private enum class Phase { REST, ACTIVE, PEAK }

    private data class Measurement(
        val phase: Phase,
        val metric: Double,
        val instruction: String
    )

    private var exerciseType: ExerciseType? = null
    private var phase = Phase.REST
    private var candidatePhase: Phase? = null
    private var candidateFrames = 0
    private var invalidFrames = 0
    private var repCount = 0
    private var lastMetric = 0.0
    private var lastInstruction = "Hold a steady position"
    private var startedFromRest = false
    private var movementObserved = false
    private var peakObserved = false

    fun update(type: ExerciseType, landmarks: List<BodyLandmark>): RepSnapshot {
        if (exerciseType != type) reset(type)
        if (landmarks.isEmpty()) return snapshot()
        invalidFrames = 0

        val measurement = measurement(type, landmarks.associateBy { it.id })
        lastMetric = measurement.metric
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
            when {
                previous == Phase.REST && phase == Phase.ACTIVE -> {
                    if (startedFromRest) movementObserved = true
                }
                phase == Phase.PEAK && (previous == Phase.ACTIVE || previous == Phase.REST) -> {
                    if (startedFromRest) {
                        movementObserved = true
                        peakObserved = true
                    }
                }
                phase == Phase.REST -> {
                    if (previous == Phase.PEAK && startedFromRest && movementObserved && peakObserved) {
                        repCount++
                        android.util.Log.d("REP_INCREMENT", "exercise=$type reps=$repCount")
                    }
                    movementObserved = false
                    peakObserved = false
                }
            }
        }

        if (phase == Phase.REST && candidateFrames >= stableFramesRequired) {
            startedFromRest = true
        }

        android.util.Log.d(
            "REP_STATE",
            "exercise=$type state=$phase metric=${"%.3f".format(measurement.metric)} " +
                "movement=$movementObserved peak=$peakObserved reps=$repCount"
        )
        return snapshot()
    }

    fun onInvalidPose() {
        invalidFrames++
        if (invalidFrames >= invalidFramesBeforeReset) {
            // Clear transition candidates, but preserve the accumulated workout count.
            candidatePhase = null
            candidateFrames = 0
            phase = Phase.REST
            movementObserved = false
            peakObserved = false
        }
    }

    fun reset(type: ExerciseType? = exerciseType) {
        exerciseType = type
        phase = Phase.REST
        candidatePhase = null
        candidateFrames = 0
        invalidFrames = 0
        repCount = 0
        lastMetric = 0.0
        lastInstruction = "Hold a steady position"
        startedFromRest = false
        movementObserved = false
        peakObserved = false
    }

    private fun snapshot(): RepSnapshot = RepSnapshot(
        count = repCount,
        state = when (phase) {
            Phase.REST -> ExerciseState.READY
            Phase.ACTIVE -> ExerciseState.IN_REP
            Phase.PEAK -> ExerciseState.PEAK
        },
        angleDegrees = lastMetric,
        instruction = lastInstruction
    )

    private fun measurement(type: ExerciseType, p: Map<Int, BodyLandmark>): Measurement {
        return when (type) {
            ExerciseType.SQUAT -> {
                val knee = jointAngleOrNull(p, 24, 26, 28) ?: return rest("Show your legs")
                when {
                    knee <= 105.0 -> Measurement(Phase.PEAK, knee, "Drive up from the bottom")
                    knee <= 140.0 -> Measurement(Phase.ACTIVE, knee, "Reach squat depth")
                    else -> Measurement(Phase.REST, knee, "Stand tall to begin the squat")
                }
            }
            ExerciseType.SIT_TO_STAND -> {
                val knee = jointAngleOrNull(p, 24, 26, 28) ?: return rest("Show your legs")
                when {
                    knee >= 160.0 -> Measurement(Phase.PEAK, knee, "Sit back down with control")
                    knee >= 130.0 -> Measurement(Phase.ACTIVE, knee, "Stand fully upright")
                    else -> Measurement(Phase.REST, knee, "Stand up to begin the rep")
                }
            }
            ExerciseType.PUSH_UPS, ExerciseType.PUSH_UP -> {
                val elbow = jointAngleOrNull(p, 12, 14, 16) ?: return rest("Show your arms")
                when {
                    elbow <= 95.0 -> Measurement(Phase.PEAK, elbow, "Press away from the floor")
                    elbow <= 135.0 -> Measurement(Phase.ACTIVE, elbow, "Reach the bottom position")
                    else -> Measurement(Phase.REST, elbow, "Return to plank position")
                }
            }
            ExerciseType.PULL_UPS -> {
                val elbow = jointAngleOrNull(p, 12, 14, 16) ?: return rest("Show your arms")
                when {
                    elbow <= 105.0 -> Measurement(Phase.PEAK, elbow, "Lower under control")
                    elbow <= 130.0 -> Measurement(Phase.ACTIVE, elbow, "Pull toward the bar")
                    else -> Measurement(Phase.REST, elbow, "Start from straight arms")
                }
            }
            ExerciseType.ARM_RAISE -> {
                val offset = armRaiseMetric(p) ?: return rest("Show one arm")
                when {
                    offset >= 0.14f -> Measurement(Phase.PEAK, offset.toDouble(), "Lower your arm")
                    offset >= 0.10f -> Measurement(Phase.ACTIVE, offset.toDouble(), "Raise your arm higher")
                    else -> Measurement(Phase.REST, offset.toDouble(), "Raise your arm")
                }
            }
            ExerciseType.JUMPING_JACKS -> {
                val opening = jumpingJackMetric(p) ?: return rest("Show your arms and legs")
                when {
                    opening >= 1.25f -> Measurement(Phase.PEAK, opening.toDouble(), "Bring arms and feet together")
                    opening >= 1.10f -> Measurement(Phase.ACTIVE, opening.toDouble(), "Open arms and feet")
                    else -> Measurement(Phase.REST, opening.toDouble(), "Open arms and feet")
                }
            }
            ExerciseType.KNEE_EXTENSION -> {
                val knee = jointAngleOrNull(p, 24, 26, 28) ?: return rest("Show your knee")
                when {
                    knee >= 155.0 -> Measurement(Phase.PEAK, knee, "Bend your knee back down")
                    knee >= 125.0 -> Measurement(Phase.ACTIVE, knee, "Extend the knee fully")
                    else -> Measurement(Phase.REST, knee, "Extend your knee")
                }
            }
            ExerciseType.RUSSIAN_TWISTS -> {
                val centerX = averageXOrNull(p, 23, 24)
                val handX = averageXOrNull(p, 15, 16)
                if (centerX == null || handX == null) return rest("Show your torso and hands")
                val offset = abs(handX - centerX)
                when {
                    offset >= 0.25f -> Measurement(Phase.PEAK, offset.toDouble(), "Return to center")
                    offset >= 0.14f -> Measurement(Phase.ACTIVE, offset.toDouble(), "Rotate farther")
                    else -> Measurement(Phase.REST, offset.toDouble(), "Rotate left or right")
                }
            }
            ExerciseType.BICEP_CURL, ExerciseType.LUNGE, ExerciseType.SHOULDER_PRESS ->
                rest("Exercise tracking unavailable")
        }
    }

    private fun rest(instruction: String) = Measurement(Phase.REST, 0.0, instruction)

    private fun jointAngleOrNull(p: Map<Int, BodyLandmark>, a: Int, b: Int, c: Int): Double? {
        val first = p[a] ?: return null
        val middle = p[b] ?: return null
        val last = p[c] ?: return null
        return JointAngleCalculator.calculateJointAngle(first, middle, last)
    }

    private fun armRaiseMetric(p: Map<Int, BodyLandmark>): Float? {
        val offsets = listOf(11 to 15, 12 to 16).mapNotNull { (shoulder, wrist) ->
            val shoulderPoint = p[shoulder] ?: return@mapNotNull null
            val wristPoint = p[wrist] ?: return@mapNotNull null
            (shoulderPoint.y - wristPoint.y).takeIf { it >= 0f }
        }
        return offsets.maxOrNull()
    }

    private fun jumpingJackMetric(p: Map<Int, BodyLandmark>): Float? {
        val shoulderY = averageYOrNull(p, 11, 12) ?: return null
        val wristY = averageYOrNull(p, 15, 16) ?: return null
        val hipWidth = distanceXOrNull(p, 23, 24) ?: return null
        val ankleWidth = distanceXOrNull(p, 27, 28) ?: return null
        if (hipWidth <= 0f || wristY >= shoulderY - 0.03f) return 0f
        return ankleWidth / hipWidth
    }

    private fun averageXOrNull(p: Map<Int, BodyLandmark>, a: Int, b: Int): Float? {
        val first = p[a] ?: return null
        val second = p[b] ?: return null
        return (first.x + second.x) / 2f
    }

    private fun averageYOrNull(p: Map<Int, BodyLandmark>, a: Int, b: Int): Float? {
        val first = p[a] ?: return null
        val second = p[b] ?: return null
        return (first.y + second.y) / 2f
    }

    private fun distanceXOrNull(p: Map<Int, BodyLandmark>, a: Int, b: Int): Float? {
        val first = p[a] ?: return null
        val second = p[b] ?: return null
        return abs(first.x - second.x)
    }
}
