package com.iqoo.wellness.engine

import com.iqoo.wellness.engine.posture.BodyLandmark
import com.iqoo.wellness.engine.posture.ExerciseRepCounter
import com.iqoo.wellness.engine.posture.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseRepCounterTest {

    @Test
    fun squatCountsOnlyAfterStableDownAndUpTransition() {
        val counter = ExerciseRepCounter(stableFramesRequired = 3)
        val standing = squatLandmarks(180.0)
        val bottom = squatLandmarks(90.0)

        repeat(3) { counter.update(ExerciseType.SQUAT, standing) }
        repeat(3) { counter.update(ExerciseType.SQUAT, bottom) }
        assertEquals(0, counter.update(ExerciseType.SQUAT, bottom).count)

        repeat(2) { counter.update(ExerciseType.SQUAT, standing) }
        assertEquals(1, counter.update(ExerciseType.SQUAT, standing).count)
    }

    @Test
    fun armRaiseCountsOneRepAfterRaisedAndLoweredTransitions() {
        val counter = ExerciseRepCounter(stableFramesRequired = 2)
        val lowered = armRaiseLandmarks(0.55f)
        val raised = armRaiseLandmarks(0.15f)

        repeat(2) { counter.update(ExerciseType.ARM_RAISE, lowered) }
        repeat(2) { counter.update(ExerciseType.ARM_RAISE, raised) }
        assertEquals(0, counter.update(ExerciseType.ARM_RAISE, raised).count)
        repeat(2) { counter.update(ExerciseType.ARM_RAISE, lowered) }
        assertEquals(1, counter.update(ExerciseType.ARM_RAISE, lowered).count)
    }

    @Test
    fun sitToStandDoesNotCountInitialStandingPose() {
        val counter = ExerciseRepCounter(stableFramesRequired = 2)
        val standing = squatLandmarks(180.0)
        val seated = squatLandmarks(90.0)

        repeat(2) { counter.update(ExerciseType.SIT_TO_STAND, standing) }
        assertEquals(0, counter.update(ExerciseType.SIT_TO_STAND, standing).count)
        repeat(2) { counter.update(ExerciseType.SIT_TO_STAND, seated) }
        repeat(2) { counter.update(ExerciseType.SIT_TO_STAND, standing) }
        repeat(2) { counter.update(ExerciseType.SIT_TO_STAND, seated) }
        assertEquals(1, counter.update(ExerciseType.SIT_TO_STAND, seated).count)
    }

    @Test
    fun armRaiseUsesTheAvailableArmWhenTheOtherArmIsMissing() {
        val counter = ExerciseRepCounter(stableFramesRequired = 2)
        val lowered = listOf(
            BodyLandmark(11, "left_shoulder", 0.4f, 0.3f),
            BodyLandmark(15, "left_wrist", 0.25f, 0.55f)
        )
        val raised = listOf(
            BodyLandmark(11, "left_shoulder", 0.4f, 0.3f),
            BodyLandmark(15, "left_wrist", 0.25f, 0.1f)
        )

        repeat(2) { counter.update(ExerciseType.ARM_RAISE, lowered) }
        repeat(2) { counter.update(ExerciseType.ARM_RAISE, raised) }
        repeat(2) { counter.update(ExerciseType.ARM_RAISE, lowered) }
        assertEquals(1, counter.update(ExerciseType.ARM_RAISE, lowered).count)
    }

    @Test
    fun stillnessDoesNotCountForAnySupportedExercise() {
        val poses = listOf(
            ExerciseType.JUMPING_JACKS to jumpingJackLandmarks(false),
            ExerciseType.PULL_UPS to armLandmarks(170.0),
            ExerciseType.PUSH_UPS to armLandmarks(170.0),
            ExerciseType.RUSSIAN_TWISTS to twistLandmarks(0.0f),
            ExerciseType.SQUAT to squatLandmarks(180.0),
            ExerciseType.ARM_RAISE to armRaiseLandmarks(0.55f),
            ExerciseType.KNEE_EXTENSION to squatLandmarks(90.0),
            ExerciseType.SIT_TO_STAND to squatLandmarks(90.0)
        )

        poses.forEach { (type, pose) ->
            val counter = ExerciseRepCounter(stableFramesRequired = 3)
            repeat(30) { counter.update(type, pose) }
            assertEquals("$type should remain at zero while still", 0, counter.update(type, pose).count)
        }
    }

    @Test
    fun completeCyclesCountOnceForAllSupportedExercises() {
        val cycles = listOf(
            ExerciseType.JUMPING_JACKS to listOf(jumpingJackLandmarks(false), jumpingJackLandmarks(true), jumpingJackLandmarks(true), jumpingJackLandmarks(false)),
            ExerciseType.PULL_UPS to listOf(armLandmarks(170.0), armLandmarks(120.0), armLandmarks(80.0), armLandmarks(170.0)),
            ExerciseType.PUSH_UPS to listOf(armLandmarks(170.0), armLandmarks(120.0), armLandmarks(90.0), armLandmarks(170.0)),
            ExerciseType.RUSSIAN_TWISTS to listOf(twistLandmarks(0.0f), twistLandmarks(0.16f), twistLandmarks(0.3f), twistLandmarks(0.0f)),
            ExerciseType.SQUAT to listOf(squatLandmarks(180.0), squatLandmarks(130.0), squatLandmarks(90.0), squatLandmarks(180.0)),
            ExerciseType.ARM_RAISE to listOf(armRaiseLandmarks(0.55f), armRaiseLandmarks(0.15f), armRaiseLandmarks(0.05f), armRaiseLandmarks(0.55f)),
            ExerciseType.KNEE_EXTENSION to listOf(squatLandmarks(90.0), squatLandmarks(140.0), squatLandmarks(170.0), squatLandmarks(90.0)),
            ExerciseType.SIT_TO_STAND to listOf(squatLandmarks(90.0), squatLandmarks(140.0), squatLandmarks(180.0), squatLandmarks(90.0))
        )

        cycles.forEach { (type, phases) ->
            val counter = ExerciseRepCounter(stableFramesRequired = 2)
            phases.forEach { pose -> repeat(2) { counter.update(type, pose) } }
            val count = counter.update(type, phases.last()).count
            assertEquals("$type should count one complete cycle; actual=$count", 1, count)
        }
    }

    private fun squatLandmarks(kneeAngle: Double): List<BodyLandmark> {
        val ankle = if (kneeAngle < 120.0) {
            BodyLandmark(28, "right_ankle", 1f, 1f)
        } else {
            BodyLandmark(28, "right_ankle", 0f, 2f)
        }
        return listOf(
            BodyLandmark(24, "right_hip", 0f, 0f),
            BodyLandmark(26, "right_knee", 0f, 1f),
            ankle
        )
    }

    private fun armRaiseLandmarks(wristY: Float): List<BodyLandmark> = listOf(
        BodyLandmark(11, "left_shoulder", 0.4f, 0.3f),
        BodyLandmark(12, "right_shoulder", 0.6f, 0.3f),
        BodyLandmark(13, "left_elbow", 0.3f, wristY + 0.05f),
        BodyLandmark(14, "right_elbow", 0.7f, wristY + 0.05f),
        BodyLandmark(15, "left_wrist", 0.25f, wristY),
        BodyLandmark(16, "right_wrist", 0.75f, wristY)
    )

    private fun armLandmarks(elbowAngle: Double): List<BodyLandmark> {
        val elbow = if (elbowAngle <= 100.0) {
            BodyLandmark(14, "right_elbow", 0.5f, 0.5f)
        } else {
            BodyLandmark(14, "right_elbow", 0.5f, 0.5f)
        }
        val wrist = if (elbowAngle <= 100.0) {
            BodyLandmark(16, "right_wrist", 0.8f, 0.5f)
        } else {
            BodyLandmark(16, "right_wrist", 0.5f, 0.8f)
        }
        return listOf(
            BodyLandmark(12, "right_shoulder", 0.5f, 0.2f),
            elbow,
            wrist
        )
    }

    private fun jumpingJackLandmarks(open: Boolean): List<BodyLandmark> = listOf(
        BodyLandmark(11, "left_shoulder", 0.4f, 0.3f),
        BodyLandmark(12, "right_shoulder", 0.6f, 0.3f),
        BodyLandmark(15, "left_wrist", 0.2f, if (open) 0.1f else 0.5f),
        BodyLandmark(16, "right_wrist", 0.8f, if (open) 0.1f else 0.5f),
        BodyLandmark(23, "left_hip", 0.45f, 0.5f),
        BodyLandmark(24, "right_hip", 0.55f, 0.5f),
        BodyLandmark(27, "left_ankle", if (open) 0.2f else 0.43f, 0.9f),
        BodyLandmark(28, "right_ankle", if (open) 0.8f else 0.57f, 0.9f)
    )

    private fun twistLandmarks(offset: Float): List<BodyLandmark> = listOf(
        BodyLandmark(23, "left_hip", 0.45f, 0.5f),
        BodyLandmark(24, "right_hip", 0.55f, 0.5f),
        BodyLandmark(15, "left_wrist", 0.5f + offset, 0.4f),
        BodyLandmark(16, "right_wrist", 0.5f + offset, 0.4f)
    )
}
