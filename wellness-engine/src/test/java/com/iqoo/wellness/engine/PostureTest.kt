package com.iqoo.wellness.engine

import com.iqoo.wellness.engine.posture.BicepCurlStateMachine
import com.iqoo.wellness.engine.posture.BodyLandmark
import com.iqoo.wellness.engine.posture.ExerciseState
import com.iqoo.wellness.engine.posture.JointAngleCalculator
import com.iqoo.wellness.engine.posture.LandmarkIndices
import com.iqoo.wellness.engine.posture.PushUpStateMachine
import com.iqoo.wellness.engine.posture.ShoulderPressStateMachine
import com.iqoo.wellness.engine.posture.SquatStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureTest {

    @Test
    fun testJointAngleTrigonometryRightAngle() {
        val angle = JointAngleCalculator.calculateAngle(
            aX = 0f, aY = 1f,
            bX = 0f, bY = 0f,
            cX = 1f, cY = 0f
        )
        assertEquals(90.0, angle, 0.1)
    }

    @Test
    fun testJointAngleTrigonometryStraightJoint() {
        val angle = JointAngleCalculator.calculateAngle(
            aX = 0f, aY = 1f,
            bX = 0f, bY = 0f,
            cX = 0f, cY = -1f
        )
        assertEquals(180.0, angle, 0.1)
    }

    @Test
    fun testJointAngleTrigonometryAcuteAngle() {
        val angle = JointAngleCalculator.calculateAngle(
            aX = 1f, aY = 1f,
            bX = 0f, bY = 0f,
            cX = 1f, cY = 0f
        )
        assertEquals(45.0, angle, 0.5)
    }

    @Test
    fun testSquatStateMachineFullRep() {
        val squatFsm = SquatStateMachine()
        val template = createBaseLandmarkList()

        // 1. Standing tall (180 deg)
        setRightLeg(template, hip = Pair(0.5f, 0.4f), knee = Pair(0.5f, 0.7f), ankle = Pair(0.5f, 1.0f))
        var feedback = squatFsm.processLandmarks(template)
        assertEquals(ExerciseState.READY, feedback.currentState)
        assertEquals(0, feedback.repCount)

        // 2. Descent (~117 deg)
        setRightLeg(template, hip = Pair(0.4f, 0.5f), knee = Pair(0.6f, 0.7f), ankle = Pair(0.5f, 1.0f))
        feedback = squatFsm.processLandmarks(template)
        assertEquals(ExerciseState.IN_REP, feedback.currentState)

        // 3. Peak depth: Thigh parallel to floor (90 deg)
        setRightLeg(template, hip = Pair(0.3f, 0.7f), knee = Pair(0.6f, 0.7f), ankle = Pair(0.6f, 1.0f))
        feedback = squatFsm.processLandmarks(template)
        assertEquals(ExerciseState.PEAK, feedback.currentState)
        assertTrue(feedback.isFormCorrect)

        // 4. Return to standing (180 deg) -> Rep completed!
        setRightLeg(template, hip = Pair(0.5f, 0.4f), knee = Pair(0.5f, 0.7f), ankle = Pair(0.5f, 1.0f))
        feedback = squatFsm.processLandmarks(template)
        assertEquals(1, feedback.repCount)
        assertTrue(feedback.feedbackMessage.contains("Rep 1 completed"))
    }

    @Test
    fun testSquatShallowDepthWarning() {
        val squatFsm = SquatStateMachine()
        val template = createBaseLandmarkList()

        // 1. Standing tall (180 deg)
        setRightLeg(template, hip = Pair(0.5f, 0.4f), knee = Pair(0.5f, 0.7f), ankle = Pair(0.5f, 1.0f))
        squatFsm.processLandmarks(template)

        // 2. Descent to 117 deg (IN_REP triggered)
        setRightLeg(template, hip = Pair(0.4f, 0.5f), knee = Pair(0.6f, 0.7f), ankle = Pair(0.5f, 1.0f))
        squatFsm.processLandmarks(template)

        // 3. Returns to standing without hitting depth threshold (<= 100 deg)
        setRightLeg(template, hip = Pair(0.5f, 0.4f), knee = Pair(0.5f, 0.7f), ankle = Pair(0.5f, 1.0f))
        val feedback = squatFsm.processLandmarks(template)

        // Must NOT award rep and must provide explainable form correction
        assertEquals(0, feedback.repCount)
        assertFalse("Form should be flagged incorrect for shallow rep", feedback.isFormCorrect)
        assertTrue(feedback.feedbackMessage.contains("Squat deeper"))
    }

    @Test
    fun testPushUpStateMachine() {
        val pushUpFsm = PushUpStateMachine()
        val template = createBaseLandmarkList()

        // 1. High plank (180 deg)
        setRightArm(template, shoulder = Pair(0.4f, 0.3f), elbow = Pair(0.4f, 0.6f), wrist = Pair(0.4f, 0.9f))
        var feedback = pushUpFsm.processLandmarks(template)
        assertEquals(ExerciseState.READY, feedback.currentState)

        // 2. Descending (~115 deg)
        setRightArm(template, shoulder = Pair(0.3f, 0.45f), elbow = Pair(0.5f, 0.6f), wrist = Pair(0.5f, 0.85f))
        feedback = pushUpFsm.processLandmarks(template)
        assertEquals(ExerciseState.IN_REP, feedback.currentState)

        // 3. Bottom flexion (90 deg)
        setRightArm(template, shoulder = Pair(0.2f, 0.6f), elbow = Pair(0.5f, 0.6f), wrist = Pair(0.5f, 0.9f))
        feedback = pushUpFsm.processLandmarks(template)
        assertEquals(ExerciseState.PEAK, feedback.currentState)

        // 4. Pressing up to lockout (180 deg)
        setRightArm(template, shoulder = Pair(0.4f, 0.3f), elbow = Pair(0.4f, 0.6f), wrist = Pair(0.4f, 0.9f))
        feedback = pushUpFsm.processLandmarks(template)
        assertEquals(1, feedback.repCount)
    }

    @Test
    fun testBicepCurlStateMachine() {
        val curlFsm = BicepCurlStateMachine()
        val template = createBaseLandmarkList()

        // 1. Arm hanging extended (180 deg)
        setRightArm(template, shoulder = Pair(0.5f, 0.2f), elbow = Pair(0.5f, 0.5f), wrist = Pair(0.5f, 0.8f))
        var feedback = curlFsm.processLandmarks(template)
        assertEquals(ExerciseState.READY, feedback.currentState)

        // 2. Mid-curl flexion (90 deg)
        setRightArm(template, shoulder = Pair(0.5f, 0.2f), elbow = Pair(0.5f, 0.5f), wrist = Pair(0.8f, 0.5f))
        feedback = curlFsm.processLandmarks(template)
        assertEquals(ExerciseState.IN_REP, feedback.currentState)

        // 3. Peak curl contraction (~45 deg)
        setRightArm(template, shoulder = Pair(0.5f, 0.2f), elbow = Pair(0.5f, 0.5f), wrist = Pair(0.65f, 0.35f))
        feedback = curlFsm.processLandmarks(template)
        assertEquals(ExerciseState.PEAK, feedback.currentState)

        // 4. Lowering back to extension (180 deg)
        setRightArm(template, shoulder = Pair(0.5f, 0.2f), elbow = Pair(0.5f, 0.5f), wrist = Pair(0.5f, 0.8f))
        feedback = curlFsm.processLandmarks(template)
        assertEquals(1, feedback.repCount)
    }

    private fun createBaseLandmarkList(): MutableList<BodyLandmark> {
        val list = mutableListOf<BodyLandmark>()
        for (i in 0..16) {
            list.add(BodyLandmark(i, "landmark_$i", 0.5f, 0.5f))
        }
        return list
    }

    private fun setRightLeg(list: MutableList<BodyLandmark>, hip: Pair<Float, Float>, knee: Pair<Float, Float>, ankle: Pair<Float, Float>) {
        list[LandmarkIndices.RIGHT_HIP] = BodyLandmark(LandmarkIndices.RIGHT_HIP, "r_hip", hip.first, hip.second)
        list[LandmarkIndices.RIGHT_KNEE] = BodyLandmark(LandmarkIndices.RIGHT_KNEE, "r_knee", knee.first, knee.second)
        list[LandmarkIndices.RIGHT_ANKLE] = BodyLandmark(LandmarkIndices.RIGHT_ANKLE, "r_ankle", ankle.first, ankle.second)
    }

    private fun setRightArm(list: MutableList<BodyLandmark>, shoulder: Pair<Float, Float>, elbow: Pair<Float, Float>, wrist: Pair<Float, Float>) {
        list[LandmarkIndices.RIGHT_SHOULDER] = BodyLandmark(LandmarkIndices.RIGHT_SHOULDER, "r_shoulder", shoulder.first, shoulder.second)
        list[LandmarkIndices.RIGHT_ELBOW] = BodyLandmark(LandmarkIndices.RIGHT_ELBOW, "r_elbow", elbow.first, elbow.second)
        list[LandmarkIndices.RIGHT_WRIST] = BodyLandmark(LandmarkIndices.RIGHT_WRIST, "r_wrist", wrist.first, wrist.second)
    }
}
