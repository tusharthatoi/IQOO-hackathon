package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks historical logged meals and nutritional estimates.
 */
@Entity(
    tableName = "food_history",
    indices = [Index(value = ["food_id"]), Index(value = ["timestamp"])]
)
data class FoodHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "food_name")
    val foodName: String,

    @ColumnInfo(name = "portion_grams")
    val portionGrams: Double,

    @ColumnInfo(name = "calories")
    val calories: Double,

    @ColumnInfo(name = "protein")
    val protein: Double,

    @ColumnInfo(name = "carbs")
    val carbs: Double,

    @ColumnInfo(name = "fat")
    val fat: Double,

    @ColumnInfo(name = "fiber")
    val fiber: Double,

    @ColumnInfo(name = "is_user_confirmed")
    val isUserConfirmed: Boolean,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
