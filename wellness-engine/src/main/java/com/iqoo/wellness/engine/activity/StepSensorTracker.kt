package com.iqoo.wellness.engine.activity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.iqoo.wellness.engine.storage.DailyActivityDao
import com.iqoo.wellness.engine.storage.DailyActivityEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hardware step sensor tracker with strict 21-day local SQLite retention policy.
 * Persists only steps and estimated calories burned.
 * Temporary distance, speed and duration are dynamically computed and discarded.
 */
class StepSensorTracker(
    private val context: Context,
    private val dailyActivityDao: DailyActivityDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialStepCount = -1
    private var currentSteps = 0

    init {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        } catch (_: Exception) {
            // Graceful fallback if sensor service is unavailable
        }
    }

    fun startTracking() {
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            if (initialStepCount < 0) {
                initialStepCount = totalSteps
            }
            currentSteps = totalSteps - initialStepCount
            persistCurrentSteps(currentSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun recordManualSteps(steps: Int) {
        this.currentSteps += steps
        persistCurrentSteps(this.currentSteps)
    }

    fun getActivitySummary(): ActivitySummary {
        val estimatedCalories = calculateCalories(currentSteps)
        val estimatedDistanceMeters = currentSteps * 0.762 // 0.762m average stride
        val averageSpeedKmh = 4.8 // Standard average walking speed
        val activeDurationMinutes = (currentSteps / 100).coerceAtLeast(0) // ~100 steps/min cadence

        return ActivitySummary(
            steps = currentSteps,
            caloriesBurned = Math.round(estimatedCalories * 10.0) / 10.0,
            estimatedDistanceMeters = Math.round(estimatedDistanceMeters * 10.0) / 10.0,
            averageSpeedKmh = averageSpeedKmh,
            activeDurationMinutes = activeDurationMinutes,
            retentionPolicyDays = 21,
            isEstimated = true
        )
    }

    private fun calculateCalories(steps: Int): Double {
        // Standard MET calculation: ~0.045 kcal per walking step for a 70kg individual
        return steps * 0.045
    }

    private fun persistCurrentSteps(steps: Int) {
        val epochDay = System.currentTimeMillis() / (1000L * 60 * 60 * 24)
        val calories = calculateCalories(steps)
        coroutineScope.launch {
            dailyActivityDao.insertOrUpdateActivity(
                DailyActivityEntity(
                    epochDay = epochDay,
                    steps = steps,
                    caloriesBurned = Math.round(calories * 10.0) / 10.0,
                    timestamp = System.currentTimeMillis()
                )
            )
            // Enforce retention policy
            enforceRetentionPolicy()
        }
    }

    suspend fun enforceRetentionPolicy(): Int {
        val cutoffTimestamp = System.currentTimeMillis() - (21L * 24 * 60 * 60 * 1000L)
        return dailyActivityDao.purgeRecordsOlderThan(cutoffTimestamp)
    }
}
