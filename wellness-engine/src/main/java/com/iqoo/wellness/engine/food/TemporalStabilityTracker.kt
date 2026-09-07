package com.iqoo.wellness.engine.food

import android.os.SystemClock
import android.util.Log

data class FramePrediction(
    val dishId: String,
    val dishName: String,
    val confidence: Float,
    val gateDecision: GateDecision,
    val timestampMs: Long = SystemClock.uptimeMillis()
)

data class StabilityEvaluation(
    val isStable: Boolean,
    val dominantDishId: String?,
    val dominantDishName: String?,
    val dominantConfidence: Float,
    val resolvedState: FoodResultState,
    val summary: String
)

class TemporalStabilityTracker(
    private val windowSize: Int = 2,
    private val windowTtlMs: Long = 2500L,
    private val minConsensusRatio: Float = 0.50f,
    private val minConfirmationConfidence: Float = 0.10f
) {

    companion object {
        private const val TAG = "IQOO_WELLNESS"
    }

    private val history = mutableListOf<FramePrediction>()
    private var generation = 0L

    @Synchronized
    fun addPrediction(prediction: FramePrediction, expectedGeneration: Long = generation): StabilityEvaluation {
        if (expectedGeneration != generation) {
            return StabilityEvaluation(
                isStable = false,
                dominantDishId = null,
                dominantDishName = null,
                dominantConfidence = 0f,
                resolvedState = FoodResultState.SCANNING,
                summary = "Scanning food..."
            )
        }
        val now = SystemClock.uptimeMillis()
        history.add(prediction)

        history.removeAll { now - it.timestampMs > windowTtlMs }
        while (history.size > windowSize) {
            history.removeAt(0)
        }

        if (history.size < 1) {
            return StabilityEvaluation(
                isStable = false,
                dominantDishId = prediction.dishId,
                dominantDishName = prediction.dishName,
                dominantConfidence = prediction.confidence,
                resolvedState = FoodResultState.SCANNING,
                summary = "Scanning food..."
            )
        }

        val counts = mutableMapOf<String, Int>()
        val confidences = mutableMapOf<String, MutableList<Float>>()

        for (item in history) {
            counts[item.dishId] = (counts[item.dishId] ?: 0) + 1
            confidences.getOrPut(item.dishId) { mutableListOf() }.add(item.confidence)
        }

        val mostFrequent = counts.maxByOrNull { it.value }
        val dominantId = mostFrequent?.key ?: prediction.dishId
        val dominantCount = mostFrequent?.value ?: 1
        val consensusRatio = dominantCount.toFloat() / history.size

        val dominantConfList = confidences[dominantId] ?: listOf(prediction.confidence)
        val avgDominantConf = dominantConfList.average().toFloat()
        val latestDominantItem = history.lastOrNull { it.dishId == dominantId } ?: prediction

        val isConsistent = consensusRatio >= minConsensusRatio
        val hasAdequateConfidence = avgDominantConf >= minConfirmationConfidence

        val resolvedState: FoodResultState
        val summary: String

        if (prediction.gateDecision == GateDecision.NOT_FOOD) {
            resolvedState = FoodResultState.NOT_FOOD
            summary = "This doesn't look like a food item."
        } else if (prediction.gateDecision == GateDecision.LOW_CONFIDENCE) {
            resolvedState = FoodResultState.LOW_CONFIDENCE
            summary = "Hold steady and point the camera at a food item."
        } else if (hasAdequateConfidence) {
            resolvedState = FoodResultState.FOOD_DETECTED
            summary = "Food confirmed stable across frames."
        } else {
            resolvedState = FoodResultState.SCANNING
            summary = "Scanning food..."
        }

        Log.i(TAG, "[FOOD_RESULT] State=$resolvedState (Prediction: ${latestDominantItem.dishName}, Conf=${(avgDominantConf * 100).toInt()}%)")

        return StabilityEvaluation(
            isStable = hasAdequateConfidence,
            dominantDishId = dominantId,
            dominantDishName = latestDominantItem.dishName,
            dominantConfidence = avgDominantConf,
            resolvedState = resolvedState,
            summary = summary
        )
    }

    @Synchronized
    fun reset() {
        generation++
        history.clear()
    }

    @Synchronized
    fun currentGeneration(): Long = generation
}
