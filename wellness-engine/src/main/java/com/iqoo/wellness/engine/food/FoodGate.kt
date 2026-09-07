package com.iqoo.wellness.engine.food

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

enum class GateDecision {
    FOOD,
    NOT_FOOD,
    LOW_CONFIDENCE
}

data class GateResult(
    val decision: GateDecision,
    val confidence: Float,
    val margin: Float,
    val explanation: String,
    val isTrainedBinaryModel: Boolean = false
)

interface FoodGate {
    fun evaluate(
        bitmap: Bitmap,
        topClassIndex: Int,
        topConfidence: Float,
        secondConfidence: Float
    ): GateResult

    fun reset() = Unit
}

class HeuristicFoodGate(
    private val context: Context? = null,
    private val foodConfidenceThreshold: Float = 0.35f,
    private val lowConfidenceThreshold: Float = 0.08f,
    private val minMarginThreshold: Float = 0.08f
) : FoodGate {

    companion object {
        private const val TAG = "IQOO_WELLNESS"
    }

    override fun evaluate(
        bitmap: Bitmap,
        topClassIndex: Int,
        topConfidence: Float,
        secondConfidence: Float
    ): GateResult {
        Log.d(TAG, "[FOOD_GATE] HEURISTIC GATE (TopConf=${(topConfidence * 100).toInt()}%, SecConf=${(secondConfidence * 100).toInt()}%)")

        val margin = (topConfidence - secondConfidence).coerceAtLeast(0f)

        if (topConfidence < lowConfidenceThreshold) {
            return GateResult(
                decision = GateDecision.LOW_CONFIDENCE,
                confidence = topConfidence,
                margin = margin,
                explanation = "Hold steady and point the camera at a food item.",
                isTrainedBinaryModel = false
            )
        }

        if (topConfidence < foodConfidenceThreshold || margin < minMarginThreshold) {
            return GateResult(
                decision = GateDecision.NOT_FOOD,
                confidence = topConfidence,
                margin = margin,
                explanation = "This doesn't look like a food item. Try scanning a dish or meal.",
                isTrainedBinaryModel = false
            )
        }

        return GateResult(
            decision = GateDecision.FOOD,
            confidence = topConfidence,
            margin = margin,
            explanation = "Food detected with high confidence.",
            isTrainedBinaryModel = false
        )
    }

    override fun reset() {
        Log.d(TAG, "[FOOD_GATE] Reset (heuristic gate has no retained state)")
    }
}
