package com.iqoo.wellness.engine.activity

data class ActivitySummary(
    val steps: Int,
    val caloriesBurned: Double,
    val estimatedDistanceMeters: Double,
    val averageSpeedKmh: Double,
    val activeDurationMinutes: Int,
    val retentionPolicyDays: Int = 21,
    val isEstimated: Boolean = true
)
