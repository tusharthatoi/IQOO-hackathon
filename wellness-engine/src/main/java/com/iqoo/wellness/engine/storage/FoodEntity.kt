package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents offline seed nutrition data for a recognized dish.
 */
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey
    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "serving_unit")
    val servingUnit: String,

    @ColumnInfo(name = "default_grams")
    val defaultGrams: Double,

    @ColumnInfo(name = "calories_per_100g")
    val caloriesPer100g: Double,

    @ColumnInfo(name = "protein_per_100g")
    val proteinPer100g: Double,

    @ColumnInfo(name = "carbs_per_100g")
    val carbsPer100g: Double,

    @ColumnInfo(name = "fat_per_100g")
    val fatPer100g: Double,

    @ColumnInfo(name = "fiber_per_100g")
    val fiberPer100g: Double
)
