package com.iqoo.wellness.engine.posture

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.ArrayDeque
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Android ONNX-based Pose Detector implementing Nirmit's 8-Exercise & Posture Pipeline.
 *
 * Pipeline:
 * 1. ML Kit Pose Detection â†’ 33 landmarks
 * 2. Feature extraction + JSON scaler preprocessing
 *    - 10 angles â†’ original_5_exercises.onnx  (Jumping Jacks, Pull ups, Push Ups, Russian twists, Squats)
 *    - 132 coords â†’ wlu_exercise.onnx          (Arm Raise, Knee Extension, Sit To Stand)
 *    - 132 coords â†’ wlu_posture.onnx           (Incorrect / Correct)
 * 3. Both exercise models run; the one with the higher top-1 probability wins.
 * 4. Top-1 label is displayed directly â€” NO confidence/margin/movement/geometry/stability gate.
 *
 * Only two rejection cases exist:
 *   NO_VALID_POSE  â€” ML Kit returns no landmarks or required landmarks are missing/off-screen.
 *   INFERENCE_FAILURE â€” ONNX session returned an exception or produced no usable output.
 */
class ONNXExercisePoseDetector(private val context: Context) : PoseDetector, AutoCloseable {

    private data class ExercisePrediction(
        val label: String,
        val confidence: Float,
        val margin: Float
    )

    private val mlKitDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val ortEnv = OrtEnvironment.getEnvironment()

    // Sessions for ONNX models
    private val origSession: OrtSession?
    private val wluExSession: OrtSession?
    private val wluPostSession: OrtSession?

    // Scaler parameters (mean and scale arrays)
    private val origMean: FloatArray
    private val origScale: FloatArray
    private val origLabels: List<String>

    private val wluExMean: FloatArray
    private val wluExScale: FloatArray
    private val wluExLabels: List<String>

    private val wluPostMean: FloatArray
    private val wluPostScale: FloatArray
    private val wluPostLabels: List<String>

    // Short smoothing prevents one noisy model frame from changing the counter type.
    private val exerciseSmoothing = 3
    private val exerciseHistory = ArrayDeque<String>()
    private val repCounter = ExerciseRepCounter()
    private var previousNormalizedPose: Map<Int, Pair<Float, Float>>? = null

    init {
        // Load ONNX sessions from assets
        origSession = createOrtSession("original_5_exercises.onnx")
        wluExSession = createOrtSession("wlu_exercise.onnx")
        wluPostSession = createOrtSession("wlu_posture.onnx")

        // Load JSON Scalers & Labels
        val (oMean, oScale) = loadScalerJson("original_scaler.json")
        origMean = oMean
        origScale = oScale
        origLabels = loadLabelsJson("original_labels.json", listOf("Jumping Jacks", "Pull ups", "Push Ups", "Russian twists", "Squats"))

        val (wExMean, wExScale) = loadScalerJson("wlu_exercise_scaler.json")
        wluExMean = wExMean
        wluExScale = wExScale
        wluExLabels = loadLabelsJson("wlu_exercise_labels.json", listOf("Arm Raise", "Knee Extension", "Sit To Stand"))

        val (wPostMean, wPostScale) = loadScalerJson("wlu_posture_scaler.json")
        wluPostMean = wPostMean
        wluPostScale = wPostScale
        wluPostLabels = loadLabelsJson("wlu_posture_labels.json", listOf("Incorrect", "Correct"))
    }

    private fun createOrtSession(assetName: String): OrtSession? {
        return try {
            val bytes = context.assets.open(assetName).readBytes()
            val session = ortEnv.createSession(bytes, OrtSession.SessionOptions())
            android.util.Log.i(
                "POSTURE_ONNX",
                "session=$assetName inputs=${session.inputNames} outputs=${session.outputNames} outputInfo=${session.outputInfo}"
            )
            session
        } catch (e: Exception) {
            android.util.Log.e("POSTURE_ONNX", "Failed to load $assetName: ${e.message}", e)
            null
        }
    }

