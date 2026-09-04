package com.iqoo.wellness.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.iqoo.wellness.engine.activity.ActivitySummary
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.posture.BodyLandmark
import com.iqoo.wellness.engine.posture.LandmarkIndices
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.scene.SceneType

/**
 * High-performance HUD overlay rendered directly on the camera preview.
 * Visualizes food bounding boxes, nutrition cards, skeleton wireframes,
 * joint angles, rep counters, and physical activity stats.
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var activeMode: SceneType = SceneType.NORMAL
    private var foodResult: PersonalizedNutritionResult? = null
    private var postureFeedback: PostureFeedback? = null
    private var activitySummary: ActivitySummary? = null

    // Paints
    private val boundingBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Neon Cyan
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val skeletonGoodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Neon Green
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val skeletonWarningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9100") // Neon Amber
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val jointPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val cardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9121824") // Deep Glassmorphism Dark
        style = Paint.Style.FILL
    }

    private val textHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }

    private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textSize = 32f
    }

    private val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 36f
        isFakeBoldText = true
    }

    fun setMode(mode: SceneType) {
        this.activeMode = mode
        postInvalidate()
    }

    fun updateFoodResult(result: PersonalizedNutritionResult?) {
        this.foodResult = result
        postInvalidate()
    }

    fun updatePostureFeedback(feedback: PostureFeedback?) {
        this.postureFeedback = feedback
        postInvalidate()
    }

    fun updateActivitySummary(summary: ActivitySummary?) {
        this.activitySummary = summary
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (activeMode) {
            SceneType.FOOD -> drawFoodOverlay(canvas)
            SceneType.EXERCISE -> drawPostureOverlay(canvas)
            SceneType.NORMAL -> drawAutoCascadedOverlay(canvas)
        }
    }

    private fun drawFoodOverlay(canvas: Canvas) {
        val result = foodResult ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Food Bounding Box
        val box = result.foodItem.boundingBox
        if (box != null) {
            val rect = RectF(box.left * w, box.top * h, box.right * w, box.bottom * h)
            canvas.drawRoundRect(rect, 24f, 24f, boundingBoxPaint)
        }

        // 2. Floating Card with Personalized Nutrition
        val cardLeft = 40f
        val cardTop = h - 420f
        val cardRight = w - 40f
        val cardBottom = h - 80f
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, 32f, 32f, cardBackgroundPaint)

        // Card Header
        canvas.drawText(result.displayHeading, cardLeft + 36f, cardTop + 65f, textHeaderPaint)

        // Subtext / Explanation (Personalized Context)
        canvas.drawText(result.displaySubtext, cardLeft + 36f, cardTop + 120f, textSubPaint)

        // Macronutrients Row
        val n = result.nutrition
        val macroText = "${n.calories.toInt()} kcal  •  ${n.protein}g Protein  •  ${n.carbohydrates}g Carbs  •  ${n.fat}g Fat"
        canvas.drawText(macroText, cardLeft + 36f, cardTop + 190f, accentTextPaint)

        val confirmHint = "Tap to confirm portion or adjust preparation details"
        canvas.drawText(confirmHint, cardLeft + 36f, cardTop + 260f, textSubPaint)
    }

    private fun drawPostureOverlay(canvas: Canvas) {
        val feedback = postureFeedback ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val landmarks = feedback.landmarks

        if (landmarks.size >= 17) {
            val paint = if (feedback.isFormCorrect) skeletonGoodPaint else skeletonWarningPaint

            // Draw skeleton connections
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_SHOULDER, LandmarkIndices.RIGHT_SHOULDER, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_SHOULDER, LandmarkIndices.LEFT_ELBOW, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_ELBOW, LandmarkIndices.LEFT_WRIST, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.RIGHT_SHOULDER, LandmarkIndices.RIGHT_ELBOW, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.RIGHT_ELBOW, LandmarkIndices.RIGHT_WRIST, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_SHOULDER, LandmarkIndices.LEFT_HIP, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.RIGHT_SHOULDER, LandmarkIndices.RIGHT_HIP, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_HIP, LandmarkIndices.RIGHT_HIP, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_HIP, LandmarkIndices.LEFT_KNEE, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.LEFT_KNEE, LandmarkIndices.LEFT_ANKLE, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.RIGHT_HIP, LandmarkIndices.RIGHT_KNEE, paint, w, h)
            drawBone(canvas, landmarks, LandmarkIndices.RIGHT_KNEE, LandmarkIndices.RIGHT_ANKLE, paint, w, h)

            // Draw joint dots
            for (lm in landmarks) {
                canvas.drawCircle(lm.x * w, lm.y * h, 10f, jointPointPaint)
            }
        }

        // Floating Rep & Form Card
        val cardRect = RectF(40f, 120f, w - 40f, 320f)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardBackgroundPaint)

        val repText = "${feedback.exerciseType.displayName}: Reps: ${feedback.repCount}  |  Angle: ${feedback.primaryAngleDegrees.toInt()}°"
        canvas.drawText(repText, 70f, 185f, textHeaderPaint)
        canvas.drawText(feedback.feedbackMessage, 70f, 255f, if (feedback.isFormCorrect) accentTextPaint else skeletonWarningPaint)
    }

    private fun drawAutoCascadedOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Show Activity HUD when in Auto Cascaded
        val activity = activitySummary
        val cardRect = RectF(40f, h - 300f, w - 40f, h - 100f)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardBackgroundPaint)

        val steps = activity?.steps ?: 0
        val cal = activity?.caloriesBurned ?: 0.0
        canvas.drawText("Smart Camera Intelligence", 70f, h - 235f, textHeaderPaint)
        canvas.drawText("Steps: $steps  •  Burned: ${cal.toInt()} kcal (21-Day Local Storage)", 70f, h - 165f, accentTextPaint)
    }

    private fun drawBone(canvas: Canvas, list: List<BodyLandmark>, idx1: Int, idx2: Int, paint: Paint, w: Float, h: Float) {
        val p1 = list[idx1]
        val p2 = list[idx2]
        canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, paint)
    }
}
