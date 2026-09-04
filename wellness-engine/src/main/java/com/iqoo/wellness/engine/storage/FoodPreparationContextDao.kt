package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface FoodPreparationContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContext(context: FoodPreparationContextEntity): Long

    @Update
    suspend fun updateContext(context: FoodPreparationContextEntity)

    @Query("SELECT * FROM food_preparation_contexts WHERE food_id = :foodId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestContextForFood(foodId: String): FoodPreparationContextEntity?

    @Query("SELECT * FROM food_preparation_contexts WHERE LOWER(food_name) = LOWER(:foodName) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestContextByFoodName(foodName: String): FoodPreparationContextEntity?

    @Query("SELECT * FROM food_preparation_contexts WHERE food_id = :foodId ORDER BY recurrence_count DESC, timestamp DESC LIMIT 1")
    suspend fun getMostFrequentContextForFood(foodId: String): FoodPreparationContextEntity?

    @Query("SELECT COUNT(*) FROM food_preparation_contexts WHERE food_id = :foodId")
    suspend fun getContextCountForFood(foodId: String): Int
}
