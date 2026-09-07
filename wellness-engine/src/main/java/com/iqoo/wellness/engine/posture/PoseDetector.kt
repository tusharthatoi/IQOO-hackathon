package com.iqoo.wellness.engine.posture

/**
 * On-device pose detection abstraction.
 * Single pose model producing 17 body landmarks for downstream geometric analysis.
 */
interface PoseDetector {
    suspend fun detectPose(frameData: ByteArray? = null): List<BodyLandmark>

    fun reset() = Unit
}

/**
 * Default on-device pose detector with realistic landmark generator for testing and demonstration.
 */
class OnDevicePoseDetector : PoseDetector {

    // Default standing posture landmarks (normalized [0, 1])
    private var simulatedLandmarks: List<BodyLandmark> = createStandingLandmarks()

    override suspend fun detectPose(frameData: ByteArray?): List<BodyLandmark> {
        return simulatedLandmarks
    }

    fun setLandmarks(landmarks: List<BodyLandmark>) {
        this.simulatedLandmarks = landmarks
    }

    companion object {
        fun createStandingLandmarks(): List<BodyLandmark> {
            return listOf(
                BodyLandmark(LandmarkIndices.NOSE, "nose", 0.5f, 0.1f),
                BodyLandmark(LandmarkIndices.LEFT_EYE, "left_eye", 0.48f, 0.08f),
                BodyLandmark(LandmarkIndices.RIGHT_EYE, "right_eye", 0.52f, 0.08f),
                BodyLandmark(LandmarkIndices.LEFT_EAR, "left_ear", 0.45f, 0.09f),
                BodyLandmark(LandmarkIndices.RIGHT_EAR, "right_ear", 0.55f, 0.09f),
                BodyLandmark(LandmarkIndices.LEFT_SHOULDER, "left_shoulder", 0.4f, 0.22f),
                BodyLandmark(LandmarkIndices.RIGHT_SHOULDER, "right_shoulder", 0.6f, 0.22f),
                BodyLandmark(LandmarkIndices.LEFT_ELBOW, "left_elbow", 0.35f, 0.38f),
                BodyLandmark(LandmarkIndices.RIGHT_ELBOW, "right_elbow", 0.65f, 0.38f),
                BodyLandmark(LandmarkIndices.LEFT_WRIST, "left_wrist", 0.35f, 0.52f),
                BodyLandmark(LandmarkIndices.RIGHT_WRIST, "right_wrist", 0.65f, 0.52f),
                BodyLandmark(LandmarkIndices.LEFT_HIP, "left_hip", 0.43f, 0.5f),
                BodyLandmark(LandmarkIndices.RIGHT_HIP, "right_hip", 0.57f, 0.5f),
                BodyLandmark(LandmarkIndices.LEFT_KNEE, "left_knee", 0.43f, 0.72f),
                BodyLandmark(LandmarkIndices.RIGHT_KNEE, "right_knee", 0.57f, 0.72f),
                BodyLandmark(LandmarkIndices.LEFT_ANKLE, "left_ankle", 0.43f, 0.92f),
                BodyLandmark(LandmarkIndices.RIGHT_ANKLE, "right_ankle", 0.57f, 0.92f)
            )
        }
    }
}
