package com.iqoo.wellness.engine.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodEntity::class,
        PortionHistoryEntity::class,
        FoodHistoryEntity::class,
        FoodPreparationContextEntity::class,
        DailyActivityEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WellnessDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun portionHistoryDao(): PortionHistoryDao
    abstract fun foodHistoryDao(): FoodHistoryDao
    abstract fun foodPreparationContextDao(): FoodPreparationContextDao
    abstract fun dailyActivityDao(): DailyActivityDao

    companion object {
        @Volatile
        private var INSTANCE: WellnessDatabase? = null

        fun getInstance(context: Context): WellnessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WellnessDatabase::class.java,
                    "iqoo_wellness.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): WellnessDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                WellnessDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