    private fun loadScalerJson(assetName: String): Pair<FloatArray, FloatArray> {
        return try {
            val jsonString = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val meanArr = json.getJSONArray("mean")
            val scaleArr = json.getJSONArray("scale")

            val mean = FloatArray(meanArr.length()) { meanArr.getDouble(it).toFloat() }
            val scale = FloatArray(scaleArr.length()) { scaleArr.getDouble(it).toFloat() }
            Pair(mean, scale)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(FloatArray(0), FloatArray(0))
        }
    }

    private fun loadLabelsJson(assetName: String, fallback: List<String>): List<String> {
        return try {
            val jsonString = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val jsonArr = JSONArray(jsonString)
            List(jsonArr.length()) { jsonArr.getString(it) }
        } catch (e: Exception) {
            fallback
        }
    }

    override suspend fun detectPose(frameData: ByteArray?): List<BodyLandmark> {
        // A byte-only frame has no pose image; never invent landmarks for inference.
        return emptyList()
    }

    /**
     * Processes a Bitmap frame directly through Google ML Kit + ONNX models.
     */
    suspend fun processFrame(bitmap: Bitmap, selectedExercise: ExerciseType): PostureFeedback {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val pose = try {
            mlKitDetector.process(inputImage).await()
        } catch (error: Exception) {
            android.util.Log.e("MLKIT_POSE", "success=false reason=${error.message}", error)
            repCounter.onInvalidPose()
            return noPoseFeedback(
                selectedExercise,
                PoseStatus.INSUFFICIENT,
                "Pose detection failed. Move into camera view."
            )
        }

        val landmarks = pose.allPoseLandmarks
        android.util.Log.d("MLKIT_POSE", "success=true poseDetected=${landmarks.isNotEmpty()} landmarkCount=${landmarks.size}")
        android.util.Log.d(
            "POSTURE",
            "ML Kit landmarks=${landmarks.size}, ids=${landmarks.map { it.landmarkType }.sorted()}"
        )
        landmarks.forEach { landmark ->
            android.util.Log.d(
                "POSE_LANDMARK",
                "type=${landmark.landmarkType} x=${landmark.position.x} y=${landmark.position.y} " +
                    "z=${landmark.position3D.z} likelihood=${landmark.inFrameLikelihood} " +
                    "usable=${isUsable(landmark)}"
            )
        }
        if (landmarks.isEmpty()) {
            repCounter.onInvalidPose()
            previousNormalizedPose = null
            android.util.Log.d("POSTURE", "valid pose=no; status=NO_PERSON; inference executed=no")
            return noPoseFeedback(selectedExercise, PoseStatus.NO_PERSON, "No person detected. Step into the camera frame.")
        }
        if (!hasValidPose(landmarks, selectedExercise)) {
            repCounter.onInvalidPose()
            previousNormalizedPose = null
            android.util.Log.d("POSTURE", "valid pose=no; status=INSUFFICIENT; inference executed=no; landmarks=${landmarks.size}")
            return noPoseFeedback(selectedExercise, PoseStatus.INSUFFICIENT, "Pose not clear. Make sure your full body is visible.")
        }

        val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
        val imgH = bitmap.height.toFloat().coerceAtLeast(1f)

        val bodyLandmarks = convertLandmarks(landmarks, imgW, imgH)
        val usableBodyLandmarks = bodyLandmarks.filter { it.visibility >= 0.5f }
        val movementScore = updateMovementScore(bodyLandmarks)   // diagnostic only

        // â”€â”€ ONNX inference â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val feat132 = extract132Features(landmarks, imgW, imgH)
        var origPred  = ExercisePrediction("Unknown", 0f, 0f)
        var wluExPred = ExercisePrediction("Unknown", 0f, 0f)

        if (origSession != null) {
            val feat10Scaled = scaleFeatures(extract10Features(landmarks), origMean, origScale)
            origPred = runOriginalInference(feat10Scaled)
        }
        if (wluExSession != null) {
            val feat132ExScaled = scaleFeatures(feat132, wluExMean, wluExScale)
            wluExPred = runWluExerciseInference(feat132ExScaled)
        }

        // â”€â”€ Select best model output: higher top-1 wins â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // If both returned "Unknown" (both sessions failed), report inference failure.
        val bothFailed = origPred.label == "Unknown" && wluExPred.label == "Unknown"
        if (bothFailed) {
            repCounter.onInvalidPose()
            android.util.Log.w("EXERCISE_DEBUG", "poseValid=true predictedClass=NONE reason=INFERENCE_FAILURE")
            return PostureFeedback(
                exerciseType = selectedExercise,
                repCount = 0,
                currentState = ExerciseState.READY,
                primaryAngleDegrees = 0.0,
                isFormCorrect = false,
                feedbackMessage = "Exercise detection unavailable.",
                landmarks = bodyLandmarks,
                confidence = 0f,
                detectedActivity = "Exercise detection unavailable",
                poseStatus = PoseStatus.NO_EXERCISE
            )
        }

        val selected = if (origPred.confidence >= wluExPred.confidence && origPred.label != "Unknown") {
            origPred
        } else if (wluExPred.label != "Unknown") {
            wluExPred
        } else {
            origPred
        }
        // top-1 label is the exercise â€” no further gate
        exerciseHistory.addLast(selected.label)
        if (exerciseHistory.size > exerciseSmoothing) exerciseHistory.removeFirst()
        val finalExerciseLabel = getMostFrequent(exerciseHistory)

        // â”€â”€ Posture model (separate, does NOT affect exercise classification) â”€â”€â”€â”€â”€
        var finalPostureLabel = "Assessingâ€¦"
        var isCorrect = false
        var postureConfidence = 0.0f
        if (wluPostSession != null) {
            val feat132PostScaled = scaleFeatures(feat132, wluPostMean, wluPostScale)
            val (pLabel, pConf) = runWluPostureInference(feat132PostScaled)
            finalPostureLabel = pLabel
            postureConfidence = pConf
            isCorrect = finalPostureLabel.equals("Correct", ignoreCase = true)
        }

        // â”€â”€ Debug log â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        android.util.Log.i(
            "EXERCISE_DEBUG",
            "poseValid=true" +
            " predictedClass=$finalExerciseLabel" +
            " predictedLabel=$finalExerciseLabel" +
            " top1=${selected.confidence}" +
            " origLabel=${origPred.label} origConf=${origPred.confidence}" +
            " wluLabel=${wluExPred.label} wluConf=${wluExPred.confidence}" +
            " movementScore=$movementScore" +
            " posture=$finalPostureLabel postureConf=$postureConfidence"
        )

