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

/**
 * Food / Non-Food Gate abstraction.
 *
 * NOTE: The current production pipeline uses this gate interface in HEURISTIC mode
 * because real non-food negative dataset/weights are not yet trained.
 *
 * Future integration: When a dedicated binary Food-vs-NonFood model (`food_gate.tflite`)
 * is trained with hard negatives (towels, furniture, electronics, empty plates, etc.),
 * it will drop into this interface directly without altering downstream architecture.
 */
interface FoodGate {
    fun evaluate(
        bitmap: Bitmap,
        topClassIndex: Int,
        topConfidence: Float,
        secondConfidence: Float
    ): GateResult
}

/**
 * Current heuristic gate implementation until real negative training data is provided.
 * Does NOT claim to be a trained binary ML classifier.
 */
class HeuristicFoodGate(
    private val context: Context? = null,
    private val foodConfidenceThreshold: Float = 0.70f,
    private val lowConfidenceThreshold: Float = 0.40f,
    private val minMarginThreshold: Float = 0.15f
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
        // Explicit log as mandated
        Log.d(TAG, "[FOOD_GATE] HEURISTIC GATE — NOT TRAINED (TopConf=${(topConfidence * 100).toInt()}%, SecConf=${(secondConfidence * 100).toInt()}%)")

        val margin = (topConfidence - secondConfidence).coerceAtLeast(0f)

        // 1. If confidence is very low, it's definitely uncertain
        if (topConfidence < lowConfidenceThreshold) {
            return GateResult(
                decision = GateDecision.LOW_CONFIDENCE,
                confidence = topConfidence,
                margin = margin,
                explanation = "Hold steady and point the camera at a food item.",
                isTrainedBinaryModel = false
            )
        }

        // 2. Closed-set classifier pitfall check:
        // When non-food items (like fabric, blank surfaces) are evaluated, closed-world models
        // often distribute probabilities across multiple visually ambiguous classes with narrow margin,
        // or land in an intermediate confidence range (e.g. 0.40 - 0.70).
        if (topConfidence < foodConfidenceThreshold || margin < minMarginThreshold) {
            return GateResult(
                decision = GateDecision.NOT_FOOD,
                confidence = topConfidence,
                margin = margin,
                explanation = "This doesn't look like a food item. Try scanning a dish or meal.",
                isTrainedBinaryModel = false
            )
        }

        // 3. Candidate passing initial heuristic thresholds (still subject to temporal stability)
        return GateResult(
            decision = GateDecision.FOOD,
            confidence = topConfidence,
            margin = margin,
            explanation = "Food detected with high confidence.",
            isTrainedBinaryModel = false
        )
    }
}
