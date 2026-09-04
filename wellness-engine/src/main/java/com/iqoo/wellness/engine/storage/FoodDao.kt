package com.iqoo.wellness.engine.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFoods(foods: List<FoodEntity>)

    @Query("SELECT * FROM foods WHERE food_id = :foodId LIMIT 1")
    suspend fun getFoodById(foodId: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getFoodByName(name: String): FoodEntity?

    @Query("SELECT * FROM foods")
    suspend fun getAllFoods(): List<FoodEntity>
}
