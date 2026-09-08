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
        DailyActivityEntity::class,
        WorkoutSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WellnessDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun portionHistoryDao(): PortionHistoryDao
    abstract fun foodHistoryDao(): FoodHistoryDao
    abstract fun foodPreparationContextDao(): FoodPreparationContextDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile
        private var INSTANCE: WellnessDatabase? = null

        fun getInstance(context: Context): WellnessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WellnessDatabase::class.java,
                    "iqoo_wellness.db"
                ).addMigrations(MIGRATION_2_3).build()
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

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "exercise_type TEXT NOT NULL, reps INTEGER NOT NULL, " +
                        "durationSeconds INTEGER NOT NULL, formScore REAL, " +
                        "exerciseConfidence REAL, timestamp INTEGER NOT NULL)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_timestamp ON workout_sessions(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_exercise_type ON workout_sessions(exercise_type)")
            }
        }
    }
}
