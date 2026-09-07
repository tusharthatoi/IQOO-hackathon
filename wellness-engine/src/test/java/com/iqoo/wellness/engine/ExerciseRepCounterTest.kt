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
        assertEquals(1, counter.update(ExerciseType.SIT_TO_STAND, standing).count)
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
}
