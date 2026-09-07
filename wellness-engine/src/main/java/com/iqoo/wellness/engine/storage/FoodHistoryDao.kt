package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class DailyNutritionSummary(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double,
    val mealCount: Int
)

@Dao
interface FoodHistoryDao {
    @Insert
    suspend fun insertFoodHistory(entry: FoodHistoryEntity): Long

    @Query("SELECT * FROM food_history WHERE food_id = :foodId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryForFood(foodId: String, limit: Int = 10): List<FoodHistoryEntity>

    @Query("SELECT * FROM food_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllRecentHistory(limit: Int = 50): List<FoodHistoryEntity>

    @Query("""
        SELECT 
            COALESCE(SUM(calories), 0.0) AS totalCalories,
            COALESCE(SUM(protein), 0.0) AS totalProtein,
            COALESCE(SUM(carbs), 0.0) AS totalCarbs,
            COALESCE(SUM(fat), 0.0) AS totalFat,
            COALESCE(SUM(fiber), 0.0) AS totalFiber,
            COUNT(*) AS mealCount
        FROM food_history 
        WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND is_user_confirmed = 1
    """)
    suspend fun getDailyNutritionSummary(startOfDay: Long, endOfDay: Long): DailyNutritionSummary

    @Query("SELECT * FROM food_history WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay AND is_user_confirmed = 1 ORDER BY timestamp DESC")
    suspend fun getTodayConfirmedMeals(startOfDay: Long, endOfDay: Long): List<FoodHistoryEntity>

    @Query("DELETE FROM food_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryOlderThan(cutoffTimestamp: Long): Int
}