        // The workout counter follows the selected session exercise. The model
        // result remains independent and is exposed as detectedActivity.
        val exerciseForCounting = selectedExercise
        val repSnapshot = repCounter.update(exerciseForCounting, usableBodyLandmarks)
        android.util.Log.d(
            "REP_COUNTER",
            "type=$exerciseForCounting landmarks=${usableBodyLandmarks.size} " +
                "state=${repSnapshot.state} count=${repSnapshot.count}"
        )
        android.util.Log.d(
            "EXERCISE_CONFIDENCE_TRACE",
            "detector=${selected.confidence} engine=${selected.confidence} " +
                "selectedExercise=${selectedExercise.displayName} detectedExercise=$finalExerciseLabel " +
                "confidence=${selected.confidence}"
        )

        val statusMsg = if (isCorrect) {
            "Great form. ${repSnapshot.instruction}"
        } else {
            "Adjust posture. ${repSnapshot.instruction}"
        }

        return PostureFeedback(
            exerciseType = selectedExercise,
            repCount = repSnapshot.count,
            currentState = repSnapshot.state,
            primaryAngleDegrees = repSnapshot.angleDegrees,
            isFormCorrect = isCorrect,
            feedbackMessage = statusMsg,
            landmarks = bodyLandmarks,
            confidence = postureConfidence,
            exerciseConfidence = selected.confidence,
            detectedActivity = finalExerciseLabel,
            poseStatus = PoseStatus.VALID
        )
    }

    private fun exerciseTypeForLabel(label: String, fallback: ExerciseType): ExerciseType {
        val normalized = label.trim().lowercase()
        return when {
            normalized.contains("jump") -> ExerciseType.JUMPING_JACKS
            normalized.contains("pull") -> ExerciseType.PULL_UPS
            normalized.contains("push") -> ExerciseType.PUSH_UPS
            normalized.contains("russian") -> ExerciseType.RUSSIAN_TWISTS
            normalized.contains("squat") -> ExerciseType.SQUAT
            normalized.contains("arm raise") -> ExerciseType.ARM_RAISE
            normalized.contains("knee") -> ExerciseType.KNEE_EXTENSION
            normalized.contains("sit") -> ExerciseType.SIT_TO_STAND
            else -> fallback
        }
    }

    /** Diagnostic-only inter-frame movement score. Never gates classification. */
    private fun updateMovementScore(landmarks: List<BodyLandmark>): Float {
        val points = landmarks.associateBy { it.id }
        val hipCX = averageX(points, 23, 24)
        val hipCY = averageY(points, 23, 24)
        val shCX  = averageX(points, 11, 12)
        val shCY  = averageY(points, 11, 12)
        val bodyScale = hypot((shCX - hipCX).toDouble(), (shCY - hipCY).toDouble())
            .toFloat().coerceAtLeast(0.05f)
        val normalized = listOf(11,12,13,14,15,16,23,24,25,26,27,28,31,32).mapNotNull { id ->
            val p = points[id] ?: return@mapNotNull null
            if (p.visibility < 0.5f) null
            else id to Pair((p.x - hipCX) / bodyScale, (p.y - hipCY) / bodyScale)
        }.toMap()

        val prev = previousNormalizedPose
        previousNormalizedPose = normalized
        if (prev == null || normalized.isEmpty()) return 0f

        val shared = normalized.keys.intersect(prev.keys)
        if (shared.isEmpty()) return 0f
        return shared.map { id ->
            val c = normalized.getValue(id); val p = prev.getValue(id)
            hypot((c.first - p.first).toDouble(), (c.second - p.second).toDouble()).toFloat()
        }.average().toFloat()
    }

    private fun averageX(points: Map<Int, BodyLandmark>, first: Int, second: Int): Float =
        ((points[first]?.x ?: 0f) + (points[second]?.x ?: 0f)) / 2f

    private fun averageY(points: Map<Int, BodyLandmark>, first: Int, second: Int): Float =
        ((points[first]?.y ?: 0f) + (points[second]?.y ?: 0f)) / 2f

    private fun noPoseFeedback(
        selectedExercise: ExerciseType,
        status: PoseStatus,
        message: String
    ) = PostureFeedback(
        exerciseType = selectedExercise,
        repCount = 0,
        currentState = ExerciseState.READY,
        primaryAngleDegrees = 0.0,
        isFormCorrect = false,
        feedbackMessage = message,
        landmarks = emptyList(),
        confidence = 0f,
        detectedActivity = selectedExercise.displayName,
        poseStatus = status
    )

    private fun hasValidPose(landmarks: List<PoseLandmark>, selectedExercise: ExerciseType): Boolean {
        if (landmarks.isEmpty()) return false

        val byType = landmarks.associateBy { it.landmarkType }
        val usable = landmarks.count {
            isUsable(it)
        }
        val required = when (selectedExercise) {
            ExerciseType.ARM_RAISE -> listOf(
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
            )
            ExerciseType.PUSH_UPS, ExerciseType.PULL_UPS -> listOf(
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
            )
            ExerciseType.RUSSIAN_TWISTS -> listOf(
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
            )
            else -> listOf(
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
            )
        }
        val requiredUsable = required.count { type -> byType[type]?.let(::isUsable) == true }
        val minimumRequired = when (selectedExercise) {
            ExerciseType.ARM_RAISE -> 3
            ExerciseType.PUSH_UPS, ExerciseType.PULL_UPS -> 4
            else -> 6
        }
        if (usable < 6 || requiredUsable < minimumRequired) {
            android.util.Log.d(
                "POSTURE",
                "valid pose=no; usableLandmarks=$usable requiredUsable=$requiredUsable " +
                    "required=$minimumRequired exercise=${selectedExercise.displayName}"
            )
            return false
        }
        android.util.Log.d(
            "POSTURE",
            "valid pose=yes usableLandmarks=$usable requiredUsable=$requiredUsable " +
                "requestedExercise=${selectedExercise.displayName}"
        )
        return true
    }

    private fun isUsable(landmark: PoseLandmark): Boolean =
        landmark.inFrameLikelihood >= 0.5f &&
            landmark.position.x.isFinite() &&
            landmark.position.y.isFinite() &&
            landmark.position3D.z.isFinite()

    private fun extract10Features(landmarks: List<PoseLandmark>): FloatArray {
        // Helper map by landmark type
        val map = landmarks.associateBy { it.landmarkType }

        fun getPt(type: Int): FloatArray {
            val lm = map[type]
            return if (lm != null) floatArrayOf(lm.position.x, lm.position.y) else floatArrayOf(0f, 0f)
        }

        fun getVis(type: Int): Float {
            return map[type]?.inFrameLikelihood ?: 0f
        }

        // Left side
        val lSh = getPt(PoseLandmark.LEFT_SHOULDER)
        val lEl = getPt(PoseLandmark.LEFT_ELBOW)
        val lWr = getPt(PoseLandmark.LEFT_WRIST)
        val lHp = getPt(PoseLandmark.LEFT_HIP)
        val lKn = getPt(PoseLandmark.LEFT_KNEE)
        val lAn = getPt(PoseLandmark.LEFT_ANKLE)
        val lFt = getPt(PoseLandmark.LEFT_FOOT_INDEX)
        val lVis = (getVis(PoseLandmark.LEFT_SHOULDER) + getVis(PoseLandmark.LEFT_ELBOW) +
                    getVis(PoseLandmark.LEFT_HIP) + getVis(PoseLandmark.LEFT_KNEE)) / 4.0f

        // Right side
        val rSh = getPt(PoseLandmark.RIGHT_SHOULDER)
        val rEl = getPt(PoseLandmark.RIGHT_ELBOW)
        val rWr = getPt(PoseLandmark.RIGHT_WRIST)
        val rHp = getPt(PoseLandmark.RIGHT_HIP)
        val rKn = getPt(PoseLandmark.RIGHT_KNEE)
        val rAn = getPt(PoseLandmark.RIGHT_ANKLE)
        val rFt = getPt(PoseLandmark.RIGHT_FOOT_INDEX)
        val rVis = (getVis(PoseLandmark.RIGHT_SHOULDER) + getVis(PoseLandmark.RIGHT_ELBOW) +
                    getVis(PoseLandmark.RIGHT_HIP) + getVis(PoseLandmark.RIGHT_KNEE)) / 4.0f

        fun computeSideAngles(sh: FloatArray, el: FloatArray, wr: FloatArray, hp: FloatArray, kn: FloatArray, an: FloatArray, ft: FloatArray): FloatArray {
            val shAng = angle3Pt(el, sh, hp).toFloat()
            val elAng = angle3Pt(sh, el, wr).toFloat()
            val hpAng = angle3Pt(sh, hp, kn).toFloat()
            val knAng = angle3Pt(hp, kn, an).toFloat()
            val anAng = angle3Pt(kn, an, ft).toFloat()

            val shGrd = groundAngle2Pt(sh, el).toFloat()
            val elGrd = groundAngle2Pt(el, wr).toFloat()
            val hpGrd = groundAngle2Pt(hp, kn).toFloat()
            val knGrd = groundAngle2Pt(kn, an).toFloat()
            val anGrd = groundAngle2Pt(an, ft).toFloat()

            return floatArrayOf(shAng, elAng, hpAng, knAng, anAng, shGrd, elGrd, hpGrd, knGrd, anGrd)
        }

        val lAngles = computeSideAngles(lSh, lEl, lWr, lHp, lKn, lAn, lFt)
        val rAngles = computeSideAngles(rSh, rEl, rWr, rHp, rKn, rAn, rFt)

        return when {
            rVis > lVis + 0.1f -> rAngles
            lVis > rVis + 0.1f -> lAngles
            else -> FloatArray(10) { i -> (lAngles[i] + rAngles[i]) / 2.0f }
        }
    }

    private fun extract132Features(landmarks: List<PoseLandmark>, imgW: Float, imgH: Float): FloatArray {
        val features = FloatArray(132)
        val map = landmarks.associateBy { it.landmarkType }
        for (i in 0 until 33) {
            val lm = map[i]
            if (lm != null) {
                features[i * 4] = lm.position.x / imgW
                features[i * 4 + 1] = lm.position.y / imgH
                // MediaPipe training z is normalized by image width; ML Kit exposes z in image units.
                features[i * 4 + 2] = lm.position3D.z / imgW
                features[i * 4 + 3] = lm.inFrameLikelihood
            }
        }
        return features
    }

    private fun scaleFeatures(features: FloatArray, mean: FloatArray, scale: FloatArray): FloatArray {
        if (mean.size != features.size || scale.size != features.size) return features
        val scaled = FloatArray(features.size)
        for (i in features.indices) {
            val s = if (scale[i] != 0f) scale[i] else 1f
            scaled[i] = (features[i] - mean[i]) / s
        }
        return scaled
    }

    private fun runOriginalInference(scaledFeat: FloatArray): ExercisePrediction {
        val session = origSession ?: return ExercisePrediction("Unknown", 0f, 0f)
        return try {
            val shape = longArrayOf(1, scaledFeat.size.toLong())
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(scaledFeat), shape)
            val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
            android.util.Log.d("POSTURE_ONNX", "model=original inputShape=${shape.contentToString()} outputCount=${result.size()}")
            val classIdx = ((result[0].value as LongArray)[0]).toInt()
            val (confidence, margin) = probabilityAndMargin(result, classIdx.toLong())
            val label = origLabels.getOrElse(classIdx) { "Unknown" }
            android.util.Log.i("EXERCISE_INFERENCE", "label=$label class=$classIdx confidence=$confidence")
            ExercisePrediction(label, confidence, margin)
        } catch (e: Exception) {
            android.util.Log.e("EXERCISE_INFERENCE", "original inference failed: ${e.message}", e)
            ExercisePrediction("Unknown", 0f, 0f)
        }
    }

    private fun runWluExerciseInference(scaledFeat: FloatArray): ExercisePrediction {
        val session = wluExSession ?: return ExercisePrediction("Unknown", 0f, 0f)
        return try {
            val shape = longArrayOf(1, scaledFeat.size.toLong())
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(scaledFeat), shape)
            val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
            android.util.Log.d("POSTURE_ONNX", "model=exercise inputShape=${shape.contentToString()} outputCount=${result.size()}")
            val rawOutput = result[0].value
            val label = if (rawOutput is Array<*>) {
                rawOutput[0].toString()
            } else {
                "Unknown"
            }
            val (confidence, margin) = probabilityAndMargin(result, label)
            android.util.Log.i("EXERCISE_INFERENCE", "label=$label confidence=$confidence")
            ExercisePrediction(label, confidence, margin)
        } catch (e: Exception) {
            android.util.Log.e("EXERCISE_INFERENCE", "WLU inference failed: ${e.message}", e)
            ExercisePrediction("Unknown", 0f, 0f)
        }
    }

    private fun probabilityAndMargin(result: OrtSession.Result, key: Any): Pair<Float, Float> {
        if (result.size() <= 1) return 0f to 0f
        val rawOutput = result[1].value
        val probabilityMap = firstProbabilityMap(rawOutput) ?: run {
            android.util.Log.e(
                "EXERCISE_INFERENCE",
                "probability output type=${rawOutput?.javaClass?.name} is not a map"
            )
            return 0f to 0f
        }
        val values = probabilityMap.entries.mapNotNull { (entryKey, entryValue) ->
            val value = (entryValue as? Number)?.toFloat() ?: return@mapNotNull null
            normalizeProbabilityKey(entryKey) to value
        }.sortedByDescending { it.second }
        val normalizedKey = normalizeProbabilityKey(key)
        val selected = values.firstOrNull { it.first == normalizedKey }?.second ?: 0f
        val second = values.filterNot { it.first == normalizedKey }.firstOrNull()?.second ?: 0f
        android.util.Log.d(
            "EXERCISE_INFERENCE",
            "probabilityType=${rawOutput?.javaClass?.name} probabilities=$values " +
                "selectedKey=$normalizedKey selected=$selected"
        )
        return selected to (selected - second).coerceAtLeast(0f)
    }

    private fun normalizeProbabilityKey(key: Any?): String =
        key?.toString()?.trim()?.removeSurrounding("\"")?.lowercase() ?: ""

    private fun firstProbabilityMap(value: Any?): Map<*, *>? {
        return when (value) {
            is Map<*, *> -> value
            is Iterable<*> -> value.firstOrNull() as? Map<*, *>
            else -> if (value != null && value.javaClass.isArray && java.lang.reflect.Array.getLength(value) > 0) {
                java.lang.reflect.Array.get(value, 0) as? Map<*, *>
            } else {
                null
            }
        }
    }

    private fun runWluPostureInference(scaledFeat: FloatArray): Pair<String, Float> {
        val session = wluPostSession ?: return Pair("Insufficient pose", 0f)
        return try {
            val shape = longArrayOf(1, scaledFeat.size.toLong())
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(scaledFeat), shape)
            val result = session.run(mapOf(session.inputNames.iterator().next() to tensor))
            android.util.Log.d("POSTURE_ONNX", "model=posture inputShape=${shape.contentToString()} outputCount=${result.size()}")
            val classIdx = ((result[0].value as LongArray)[0]).toInt()
            val confidence = probabilityFor(result, classIdx.toLong())
            val label = wluPostLabels.getOrElse(classIdx) { "Unknown" }
            android.util.Log.i(
                "POSTURE_ONNX",
                "outputs=${result.size()} output0Type=${result[0].value::class.java.name} output0Raw=${result[0].value} output1Type=${result[1].value::class.java.name} output1Raw=${result[1].value}"
            )
            android.util.Log.i("POSTURE_CONF", "classIndex=$classIdx classLabel=$label classProbability=$confidence")
            Pair(label, confidence)
        } catch (e: Exception) {
            android.util.Log.e("POSTURE_ONNX", "wlu_posture inference failed: ${e.message}", e)
            Pair("Insufficient pose", 0f)
        }
    }

    private fun probabilityFor(result: OrtSession.Result, key: Any): Float {
        if (result.size() <= 1) return 0f
        val probabilityMap = firstProbabilityMap(result[1].value) ?: run {
            android.util.Log.e("POSTURE_CONF", "Probability output is not a map: ${result[1].value?.javaClass?.name}")
            return 0f
        }
        val entries = probabilityMap.entries.mapNotNull { entry ->
            val value = (entry.value as? Number)?.toFloat() ?: return@mapNotNull null
            val numericKey = (entry.key as? Number)?.toLong()
            (numericKey?.toString() ?: entry.key.toString()) to value
        }
        android.util.Log.d("POSTURE_CONF", "lookupKey=$key mapKeys=${entries.map { it.first }} mapValues=${entries.map { it.second }}")
        val rawValue = entries.firstOrNull { it.first == key.toString() }?.second ?: run {
            android.util.Log.e("POSTURE_CONF", "No probability for class key=$key")
            return 0f
        }
        val sum = entries.sumOf { it.second.toDouble() }.toFloat()
        if (sum in 0.95f..1.05f && entries.all { it.second >= 0f }) return rawValue

        val maxValue = entries.maxOfOrNull { it.second } ?: return 0f
        val exponentials = entries.map { kotlin.math.exp((it.second - maxValue).toDouble()).toFloat() }
        val exponentialSum = exponentials.sum()
        val index = entries.indexOfFirst { it.first == key.toString() }
        return if (index >= 0 && exponentialSum > 0f) exponentials[index] / exponentialSum else 0f
    }

    private fun convertLandmarks(landmarks: List<PoseLandmark>, imgW: Float, imgH: Float): List<BodyLandmark> {
        return landmarks.map { lm ->
            BodyLandmark(
                id = lm.landmarkType,
                name = getLandmarkName(lm.landmarkType),
                x = lm.position.x / imgW,
                y = lm.position.y / imgH,
                z = lm.position3D.z / imgW,
                visibility = lm.inFrameLikelihood
            )
        }
    }

    private fun getLandmarkName(type: Int): String {
        return when (type) {
            PoseLandmark.NOSE -> "nose"
            PoseLandmark.LEFT_EYE -> "left_eye"
            PoseLandmark.RIGHT_EYE -> "right_eye"
            PoseLandmark.LEFT_EAR -> "left_ear"
            PoseLandmark.RIGHT_EAR -> "right_ear"
            PoseLandmark.LEFT_SHOULDER -> "left_shoulder"
            PoseLandmark.RIGHT_SHOULDER -> "right_shoulder"
            PoseLandmark.LEFT_ELBOW -> "left_elbow"
            PoseLandmark.RIGHT_ELBOW -> "right_elbow"
            PoseLandmark.LEFT_WRIST -> "left_wrist"
            PoseLandmark.RIGHT_WRIST -> "right_wrist"
            PoseLandmark.LEFT_HIP -> "left_hip"
            PoseLandmark.RIGHT_HIP -> "right_hip"
            PoseLandmark.LEFT_KNEE -> "left_knee"
            PoseLandmark.RIGHT_KNEE -> "right_knee"
            PoseLandmark.LEFT_ANKLE -> "left_ankle"
            PoseLandmark.RIGHT_ANKLE -> "right_ankle"
            else -> "landmark_$type"
        }
    }

    private fun angle3Pt(a: FloatArray, b: FloatArray, c: FloatArray): Double {
        val baX = a[0] - b[0]
        val baY = a[1] - b[1]
        val bcX = c[0] - b[0]
        val bcY = c[1] - b[1]

        val normBa = hypot(baX.toDouble(), baY.toDouble())
        val normBc = hypot(bcX.toDouble(), bcY.toDouble())
        if (normBa == 0.0 || normBc == 0.0) return 180.0

        val dot = (baX * bcX + baY * bcY).toDouble()
        val cosine = (dot / (normBa * normBc)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun groundAngle2Pt(p1: FloatArray, p2: FloatArray): Double {
        val dx = (p2[0] - p1[0]).toDouble()
        val dy = (p2[1] - p1[1]).toDouble()
        return Math.toDegrees(atan2(Math.abs(dy), Math.abs(dx)))
    }

    private fun getMostFrequent(deque: ArrayDeque<String>): String {
        if (deque.isEmpty()) return "Unknown"
        return deque.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: deque.last
    }

    override fun reset() {
        exerciseHistory.clear()
        repCounter.reset()
        previousNormalizedPose = null
        android.util.Log.d("POSTURE", "Pose history reset")
    }

    override fun close() {
        try {
            mlKitDetector.close()
            origSession?.close()
            wluExSession?.close()
            wluPostSession?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
