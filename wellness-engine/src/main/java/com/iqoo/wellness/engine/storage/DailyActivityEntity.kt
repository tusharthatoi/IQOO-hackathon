package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,

    @ColumnInfo(name = "steps")
    val steps: Int,

    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DailyActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateActivity(entry: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE epoch_day = :epochDay LIMIT 1")
    suspend fun getActivityForDay(epochDay: Long): DailyActivityEntity?

    @Query("SELECT * FROM daily_activity ORDER BY epoch_day DESC")
    suspend fun getAllActivity(): List<DailyActivityEntity>

    /**
     * Strictly enforces the PRD 21-day rolling retention policy.
     * Deletes all records with timestamps older than the cutoff.
     */
    @Query("DELETE FROM daily_activity WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeRecordsOlderThan(cutoffTimestamp: Long): Int
}
