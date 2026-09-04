package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PortionHistoryDao {
    @Insert
    suspend fun insertPortion(portion: PortionHistoryEntity): Long

    @Query("SELECT * FROM portion_history WHERE food_id = :foodId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPortionsForFood(foodId: String, limit: Int = 5): List<PortionHistoryEntity>

    @Query("SELECT AVG(portion_grams) FROM (SELECT portion_grams FROM portion_history WHERE food_id = :foodId ORDER BY timestamp DESC LIMIT 5)")
    suspend fun getAverageRecentPortion(foodId: String): Double?

    @Query("SELECT COUNT(*) FROM portion_history WHERE food_id = :foodId")
    suspend fun getPortionCountForFood(foodId: String): Int
}
