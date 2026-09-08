package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["timestamp"]), Index(value = ["exercise_type"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_type") val exerciseType: String,
    val reps: Int,
    val durationSeconds: Long,
    val formScore: Float?,
    val exerciseConfidence: Float?,
    val timestamp: Long = System.currentTimeMillis()
)
