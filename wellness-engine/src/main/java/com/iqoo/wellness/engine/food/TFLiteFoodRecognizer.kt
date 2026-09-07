package com.iqoo.wellness.engine.food

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * On-device food classifier using the 10-class FP32 TensorFlow Lite model.
 * Input contract: FLOAT32 [1, 300, 300, 3], RGB, pixel values in 0-255 range (no normalization).
 * Output contract: FLOAT32 [1, 10].
 */
class TFLiteFoodRecognizer(
    private val context: Context,
    private val modelFileName: String = "food_classifier_fp32.tflite",
    private val labelFileName: String = "labels.txt",
    var confidenceThreshold: Float = 0.35f
) : FoodRecognizer, AutoCloseable {

    companion object {
        private const val TAG = "IQOO_WELLNESS"
        private const val NUM_CHANNELS = 3
        private const val BYTES_PER_FLOAT = 4
    }

    private var interpreter: Interpreter? = null
    private val labels: MutableList<String> = ArrayList()

    val classCount: Int get() = labels.size
    val isModelLoaded: Boolean get() = interpreter != null

    private var inputSize: Int = 300
    private var numOutputClasses: Int = 10

    private var inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 300 * 300 * NUM_CHANNELS * BYTES_PER_FLOAT).apply {
        order(ByteOrder.nativeOrder())
    }
    private var outputBuffer = Array(1) { FloatArray(10) }
    private var intPixelValues = IntArray(300 * 300)

    private val gate: FoodGate = HeuristicFoodGate(context, foodConfidenceThreshold = confidenceThreshold)
    private val stabilityTracker = TemporalStabilityTracker()
    private val inferenceLock = ReentrantLock()
    private val scanGeneration = AtomicLong(0L)

    override fun reset() {
        scanGeneration.incrementAndGet()
        stabilityTracker.reset()
        gate.reset()
        Log.i(TAG, "[FOOD_SCAN] TFLite recognizer state reset")
    }

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
                        val parts = trimmed.split(" ", limit = 2)
                        val labelName = if (parts.size == 2 && parts[0].toIntOrNull() != null) {
                            parts[1]
                        } else {
                            parts[0]
                        }
                        labels.add(labelName)
                    }
                }
            }
            Log.d(TAG, "[FOOD_TFLITE] Loaded ${labels.size} food classes from $labelFileName")
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
            val modelBuffer = loadModelFile(modelFileName)
            interpreter = Interpreter(modelBuffer, options)
            configureTensors()
            Log.i(TAG, "[FOOD_TFLITE] Successfully initialized production model: $modelFileName")
        } catch (e: Throwable) {
            Log.e(TAG, "[FOOD_TFLITE] Production model '$modelFileName' failed to initialize: ${e.message}", e)
            interpreter = null
        }
    }

    private fun configureTensors() {
        val currentInterpreter = interpreter ?: return

        // 1. Input Tensor Configuration [1, 300, 300, 3] FLOAT32
        val inputTensor = currentInterpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        if (inputShape.size >= 3) {
            inputSize = inputShape[1]
        }

        inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * NUM_CHANNELS * BYTES_PER_FLOAT).apply {
            order(ByteOrder.nativeOrder())
        }
        intPixelValues = IntArray(inputSize * inputSize)
        Log.i(TAG, "[FOOD_RECOGNIZER] Input tensor shape=${inputShape.contentToString()}, type=${inputTensor.dataType()}, size=${inputSize}x${inputSize}")

        // 2. Output Tensor Configuration [1, 10] FLOAT32
        val outputTensor = currentInterpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        numOutputClasses = if (outputShape.size >= 2) outputShape[1] else outputShape[0]
        outputBuffer = Array(1) { FloatArray(numOutputClasses) }
        Log.i(TAG, "[FOOD_RECOGNIZER] Output tensor shape=${outputShape.contentToString()}, type=${outputTensor.dataType()}, classes=$numOutputClasses")
    }

    override suspend fun recognizeFood(imageData: ByteArray?): List<RecognizedFoodItem> = withContext(Dispatchers.Default) {
        emptyList()
    }

    override suspend fun recognizeFood(bitmap: Bitmap): List<RecognizedFoodItem> = withContext(Dispatchers.Default) {
        if (!inferenceLock.tryLock()) {
            Log.d(TAG, "[FOOD_SCAN] Skipping frame while previous inference is active")
            return@withContext emptyList()
        }
        try {
            recognizeFoodLocked(bitmap, scanGeneration.get())
        } finally {
            inferenceLock.unlock()
        }
    }

    private fun recognizeFoodLocked(bitmap: Bitmap, expectedGeneration: Long): List<RecognizedFoodItem> {
        val currentInterpreter = interpreter
        if (currentInterpreter == null) {
            Log.e(TAG, "[FOOD_TFLITE] Interpreter is null. Model failed to load.")
            return emptyList()
        }

        val startTimeMs = SystemClock.uptimeMillis()
        val trackerGeneration = stabilityTracker.currentGeneration()
        val preprocessedBitmap = prepareBitmap(bitmap)

        synchronized(inputBuffer) {
            preprocessedBitmap.getPixels(
                intPixelValues, 0, inputSize, 0, 0, inputSize, inputSize
            )

            inputBuffer.rewind()
            for (pixel in intPixelValues) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Verified TFLite Contract: RGB FLOAT32 values in 0..255 range (NO normalization)
                inputBuffer.putFloat(r.toFloat())
                inputBuffer.putFloat(g.toFloat())
                inputBuffer.putFloat(b.toFloat())
            }

            outputBuffer[0].fill(0f)
            currentInterpreter.run(inputBuffer, outputBuffer)
        }

        val inferenceTimeMs = SystemClock.uptimeMillis() - startTimeMs
        val probabilities = outputBuffer[0]
        val normalizedProbs = applySoftmaxIfNeeded(probabilities)

        val topK = normalizedProbs.indices
            .map { idx -> idx to normalizedProbs[idx] }
            .sortedByDescending { it.second }
            .take(3)

        val (bestIdx, bestConfidence) = topK.firstOrNull() ?: (0 to 0f)
        val secondConfidence = topK.getOrNull(1)?.second ?: 0f
        val bestRawLabel = labels.getOrElse(bestIdx) { "unknown" }
        val bestDisplayName = formatFoodLabel(bestRawLabel)

        val topKSummary = topK.joinToString { (index, confidence) ->
            "${labels.getOrElse(index) { "unknown" }}=${"%.4f".format(confidence)}"
        }
        Log.i(TAG, "[FOOD_RECOGNIZER] Prediction: $bestDisplayName, top3=[$topKSummary], margin=${"%.4f".format(bestConfidence - secondConfidence)}, inferenceMs=$inferenceTimeMs")

        val gateResult = gate.evaluate(
            bitmap = preprocessedBitmap,
            topClassIndex = bestIdx,
            topConfidence = bestConfidence,
            secondConfidence = secondConfidence
        )
        Log.i(TAG, "[FOOD_GATE] decision=${gateResult.decision}, confidence=${gateResult.confidence}, margin=${gateResult.margin}, explanation=${gateResult.explanation}")

        if (expectedGeneration != scanGeneration.get()) {
            Log.d(TAG, "[FOOD_SCAN] Discarding inference from stale generation=$expectedGeneration")
            return emptyList()
        }

        val stability = stabilityTracker.addPrediction(
            FramePrediction(
                dishId = bestRawLabel,
                dishName = bestDisplayName,
                confidence = bestConfidence,
                gateDecision = gateResult.decision
            ),
            expectedGeneration = trackerGeneration
        )
        Log.i(TAG, "[FOOD_TRACKER] state=${stability.resolvedState}, dominant=${stability.dominantDishName}, confidence=${stability.dominantConfidence}, stable=${stability.isStable}")

        val thumbnail = Bitmap.createScaledBitmap(preprocessedBitmap, 120, 120, true)
        val targetDishId = stability.dominantDishId ?: bestRawLabel
        val targetDishName = stability.dominantDishName ?: bestDisplayName
        val targetConfidence = if (stability.dominantConfidence > 0) stability.dominantConfidence else bestConfidence

        val resultItem = when (stability.resolvedState) {
            FoodResultState.FOOD_DETECTED -> {
                RecognizedFoodItem(
                    foodId = targetDishId,
                    name = targetDishName,
                    confidence = targetConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 250.0,
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
                    foodId = targetDishId,
                    name = targetDishName,
                    confidence = targetConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 0.0,
                    state = FoodResultState.LOW_CONFIDENCE,
                    stateMessage = "Hold steady and point the camera at a food item.",
                    thumbnail = thumbnail
                )
            }
            FoodResultState.SCANNING -> {
                RecognizedFoodItem(
                    foodId = targetDishId,
                    name = targetDishName,
                    confidence = targetConfidence,
                    boundingBox = BoundingBox(0.15f, 0.2f, 0.85f, 0.8f),
                    estimatedAreaPortionGrams = 0.0,
                    state = FoodResultState.SCANNING,
                    stateMessage = "Hold steady for best results",
                    thumbnail = thumbnail
                )
            }
        }

        return listOf(resultItem)
    }

    private fun applySoftmaxIfNeeded(probabilities: FloatArray): FloatArray {
        val sum = probabilities.sum()
        if (sum in 0.95f..1.05f && probabilities.all { it >= 0f }) {
            return probabilities
        }

        val maxVal = probabilities.max()
        val expValues = FloatArray(probabilities.size) { i ->
            kotlin.math.exp((probabilities[i] - maxVal).toDouble()).toFloat()
        }
        val expSum = expValues.sum()
        return FloatArray(probabilities.size) { i ->
            expValues[i] / expSum
        }
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

        return if (cropped.width != inputSize || cropped.height != inputSize) {
            Bitmap.createScaledBitmap(cropped, inputSize, inputSize, true)
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
        } catch (e: Exception) {
            Log.e(TAG, "[FOOD_TFLITE] Error closing interpreter: ${e.message}", e)
        }
    }
}
