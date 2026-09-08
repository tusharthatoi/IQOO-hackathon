package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_sessions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun getForDay(start: Long, end: Long): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE timestamp >= :cutoff ORDER BY timestamp DESC")
    suspend fun getSince(cutoff: Long): List<WorkoutSessionEntity>

    @Query("DELETE FROM workout_sessions WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
