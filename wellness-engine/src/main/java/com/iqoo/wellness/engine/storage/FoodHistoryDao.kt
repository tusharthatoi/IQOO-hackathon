package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FoodHistoryDao {
    @Insert
    suspend fun insertFoodHistory(entry: FoodHistoryEntity): Long

    @Query("SELECT * FROM food_history WHERE food_id = :foodId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryForFood(foodId: String, limit: Int = 10): List<FoodHistoryEntity>

    @Query("SELECT * FROM food_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllRecentHistory(limit: Int = 50): List<FoodHistoryEntity>

    @Query("DELETE FROM food_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryOlderThan(cutoffTimestamp: Long): Int
}
