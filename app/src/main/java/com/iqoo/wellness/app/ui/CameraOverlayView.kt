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

    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }

    private fun drawFoodOverlay(canvas: Canvas) {
        drawScanningReticle(canvas)
    }

    private fun drawScanningReticle(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Square reticle in center
        val boxSize = minOf(w, h) * 0.72f
        val left = (w - boxSize) / 2f
        val top = (h - boxSize) / 2f + 20f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerLen = 48f
        val cornerRadius = 24f

        // Draw 4 corner brackets
        // Top-left
        canvas.drawLine(left + cornerRadius, top, left + cornerLen, top, reticlePaint)
        canvas.drawLine(left, top + cornerRadius, left, top + cornerLen, reticlePaint)
        canvas.drawArc(RectF(left, top, left + cornerRadius * 2, top + cornerRadius * 2), 180f, 90f, false, reticlePaint)

        // Top-right
        canvas.drawLine(right - cornerLen, top, right - cornerRadius, top, reticlePaint)
        canvas.drawLine(right, top + cornerRadius, right, top + cornerLen, reticlePaint)
        canvas.drawArc(RectF(right - cornerRadius * 2, top, right, top + cornerRadius * 2), 270f, 90f, false, reticlePaint)

        // Bottom-left
        canvas.drawLine(left + cornerRadius, bottom, left + cornerLen, bottom, reticlePaint)
        canvas.drawLine(left, bottom - cornerLen, left, bottom - cornerRadius, reticlePaint)
        canvas.drawArc(RectF(left, bottom - cornerRadius * 2, left + cornerRadius * 2, bottom), 90f, 90f, false, reticlePaint)

        // Bottom-right
        canvas.drawLine(right - cornerLen, bottom, right - cornerRadius, bottom, reticlePaint)
        canvas.drawLine(right, bottom - cornerLen, right, bottom - cornerRadius, reticlePaint)
        canvas.drawArc(RectF(right - cornerRadius * 2, bottom - cornerRadius * 2, right, bottom), 0f, 90f, false, reticlePaint)
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
