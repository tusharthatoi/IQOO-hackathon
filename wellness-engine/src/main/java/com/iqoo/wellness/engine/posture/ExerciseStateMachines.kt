package com.iqoo.wellness.engine.posture

/**
 * Common contract for exercise state machines.
 */
interface ExerciseStateMachine {
    val exerciseType: ExerciseType
    val repCount: Int
    val currentState: ExerciseState
    fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback
    fun reset()
}

/**
 * Landmark index constants according to standard 17-point pose topology.
 */
object LandmarkIndices {
    const val NOSE = 0
    const val LEFT_EYE = 1
    const val RIGHT_EYE = 2
    const val LEFT_EAR = 3
    const val RIGHT_EAR = 4
    const val LEFT_SHOULDER = 5
    const val RIGHT_SHOULDER = 6
    const val LEFT_ELBOW = 7
    const val RIGHT_ELBOW = 8
    const val LEFT_WRIST = 9
    const val RIGHT_WRIST = 10
    const val LEFT_HIP = 11
    const val RIGHT_HIP = 12
    const val LEFT_KNEE = 13
    const val RIGHT_KNEE = 14
    const val LEFT_ANKLE = 15
    const val RIGHT_ANKLE = 16
}

class SquatStateMachine : ExerciseStateMachine {
    override val exerciseType = ExerciseType.SQUAT
    override var repCount = 0
        private set
    override var currentState = ExerciseState.READY
        private set

    private var reachedValidDepth = false

    override fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback {
        if (landmarks.size < 17) {
            return PostureFeedback(exerciseType, repCount, currentState, 180.0, false, "Position full body in frame", landmarks)
        }

        // Evaluate right leg or left leg knee angle
        val hip = landmarks[LandmarkIndices.RIGHT_HIP]
        val knee = landmarks[LandmarkIndices.RIGHT_KNEE]
        val ankle = landmarks[LandmarkIndices.RIGHT_ANKLE]

        val kneeAngle = JointAngleCalculator.calculateJointAngle(hip, knee, ankle)
        var feedbackMessage = "Good posture"
        var isFormCorrect = true

        when (currentState) {
            ExerciseState.READY -> {
                if (kneeAngle < 140.0) {
                    currentState = ExerciseState.IN_REP
                    reachedValidDepth = false
                    feedbackMessage = "Descending..."
                } else {
                    feedbackMessage = "Ready — Stand tall to begin squat"
                }
            }
            ExerciseState.IN_REP -> {
                if (kneeAngle <= 100.0) {
                    currentState = ExerciseState.PEAK
                    reachedValidDepth = true
                    feedbackMessage = "Good depth reached — Drive up through heels"
                } else if (kneeAngle > 155.0) {
                    // Reversing without reaching depth
                    currentState = ExerciseState.READY
                    isFormCorrect = false
                    feedbackMessage = "Squat deeper — hips must drop below knees"
                } else {
                    feedbackMessage = "Keep chest up and knees aligned"
                }
            }
            ExerciseState.PEAK -> {
                if (kneeAngle > 110.0) {
                    currentState = ExerciseState.IN_REP
                    feedbackMessage = "Ascending..."
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.READY
            }
        }

        if (reachedValidDepth && kneeAngle >= 160.0 && (currentState == ExerciseState.IN_REP || currentState == ExerciseState.PEAK)) {
            repCount++
            currentState = ExerciseState.COMPLETED
            reachedValidDepth = false
            feedbackMessage = "Rep $repCount completed!"
        }

        return PostureFeedback(
            exerciseType = exerciseType,
            repCount = repCount,
            currentState = currentState,
            primaryAngleDegrees = kneeAngle,
            isFormCorrect = isFormCorrect,
            feedbackMessage = feedbackMessage,
            landmarks = landmarks
        )
    }

    override fun reset() {
        repCount = 0
        currentState = ExerciseState.READY
        reachedValidDepth = false
    }
}

class PushUpStateMachine : ExerciseStateMachine {
    override val exerciseType = ExerciseType.PUSH_UP
    override var repCount = 0
        private set
    override var currentState = ExerciseState.READY
        private set

    private var reachedBottom = false

