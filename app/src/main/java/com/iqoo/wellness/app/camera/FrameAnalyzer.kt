package com.iqoo.wellness.app.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Listener for analyzed camera frames.
 */
fun interface FrameListener {
    fun onFrameAvailable(
        bitmap: Bitmap?,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        planes: Array<ByteBuffer>
    )
}

/**
 * Backpressure-safe CameraX frame analyzer.
 * Uses STRATEGY_KEEP_ONLY_LATEST and frame skipping to control AI analysis FPS (5-10 FPS),
 * preventing thermal buildup on mobile devices.
 */
class FrameAnalyzer(
    private val targetFps: Int = 10,
    private val listener: FrameListener
) : ImageAnalysis.Analyzer {

    private val minFrameIntervalMs = 1000L / targetFps
    private var lastAnalyzedTimestampMs = 0L
    private var frameId = 0L

    override fun analyze(image: ImageProxy) {
        val currentTimestampMs = System.currentTimeMillis()
        if (currentTimestampMs - lastAnalyzedTimestampMs < minFrameIntervalMs) {
            // Drop frame to maintain controlled inference FPS and preserve thermals
            image.close()
            return
        }

        if (listener is com.iqoo.wellness.app.WellnessManager && !listener.canAcceptFrame()) {
            image.close()
            return
        }

        try {
            lastAnalyzedTimestampMs = currentTimestampMs
            frameId += 1
            val rotationDegrees = image.imageInfo.rotationDegrees
            android.util.Log.d("CAMERA_FRAME", "frameId=$frameId timestamp=$currentTimestampMs width=${image.width} height=${image.height} rotation=$rotationDegrees")
            android.util.Log.d("POSE_ANALYZER", "frameId=$frameId analysisStarted=true")
            val planes = Array(image.planes.size) { i -> image.planes[i].buffer }

            val rawBitmap = image.toBitmap()
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            listener.onFrameAvailable(
                bitmap = orientedBitmap,
                width = image.width,
                height = image.height,
                rotationDegrees = rotationDegrees,
                planes = planes
            )
        } catch (e: Exception) {
            android.util.Log.e("IQOO_WELLNESS", "[CAMERA] Error analyzing frame: ${e.message}", e)
        } finally {
            image.close()
        }
    }
}
