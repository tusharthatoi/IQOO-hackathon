package com.iqoo.wellness.engine.food

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Real on-device Indian food classifier powered by TensorFlow Lite.
 * Loads production-trained MobileNetV3 model (quantized or fp32) and labels directly from assets.
 */
class TFLiteFoodRecognizer(
    private val context: Context,
    private val primaryModelName: String = "food_classifier_quantized.tflite",
    private val fallbackModelName: String = "food_classifier_fp32.tflite",
    private val labelFileName: String = "labels.txt",
    var confidenceThreshold: Float = 0.50f
) : FoodRecognizer, AutoCloseable {

    companion object {
        private const val TAG = "IQOO_WELLNESS"
        private const val INPUT_SIZE = 224
        private const val NUM_CHANNELS = 3
        private const val BYTES_PER_CHANNEL = 4 // Float32
        private const val EXPECTED_CLASSES = 80
    }

    private var interpreter: Interpreter? = null
    private val labels: MutableList<String> = ArrayList(EXPECTED_CLASSES)
    private var activeModelName: String = primaryModelName

    // Reusable buffers to minimize memory allocations during real-time camera streaming
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * BYTES_PER_CHANNEL
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer = Array(1) { FloatArray(EXPECTED_CLASSES) }
    private val intPixelValues = IntArray(INPUT_SIZE * INPUT_SIZE)

    init {
        loadLabels()
        initializeInterpreter()
    }

    private fun loadLabels() {
        labels.clear()
        try {
            context.assets.open(labelFileName).use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).buffered().useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        // Format is: "<index> <label_name>", e.g. "0 adhirasam"
                        val parts = trimmed.split(" ", limit = 2)
                        val labelName = if (parts.size == 2) parts[1] else parts[0]
                        labels.add(labelName)
                    }
                }
            }
            Log.d(TAG, "[FOOD_TFLITE] Loaded ${labels.size} food classes from $labelFileName")
            if (labels.size != EXPECTED_CLASSES) {
                Log.w(TAG, "[FOOD_TFLITE] Warning: Expected $EXPECTED_CLASSES labels, found ${labels.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FOOD_TFLITE] Failed to load labels from $labelFileName: ${e.message}", e)
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun initializeInterpreter() {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setCancellable(false)
        }

        try {
            activeModelName = primaryModelName
            val modelBuffer = loadModelFile(activeModelName)
            interpreter = Interpreter(modelBuffer, options)
            logModelDetails(activeModelName)
        } catch (e: Throwable) {
            Log.w(TAG, "[FOOD_TFLITE] Primary model '$primaryModelName' failed to initialize: ${e.message}. Attempting fallback to '$fallbackModelName'...", e)
            try {
                activeModelName = fallbackModelName
                val modelBuffer = loadModelFile(activeModelName)
                interpreter = Interpreter(modelBuffer, options)
                logModelDetails(activeModelName)
            } catch (fallbackEx: Throwable) {
                Log.e(TAG, "[FOOD_TFLITE] Fallback model '$fallbackModelName' also failed: ${fallbackEx.message}", fallbackEx)
                interpreter = null
            }
        }
    }

    val isModelLoaded: Boolean
        get() = interpreter != null

    val classCount: Int
        get() = labels.size

    private fun logModelDetails(modelName: String) {
        val currentInterpreter = interpreter ?: return
        val inputTensor = currentInterpreter.getInputTensor(0)
        val outputTensor = currentInterpreter.getOutputTensor(0)

        Log.i(TAG, "[FOOD_TFLITE] Model loaded: $modelName")
        Log.i(TAG, "[FOOD_TFLITE] Input shape: ${inputTensor.shape().contentToString()}, type: ${inputTensor.dataType()}")
        Log.i(TAG, "[FOOD_TFLITE] Output shape: ${outputTensor.shape().contentToString()}, type: ${outputTensor.dataType()}")
    }

    override suspend fun recognizeFood(imageData: ByteArray?): List<RecognizedFoodItem> = withContext(Dispatchers.Default) {
        if (imageData == null || imageData.isEmpty()) {
            return@withContext emptyList()
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: return@withContext emptyList()
        recognizeFood(bitmap)
    }

    private val gate: FoodGate = HeuristicFoodGate(context)
    private val stabilityTracker = TemporalStabilityTracker()

    override suspend fun recognizeFood(bitmap: Bitmap): List<RecognizedFoodItem> = withContext(Dispatchers.Default) {
        val currentInterpreter = interpreter
        if (currentInterpreter == null) {
            Log.e(TAG, "[FOOD_TFLITE] Interpreter is null. Model failed to load.")
            return@withContext emptyList()
        }

        val startTimeMs = SystemClock.uptimeMillis()

        // 1. Center crop and scale Bitmap to 224x224
        val preprocessedBitmap = prepareBitmap(bitmap)

        // 2. Preprocess into float buffer normalized to [-1.0, 1.0]
        synchronized(inputBuffer) {
            preprocessedBitmap.getPixels(
                intPixelValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE
            )

            inputBuffer.rewind()
            for (pixel in intPixelValues) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Normalization: (Pixel / 127.5) - 1.0
                inputBuffer.putFloat((r / 127.5f) - 1.0f)
                inputBuffer.putFloat((g / 127.5f) - 1.0f)
                inputBuffer.putFloat((b / 127.5f) - 1.0f)
            }

            // 3. Execute on-device TFLite inference
            outputBuffer[0].fill(0f)
            currentInterpreter.run(inputBuffer, outputBuffer)
        }

        val inferenceTimeMs = SystemClock.uptimeMillis() - startTimeMs
        val probabilities = outputBuffer[0]

        // 4. Extract Top-K predictions
        val topK = probabilities.indices
            .map { idx -> idx to probabilities[idx] }
            .sortedByDescending { it.second }
            .take(3)

        val (bestIdx, bestConfidence) = topK.firstOrNull() ?: (0 to 0f)
        val secondConfidence = topK.getOrNull(1)?.second ?: 0f
        val bestRawLabel = labels.getOrElse(bestIdx) { "unknown" }
        val bestDisplayName = formatFoodLabel(bestRawLabel)

        val topKSummary = topK.joinToString(", ") { (idx, conf) ->
            "${labels.getOrElse(idx) { "class_$idx" }} (${(conf * 1000).toInt() / 10.0}%)"
        }

        Log.i(TAG, "[FOOD_TFLITE] Prediction: $bestDisplayName, Confidence: ${(bestConfidence * 1000).toInt() / 10.0}% (Inference: ${inferenceTimeMs}ms)")
        Log.d(TAG, "[FOOD_TFLITE] Top-3: $topKSummary")

        // 5. Evaluate Food/Non-Food Gate (Heuristic fallback)
        val gateResult = gate.evaluate(
            bitmap = preprocessedBitmap,
            topClassIndex = bestIdx,
            topConfidence = bestConfidence,
            secondConfidence = secondConfidence
        )

        // 6. Temporal Stability Evaluation
        val stability = stabilityTracker.addPrediction(
            FramePrediction(
                dishId = bestRawLabel,
                dishName = bestDisplayName,
                confidence = bestConfidence,
                gateDecision = gateResult.decision
            )
        )

        // Generate thumbnail for UI preview
        val thumbnail = Bitmap.createScaledBitmap(preprocessedBitmap, 120, 120, true)

        val resultItem = when (stability.resolvedState) {
            FoodResultState.FOOD_DETECTED -> {
                RecognizedFoodItem(
                    foodId = stability.dominantDishId ?: bestRawLabel,
                    name = stability.dominantDishName ?: bestDisplayName,
                    confidence = stability.dominantConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 200.0,
                    state = FoodResultState.FOOD_DETECTED,
                    stateMessage = "Food detected",
                    thumbnail = thumbnail
                )
            }
            FoodResultState.NOT_FOOD -> {
                RecognizedFoodItem(
                    foodId = "not_food",
                    name = "Not Food",
                    confidence = bestConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 0.0,
                    state = FoodResultState.NOT_FOOD,
                    stateMessage = "This doesn't look like a food item. Try scanning a dish or meal.",
                    thumbnail = thumbnail
                )
            }
            FoodResultState.LOW_CONFIDENCE -> {
                RecognizedFoodItem(
                    foodId = "unknown",
                    name = "Unknown Food",
                    confidence = bestConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 0.0,
                    state = FoodResultState.LOW_CONFIDENCE,
                    stateMessage = "Hold steady and point the camera at a food item.",
                    thumbnail = thumbnail
                )
            }
            FoodResultState.SCANNING -> {
                RecognizedFoodItem(
                    foodId = "scanning",
                    name = "Scanning food...",
                    confidence = bestConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 0.0,
                    state = FoodResultState.SCANNING,
                    stateMessage = "Hold steady for best results",
                    thumbnail = thumbnail
                )
            }
        }

        listOf(resultItem)
    }

    private fun prepareBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        val cropSize = minOf(width, height)
        val cropX = (width - cropSize) / 2
        val cropY = (height - cropSize) / 2

        val cropped = if (width != height) {
            Bitmap.createBitmap(source, cropX, cropY, cropSize, cropSize)
        } else {
            source
        }

        return if (cropped.width != INPUT_SIZE || cropped.height != INPUT_SIZE) {
            Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        } else {
            cropped
        }
    }

    private fun formatFoodLabel(raw: String): String {
        return raw.split("_")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    override fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "[FOOD_TFLITE] Interpreter closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[FOOD_TFLITE] Error closing interpreter: ${e.message}", e)
        }
    }
}
