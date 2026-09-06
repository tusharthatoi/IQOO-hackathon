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

/**
 * Temporal Stability Tracker across consecutive camera frames.
 *
 * Prevents erratic frame-to-frame flickering and serves as an important UX safeguard.
 * Note: Stability alone does NOT guarantee an object is food; both stability AND high confidence
 * and positive gate evaluation are strictly required to confirm a food result.
 */
class TemporalStabilityTracker(
    private val windowSize: Int = 4,
    private val windowTtlMs: Long = 1800L,
    private val minConsensusRatio: Float = 0.70f,
    private val minConfirmationConfidence: Float = 0.70f
) {

    companion object {
        private const val TAG = "IQOO_WELLNESS"
    }

    private val history = mutableListOf<FramePrediction>()

    @Synchronized
    fun addPrediction(prediction: FramePrediction): StabilityEvaluation {
        val now = SystemClock.uptimeMillis()
        history.add(prediction)

        // Expire older frames outside the rolling time window
        history.removeAll { now - it.timestampMs > windowTtlMs }
        while (history.size > windowSize) {
            history.removeAt(0)
        }

        // 1. If buffer has too few frames, we are still scanning
        if (history.size < 2) {
            Log.d(TAG, "[FOOD_STABILITY] Warming up window (${history.size}/$windowSize frames) -> State: SCANNING")
            return StabilityEvaluation(
                isStable = false,
                dominantDishId = prediction.dishId,
                dominantDishName = prediction.dishName,
                dominantConfidence = prediction.confidence,
                resolvedState = FoodResultState.SCANNING,
                summary = "Scanning food..."
            )
        }

        // Count occurrences per dishId
        val counts = mutableMapOf<String, Int>()
        val confidences = mutableMapOf<String, MutableList<Float>>()
        val gateDecisions = mutableMapOf<String, MutableList<GateDecision>>()

        for (item in history) {
            counts[item.dishId] = (counts[item.dishId] ?: 0) + 1
            confidences.getOrPut(item.dishId) { mutableListOf() }.add(item.confidence)
            gateDecisions.getOrPut(item.dishId) { mutableListOf() }.add(item.gateDecision)
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
        val isGatePositive = gateDecisions[dominantId]?.all { it == GateDecision.FOOD } == true
        val isStable = isConsistent && hasAdequateConfidence && isGatePositive

        Log.d(TAG, "[FOOD_STABILITY] ${if (isStable) "Stable" else "Unstable"} (Dominant: $dominantId $dominantCount/${history.size}, Consensus=${(consensusRatio * 100).toInt()}%, AvgConf=${(avgDominantConf * 100).toInt()}%, GatePositive=$isGatePositive)")

        // Decision logic: Temporal stability must NOT convert a consistently wrong prediction into FOOD.
        // Requires sufficient confidence, stable consensus, and positive gate evaluation.
        val resolvedState: FoodResultState
        val summary: String

        if (prediction.gateDecision == GateDecision.LOW_CONFIDENCE || avgDominantConf < 0.40f) {
            resolvedState = FoodResultState.LOW_CONFIDENCE
            summary = "Hold steady and point the camera at a food item."
        } else if (prediction.gateDecision == GateDecision.NOT_FOOD || !isGatePositive || !hasAdequateConfidence) {
            resolvedState = FoodResultState.NOT_FOOD
            summary = "This doesn't look like a food item. Try scanning a dish or meal."
        } else if (isStable) {
            resolvedState = FoodResultState.FOOD_DETECTED
            summary = "Food confirmed stable across frames."
        } else {
            // Fluctuating between multiple food classes -> keep in scanning
            resolvedState = FoodResultState.SCANNING
            summary = "Scanning food..."
        }

        val resultTag = when (resolvedState) {
            FoodResultState.FOOD_DETECTED -> "FOOD"
            FoodResultState.NOT_FOOD -> "NOT_FOOD"
            FoodResultState.LOW_CONFIDENCE -> "LOW_CONFIDENCE"
            FoodResultState.SCANNING -> "SCANNING"
        }
        Log.i(TAG, "[FOOD_RESULT] $resultTag (Prediction: ${latestDominantItem.dishName}, Conf=${(avgDominantConf * 100).toInt()}%)")

        return StabilityEvaluation(
            isStable = isConsistent && hasAdequateConfidence,
            dominantDishId = dominantId,
            dominantDishName = latestDominantItem.dishName,
            dominantConfidence = avgDominantConf,
            resolvedState = resolvedState,
            summary = summary
        )
    }

    @Synchronized
    fun reset() {
        history.clear()
    }
}
