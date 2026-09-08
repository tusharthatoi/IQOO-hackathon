package com.iqoo.wellness.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.storage.WellnessDatabase
import com.iqoo.wellness.engine.storage.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSessionDaoTest {
    private lateinit var database: WellnessDatabase

    @Before
    fun setUp() {
        database = WellnessDatabase.createInMemory(
            ApplicationProvider.getApplicationContext<Context>()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesSessionsAndReturnsFiveDayHistoryWindow() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        database.workoutSessionDao().insert(
            WorkoutSessionEntity(exerciseType = "ARM_RAISE", reps = 5, durationSeconds = 90, formScore = 0.92f, exerciseConfidence = 0.91f, timestamp = now)
        )
        database.workoutSessionDao().insert(
            WorkoutSessionEntity(exerciseType = "SQUAT", reps = 20, durationSeconds = 120, formScore = 0.88f, exerciseConfidence = 0.86f, timestamp = now - 5 * day)
        )
        database.workoutSessionDao().insert(
            WorkoutSessionEntity(exerciseType = "PUSH_UPS", reps = 10, durationSeconds = 60, formScore = null, exerciseConfidence = null, timestamp = now - 7 * day)
        )

        val history = database.workoutSessionDao().getSince(now - 6 * day)
        assertEquals(2, history.size)
        assertEquals("ARM_RAISE", history.first().exerciseType)
    }
}
