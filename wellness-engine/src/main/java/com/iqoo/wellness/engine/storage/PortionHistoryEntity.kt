package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks historical portions confirmed by the user for a given dish.
 */
@Entity(
    tableName = "portion_history",
    indices = [Index(value = ["food_id"]), Index(value = ["food_name"])]
)
data class PortionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "food_name")
    val foodName: String,

    @ColumnInfo(name = "portion_grams")
    val portionGrams: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