    override fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback {
        if (landmarks.size < 17) {
            return PostureFeedback(exerciseType, repCount, currentState, 180.0, false, "Position upper body in frame", landmarks)
        }

        val shoulder = landmarks[LandmarkIndices.RIGHT_SHOULDER]
        val elbow = landmarks[LandmarkIndices.RIGHT_ELBOW]
        val wrist = landmarks[LandmarkIndices.RIGHT_WRIST]

        val elbowAngle = JointAngleCalculator.calculateJointAngle(shoulder, elbow, wrist)
        var feedbackMessage = "Maintain tight core"
        var isFormCorrect = true

        when (currentState) {
            ExerciseState.READY -> {
                if (elbowAngle < 135.0) {
                    currentState = ExerciseState.IN_REP
                    reachedBottom = false
                    feedbackMessage = "Lowering chest..."
                } else {
                    feedbackMessage = "Ready in plank position"
                }
            }
            ExerciseState.IN_REP -> {
                if (elbowAngle <= 95.0) {
                    currentState = ExerciseState.PEAK
                    reachedBottom = true
                    feedbackMessage = "Full depth — Press floor away"
                } else if (elbowAngle > 155.0) {
                    currentState = ExerciseState.READY
                    isFormCorrect = false
                    feedbackMessage = "Go lower — lower chest to 90 degrees"
                }
            }
            ExerciseState.PEAK -> {
                if (elbowAngle > 105.0) {
                    currentState = ExerciseState.IN_REP
                    feedbackMessage = "Pressing up..."
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.READY
            }
        }

        if (reachedBottom && elbowAngle >= 155.0 && (currentState == ExerciseState.IN_REP || currentState == ExerciseState.PEAK)) {
            repCount++
            currentState = ExerciseState.COMPLETED
            reachedBottom = false
            feedbackMessage = "Push-up rep $repCount counted!"
        }

        return PostureFeedback(
            exerciseType = exerciseType,
            repCount = repCount,
            currentState = currentState,
            primaryAngleDegrees = elbowAngle,
            isFormCorrect = isFormCorrect,
            feedbackMessage = feedbackMessage,
            landmarks = landmarks
        )
    }

    override fun reset() {
        repCount = 0
        currentState = ExerciseState.READY
        reachedBottom = false
    }
}

class BicepCurlStateMachine : ExerciseStateMachine {
    override val exerciseType = ExerciseType.BICEP_CURL
    override var repCount = 0
        private set
    override var currentState = ExerciseState.READY
        private set

    private var reachedContraction = false

    override fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback {
        if (landmarks.size < 17) {
            return PostureFeedback(exerciseType, repCount, currentState, 180.0, false, "Arms must be visible", landmarks)
        }

        val shoulder = landmarks[LandmarkIndices.RIGHT_SHOULDER]
        val elbow = landmarks[LandmarkIndices.RIGHT_ELBOW]
        val wrist = landmarks[LandmarkIndices.RIGHT_WRIST]

        val elbowAngle = JointAngleCalculator.calculateJointAngle(shoulder, elbow, wrist)
        var feedbackMessage = "Keep elbow pinned to torso"
        var isFormCorrect = true

        when (currentState) {
            ExerciseState.READY -> {
                if (elbowAngle < 125.0) {
                    currentState = ExerciseState.IN_REP
                    reachedContraction = false
                    feedbackMessage = "Curling up..."
                } else {
                    feedbackMessage = "Arm extended — Begin curl"
                }
            }
            ExerciseState.IN_REP -> {
                if (elbowAngle <= 60.0) {
                    currentState = ExerciseState.PEAK
                    reachedContraction = true
                    feedbackMessage = "Peak contraction — Squeeze bicep"
                }
            }
            ExerciseState.PEAK -> {
                if (elbowAngle > 80.0) {
                    currentState = ExerciseState.IN_REP
                    feedbackMessage = "Lowering under control..."
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.READY
            }
        }

        if (reachedContraction && elbowAngle >= 145.0 && (currentState == ExerciseState.IN_REP || currentState == ExerciseState.PEAK)) {
            repCount++
            currentState = ExerciseState.COMPLETED
            reachedContraction = false
            feedbackMessage = "Curl $repCount counted!"
        }

        return PostureFeedback(
            exerciseType = exerciseType,
            repCount = repCount,
            currentState = currentState,
            primaryAngleDegrees = elbowAngle,
            isFormCorrect = isFormCorrect,
            feedbackMessage = feedbackMessage,
            landmarks = landmarks
        )
    }

    override fun reset() {
        repCount = 0
        currentState = ExerciseState.READY
        reachedContraction = false
    }
}

class LungeStateMachine : ExerciseStateMachine {
    override val exerciseType = ExerciseType.LUNGE
    override var repCount = 0
        private set
    override var currentState = ExerciseState.READY
        private set

