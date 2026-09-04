package com.iqoo.wellness.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.activity.StepSensorTracker
import com.iqoo.wellness.engine.storage.DailyActivityEntity
import com.iqoo.wellness.engine.storage.WellnessDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityRetentionTest {

    private lateinit var database: WellnessDatabase
    private lateinit var tracker: StepSensorTracker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WellnessDatabase.createInMemory(context)
        tracker = StepSensorTracker(context, database.dailyActivityDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testStrict21DayRetentionPurgePolicy() = runBlocking {
        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60 * 60 * 1000L

        // Insert historical activity records across various days:
        // Day 0 (today)
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = (now / oneDayMs), steps = 8000, caloriesBurned = 360.0, timestamp = now)
        )
        // Day 5 ago
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = ((now - 5 * oneDayMs) / oneDayMs), steps = 6500, caloriesBurned = 292.5, timestamp = now - 5 * oneDayMs)
        )
        // Day 15 ago
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = ((now - 15 * oneDayMs) / oneDayMs), steps = 9200, caloriesBurned = 414.0, timestamp = now - 15 * oneDayMs)
        )
        // Day 20 ago (Within 21-day retention window -> Should be kept)
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = ((now - 20 * oneDayMs) / oneDayMs), steps = 7100, caloriesBurned = 319.5, timestamp = now - 20 * oneDayMs)
        )
        // Day 22 ago (Violates 21-day retention window -> Must be purged)
        val day22Epoch = (now - 22 * oneDayMs) / oneDayMs
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = day22Epoch, steps = 5000, caloriesBurned = 225.0, timestamp = now - 22 * oneDayMs)
        )
        // Day 45 ago (Violates 21-day retention window -> Must be purged)
        val day45Epoch = (now - 45 * oneDayMs) / oneDayMs
        database.dailyActivityDao().insertOrUpdateActivity(
            DailyActivityEntity(epochDay = day45Epoch, steps = 4000, caloriesBurned = 180.0, timestamp = now - 45 * oneDayMs)
        )

        // Verify total 6 records inserted initially
        assertEquals(6, database.dailyActivityDao().getAllActivity().size)

        // Execute 21-day retention purge
        val deletedCount = tracker.enforceRetentionPolicy()
        assertEquals(2, deletedCount) // Exactly 2 records older than 21 days purged

        // Verify that only the 4 valid records remain in database
        val remaining = database.dailyActivityDao().getAllActivity()
        assertEquals(4, remaining.size)

        // Verify specific purged days no longer exist
        assertNull("Day 22 must be purged", database.dailyActivityDao().getActivityForDay(day22Epoch))
        assertNull("Day 45 must be purged", database.dailyActivityDao().getActivityForDay(day45Epoch))

        // Verify Day 20 and today are preserved
        assertNotNull("Day 20 must be preserved", database.dailyActivityDao().getActivityForDay((now - 20 * oneDayMs) / oneDayMs))
    }

    @Test
    fun testCalorieBurnAndTemporaryMetrics() {
        tracker.recordManualSteps(5000)
        val summary = tracker.getActivitySummary()

        assertEquals(5000, summary.steps)
        assertEquals(225.0, summary.caloriesBurned, 0.5) // 5000 * 0.045 kcal/step
        assertEquals(3810.0, summary.estimatedDistanceMeters, 1.0) // 5000 * 0.762m
        assertEquals(50, summary.activeDurationMinutes) // 5000 / 100 steps/min
        assertEquals(21, summary.retentionPolicyDays)
    }
}
