package com.iqoo.wellness.engine.posture

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Deterministic geometric joint angle calculator.
 * Computes 2D/3D joint angles using vector dot products and cosine trigonometry.
 */
object JointAngleCalculator {

    /**
     * Calculates the internal angle in degrees at vertex joint B formed by rays B->A and B->C.
     * Returns angle in degrees [0.0, 180.0].
     */
    fun calculateAngle(
        aX: Float, aY: Float,
        bX: Float, bY: Float,
        cX: Float, cY: Float
    ): Double {
        // Vector BA
        val v1X = (aX - bX).toDouble()
        val v1Y = (aY - bY).toDouble()

        // Vector BC
        val v2X = (cX - bX).toDouble()
        val v2Y = (cY - bY).toDouble()

        val dotProduct = (v1X * v2X) + (v1Y * v2Y)
        val mag1 = sqrt((v1X * v1X) + (v1Y * v1Y))
        val mag2 = sqrt((v2X * v2X) + (v2Y * v2Y))

        if (mag1 == 0.0 || mag2 == 0.0) {
            return 180.0 // Collinear default if points coincide
        }

        val cosVal = (dotProduct / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        val radians = acos(cosVal)
        val degrees = Math.toDegrees(radians)

        return Math.round(degrees * 10.0) / 10.0
    }

    fun calculateJointAngle(pointA: BodyLandmark, vertexB: BodyLandmark, pointC: BodyLandmark): Double {
        return calculateAngle(pointA.x, pointA.y, vertexB.x, vertexB.y, pointC.x, pointC.y)
    }
}
