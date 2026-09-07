package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PortionHistoryDao {
    @Insert
    suspend fun insertPortion(portion: PortionHistoryEntity): Long

    @Query("SELECT * FROM portion_history WHERE food_id = :foodId OR LOWER(food_name) = LOWER(:foodName) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPortionsForFood(foodId: String, limit: Int = 5, foodName: String = foodId): List<PortionHistoryEntity>

    @Query("SELECT AVG(portion_grams) FROM (SELECT portion_grams FROM portion_history WHERE food_id = :foodId OR LOWER(food_name) = LOWER(:foodName) ORDER BY timestamp DESC LIMIT 5)")
    suspend fun getAverageRecentPortion(foodId: String, foodName: String = foodId): Double?

    @Query("SELECT COUNT(*) FROM portion_history WHERE food_id = :foodId OR LOWER(food_name) = LOWER(:foodName)")
    suspend fun getPortionCountForFood(foodId: String, foodName: String = foodId): Int
}