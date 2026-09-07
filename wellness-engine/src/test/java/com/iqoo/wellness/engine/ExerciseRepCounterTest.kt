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
}