    private var reachedDepth = false

    override fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback {
        if (landmarks.size < 17) {
            return PostureFeedback(exerciseType, repCount, currentState, 180.0, false, "Position full body in frame", landmarks)
        }

        val hip = landmarks[LandmarkIndices.RIGHT_HIP]
        val knee = landmarks[LandmarkIndices.RIGHT_KNEE]
        val ankle = landmarks[LandmarkIndices.RIGHT_ANKLE]

        val kneeAngle = JointAngleCalculator.calculateJointAngle(hip, knee, ankle)
        var feedbackMessage = "Step forward into lunge"
        var isFormCorrect = true

        when (currentState) {
            ExerciseState.READY -> {
                if (kneeAngle < 135.0) {
                    currentState = ExerciseState.IN_REP
                    reachedDepth = false
                    feedbackMessage = "Dropping hips..."
                }
            }
            ExerciseState.IN_REP -> {
                if (kneeAngle <= 95.0) {
                    currentState = ExerciseState.PEAK
                    reachedDepth = true
                    feedbackMessage = "90 degree bend — Drive back up"
                }
            }
            ExerciseState.PEAK -> {
                if (kneeAngle > 110.0) {
                    currentState = ExerciseState.IN_REP
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.READY
            }
        }

        if (reachedDepth && kneeAngle >= 155.0 && (currentState == ExerciseState.IN_REP || currentState == ExerciseState.PEAK)) {
            repCount++
            currentState = ExerciseState.COMPLETED
            reachedDepth = false
            feedbackMessage = "Lunge $repCount completed!"
        }

        return PostureFeedback(exerciseType, repCount, currentState, kneeAngle, isFormCorrect, feedbackMessage, landmarks)
    }

    override fun reset() {
        repCount = 0
        currentState = ExerciseState.READY
        reachedDepth = false
    }
}

class ShoulderPressStateMachine : ExerciseStateMachine {
    override val exerciseType = ExerciseType.SHOULDER_PRESS
    override var repCount = 0
        private set
    override var currentState = ExerciseState.READY
        private set

    private var reachedOverhead = false

    override fun processLandmarks(landmarks: List<BodyLandmark>): PostureFeedback {
        if (landmarks.size < 17) {
            return PostureFeedback(exerciseType, repCount, currentState, 180.0, false, "Position upper body in frame", landmarks)
        }

        val shoulder = landmarks[LandmarkIndices.RIGHT_SHOULDER]
        val elbow = landmarks[LandmarkIndices.RIGHT_ELBOW]
        val wrist = landmarks[LandmarkIndices.RIGHT_WRIST]

        val elbowAngle = JointAngleCalculator.calculateJointAngle(shoulder, elbow, wrist)
        var feedbackMessage = "Weights at shoulder level"
        var isFormCorrect = true

        when (currentState) {
            ExerciseState.READY -> {
                if (elbowAngle > 100.0 && elbowAngle < 155.0) {
                    currentState = ExerciseState.IN_REP
                    reachedOverhead = false
                    feedbackMessage = "Pressing overhead..."
                }
            }
            ExerciseState.IN_REP -> {
                if (elbowAngle >= 165.0) {
                    currentState = ExerciseState.PEAK
                    reachedOverhead = true
                    feedbackMessage = "Full extension overhead — Lower under control"
                }
            }
            ExerciseState.PEAK -> {
                if (elbowAngle < 140.0) {
                    currentState = ExerciseState.IN_REP
                    feedbackMessage = "Lowering to rack position..."
                }
            }
            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.READY
            }
        }

        if (reachedOverhead && elbowAngle <= 90.0 && (currentState == ExerciseState.IN_REP || currentState == ExerciseState.PEAK)) {
            repCount++
            currentState = ExerciseState.COMPLETED
            reachedOverhead = false
            feedbackMessage = "Shoulder press $repCount counted!"
        }

        return PostureFeedback(exerciseType, repCount, currentState, elbowAngle, isFormCorrect, feedbackMessage, landmarks)
    }

    override fun reset() {
        repCount = 0
        currentState = ExerciseState.READY
        reachedOverhead = false
    }
}
