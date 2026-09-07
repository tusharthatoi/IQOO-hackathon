package com.iqoo.wellness.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.food.BoundingBox
import com.iqoo.wellness.engine.food.FoodRecognizer
import com.iqoo.wellness.engine.food.FoodSeedDatabase
import com.iqoo.wellness.engine.food.NutritionCalculator
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.storage.FoodEntity
import com.iqoo.wellness.engine.storage.FoodHistoryEntity
import com.iqoo.wellness.engine.storage.WellnessDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NutritionRagPipelineTest {

    private lateinit var database: WellnessDatabase
    private lateinit var wellnessEngine: WellnessEngine
    private lateinit var testRecognizer: MockFoodRecognizer

    private class MockFoodRecognizer(var currentFood: RecognizedFoodItem) : FoodRecognizer {
        override suspend fun recognizeFood(imageData: ByteArray?): List<RecognizedFoodItem> {
            return listOf(currentFood)
        }
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WellnessDatabase.createInMemory(context)
        database.foodDao().insertAllFoods(FoodSeedDatabase.SEED_FOODS)

        val biryani = RecognizedFoodItem(
            foodId = "biryani_01",
            name = "Biryani",
            confidence = 0.95f,
            boundingBox = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            estimatedAreaPortionGrams = 200.0
        )
        testRecognizer = MockFoodRecognizer(biryani)

        wellnessEngine = WellnessEngineImpl.createForTesting(database, testRecognizer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // TEST 1 — Nutrition scaling (100g vs 200g exact 2.0x scaling)
    @Test
    fun testNutritionScaling() = runBlocking {
        val seedFood = database.foodDao().getFoodByName("Biryani")
        assertNotNull("Biryani should exist in seed database", seedFood)

        val profile100 = NutritionCalculator.calculateBaseline(seedFood!!, 100.0)
        val profile200 = NutritionCalculator.calculateBaseline(seedFood, 200.0)

        assertEquals("Calories must scale 2x", profile100.calories * 2.0, profile200.calories, 0.1)
        assertEquals("Protein must scale 2x", profile100.protein * 2.0, profile200.protein, 0.1)
        assertEquals("Carbs must scale 2x", profile100.carbohydrates * 2.0, profile200.carbohydrates, 0.1)
        assertEquals("Fat must scale 2x", profile100.fat * 2.0, profile200.fat, 0.1)
        assertEquals("Fiber must scale 2x", profile100.fiber * 2.0, profile200.fiber, 0.1)
        assertTrue(profile100.isAvailable)
        assertTrue(profile200.isAvailable)
    }

    // TEST 2 — Meal persistence (Confirm meal -> Room contains meal record)
    @Test
    fun testMealPersistence() = runBlocking {
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 250.0
        )

        val meals = database.foodHistoryDao().getAllRecentHistory(10)
        assertEquals("Exactly one meal record must be saved in Room", 1, meals.size)
        val savedMeal = meals[0]
        assertEquals("biryani_01", savedMeal.foodId)
        assertEquals("Biryani", savedMeal.foodName)
        assertEquals(250.0, savedMeal.portionGrams, 0.1)
        assertTrue("Meal record must be flagged as user confirmed", savedMeal.isUserConfirmed)
        assertTrue("Calories must be greater than 0", savedMeal.calories > 0)
    }

    // TEST 3 — Daily aggregation (Meal A + Meal B -> sum of calories, protein, carbs, fat, fiber)
    @Test
    fun testDailyAggregation() = runBlocking {
        val now = System.currentTimeMillis()
        val mealA = FoodHistoryEntity(
            foodId = "meal_a",
            foodName = "Meal A",
            portionGrams = 200.0,
            calories = 400.0,
            protein = 20.0,
            carbs = 50.0,
            fat = 10.0,
            fiber = 4.0,
            isUserConfirmed = true,
            timestamp = now
        )
        val mealB = FoodHistoryEntity(
            foodId = "meal_b",
            foodName = "Meal B",
            portionGrams = 300.0,
            calories = 600.0,
            protein = 30.0,
            carbs = 70.0,
            fat = 20.0,
            fiber = 6.0,
            isUserConfirmed = true,
            timestamp = now
        )

        database.foodHistoryDao().insertFoodHistory(mealA)
        database.foodHistoryDao().insertFoodHistory(mealB)

        val summary = wellnessEngine.getDailyNutritionSummary(now)
        assertEquals("Total calories must sum Meal A + Meal B", 1000.0, summary.totalCalories, 0.1)
        assertEquals("Total protein must sum Meal A + Meal B", 50.0, summary.totalProtein, 0.1)
        assertEquals("Total carbs must sum Meal A + Meal B", 120.0, summary.totalCarbs, 0.1)
        assertEquals("Total fat must sum Meal A + Meal B", 30.0, summary.totalFat, 0.1)
        assertEquals("Total fiber must sum Meal A + Meal B", 10.0, summary.totalFiber, 0.1)
        assertEquals("Meal count must be 2", 2, summary.mealCount)
    }

    // TEST 4 — Personalization retrieval (Biryani portions: 200g, 250g, 225g, 250g -> rolling average)
    @Test
    fun testPersonalizationRetrieval() = runBlocking {
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 200.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 225.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)

        val avg = database.portionHistoryDao().getAverageRecentPortion("biryani_01")
        assertNotNull(avg)
        // (200 + 250 + 225 + 250) / 4 = 231.25
        assertEquals(231.25, avg!!, 0.1)
    }

    // TEST 5 — Personalization suggestion (Future scan retrieves historical portion)
    @Test
    fun testPersonalizationSuggestion() = runBlocking {
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 200.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 225.0)
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertTrue("Subsequent scan must be personalized", result!!.isPersonalized)
        assertTrue("Context hasHistory must be true", result.context.hasHistory)
        assertEquals("Typical portion must be ~231g", 231.25, result.context.typicalPortionGrams, 0.1)
        assertTrue("Subtext must state 'Your usual portion'", result.displaySubtext.contains("Your usual portion", ignoreCase = true))
    }

    // TEST 6 — Suggestion is NOT automatically saved (Retrieve usual portion without confirming)
    @Test
    fun testSuggestionNotAutomaticallySaved() = runBlocking {
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)
        assertEquals(1, database.foodHistoryDao().getAllRecentHistory(10).size)

        // Perform food analysis scan -> suggestions retrieved in memory
        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)

        // Verify no additional meal records were created simply by scanning/retrieving
        val mealsAfterScan = database.foodHistoryDao().getAllRecentHistory(10)
        assertEquals("Scanning/retrieving must NOT create a new meal record", 1, mealsAfterScan.size)
    }

    // TEST 7 — Restart persistence (Save meal -> recreate DB access -> totals remain intact)
    @Test
    fun testRestartPersistence() = runBlocking {
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 250.0)
        val initialSummary = wellnessEngine.getDailyNutritionSummary()
        assertTrue("Initial calories must be > 0", initialSummary.totalCalories > 0)

        // Simulate app restart by creating a new WellnessEngine instance over the same Room database
        val newEngine = WellnessEngineImpl.createForTesting(database, testRecognizer)
        val restoredSummary = newEngine.getDailyNutritionSummary()

        assertEquals(initialSummary.totalCalories, restoredSummary.totalCalories, 0.01)
        assertEquals(initialSummary.totalProtein, restoredSummary.totalProtein, 0.01)
        assertEquals(initialSummary.mealCount, restoredSummary.mealCount)
    }

    // TEST 8 — Change food resets scanning (Rejecting prediction saves nothing)
    @Test
    fun testChangeFoodResetsScanning() = runBlocking {
        val scanResult = wellnessEngine.analyzeFood()
        assertNotNull(scanResult)

        // User rejects food (taps "Change food") -> No confirmFoodPortion called
        val meals = database.foodHistoryDao().getAllRecentHistory(10)
        val portions = database.portionHistoryDao().getRecentPortionsForFood("biryani_01", 10)

        assertEquals("No meal records must be saved on rejected prediction", 0, meals.size)
        assertEquals("No portion history must be saved on rejected prediction", 0, portions.size)
    }

    // TEST 9 — Uncatalogued nutrition (Recognized food not in seed database)
    @Test
    fun testUncataloguedNutrition() = runBlocking {
        val exoticFood = RecognizedFoodItem(
            foodId = "exotic_curry_99",
            name = "Exotic Curry",
            confidence = 0.88f
        )
        testRecognizer.currentFood = exoticFood

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertFalse("Uncatalogued food nutrition must have isAvailable = false", result!!.nutrition.isAvailable)
        assertEquals("Uncatalogued food calories must report 0.0 without fabrication", 0.0, result.nutrition.calories, 0.01)
        assertTrue("Subtext must explain nutrition data unavailable", result.displaySubtext.contains("Nutrition data unavailable", ignoreCase = true))
    }

    // TEST 10 — Date boundary (Yesterday's meal excluded from today's total)
    @Test
    fun testDateBoundary() = runBlocking {
        val now = System.currentTimeMillis()
        val yesterdayTimestamp = now - (25 * 60 * 60 * 1000L) // 25 hours ago

        val yesterdayMeal = FoodHistoryEntity(
            foodId = "yesterday_food",
            foodName = "Yesterday Food",
            portionGrams = 200.0,
            calories = 500.0,
            protein = 20.0,
            carbs = 60.0,
            fat = 15.0,
            fiber = 5.0,
            isUserConfirmed = true,
            timestamp = yesterdayTimestamp
        )
        val todayMeal = FoodHistoryEntity(
            foodId = "today_food",
            foodName = "Today Food",
            portionGrams = 200.0,
            calories = 300.0,
            protein = 15.0,
            carbs = 40.0,
            fat = 10.0,
            fiber = 3.0,
            isUserConfirmed = true,
            timestamp = now
        )

        database.foodHistoryDao().insertFoodHistory(yesterdayMeal)
        database.foodHistoryDao().insertFoodHistory(todayMeal)

        val todaySummary = wellnessEngine.getDailyNutritionSummary(now)
        assertEquals("Today's calories must only include today's meal (300 kcal)", 300.0, todaySummary.totalCalories, 0.1)
        assertEquals("Today's protein must only include today's meal (15g)", 15.0, todaySummary.totalProtein, 0.1)
        assertEquals("Today's meal count must be 1", 1, todaySummary.mealCount)

        val yesterdaySummary = wellnessEngine.getDailyNutritionSummary(yesterdayTimestamp)
        assertEquals("Yesterday's calories must be 500 kcal", 500.0, yesterdaySummary.totalCalories, 0.1)
    }

    // TEST 11 — Macro aggregation (Independent sums of calories, protein, carbs, fat, fiber)
    @Test
    fun testMacroAggregation() = runBlocking {
        val now = System.currentTimeMillis()
        val m1 = FoodHistoryEntity(foodId = "f1", foodName = "F1", portionGrams = 100.0, calories = 150.0, protein = 5.0, carbs = 25.0, fat = 3.0, fiber = 2.0, isUserConfirmed = true, timestamp = now)
        val m2 = FoodHistoryEntity(foodId = "f2", foodName = "F2", portionGrams = 100.0, calories = 250.0, protein = 20.0, carbs = 10.0, fat = 12.0, fiber = 1.0, isUserConfirmed = true, timestamp = now)
        val m3 = FoodHistoryEntity(foodId = "f3", foodName = "F3", portionGrams = 100.0, calories = 100.0, protein = 2.0, carbs = 15.0, fat = 4.0, fiber = 4.0, isUserConfirmed = true, timestamp = now)

        database.foodHistoryDao().insertFoodHistory(m1)
        database.foodHistoryDao().insertFoodHistory(m2)
        database.foodHistoryDao().insertFoodHistory(m3)

        val summary = wellnessEngine.getDailyNutritionSummary(now)
        assertEquals(500.0, summary.totalCalories, 0.1)
        assertEquals(27.0, summary.totalProtein, 0.1)
        assertEquals(50.0, summary.totalCarbs, 0.1)
        assertEquals(19.0, summary.totalFat, 0.1)
        assertEquals(7.0, summary.totalFiber, 0.1)
        assertEquals(3, summary.mealCount)
    }

    // TEST 12 — Confirmed meal only (Unconfirmed scans & portion previews do not persist)
    @Test
    fun testConfirmedMealOnly() = runBlocking {
        // 1. Scan food
        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)

        // 2. Preview 3 different portion sizes
        val preview1 = wellnessEngine.calculateNutritionForPortion("biryani_01", "Biryani", 150.0)
        val preview2 = wellnessEngine.calculateNutritionForPortion("biryani_01", "Biryani", 250.0)
        val preview3 = wellnessEngine.calculateNutritionForPortion("biryani_01", "Biryani", 350.0)
        assertNotNull(preview1)
        assertNotNull(preview2)
        assertNotNull(preview3)

        // 3. Verify Room still has 0 meal records
        val meals = database.foodHistoryDao().getAllRecentHistory(10)
        assertEquals("Previews must NOT persist to Room", 0, meals.size)

        // 4. User explicitly confirms portion
        wellnessEngine.confirmFoodPortion("biryani_01", "Biryani", 350.0)
        val mealsAfterConfirm = database.foodHistoryDao().getAllRecentHistory(10)
        assertEquals("Explicit confirmation must create exactly 1 meal record", 1, mealsAfterConfirm.size)
        assertEquals(350.0, mealsAfterConfirm[0].portionGrams, 0.1)
    }

    // TEST 13 — Personalized portion changes nutrition (Data path verification)
    @Test
    fun testPersonalizedPortionChangesNutrition() = runBlocking {
        // Baseline 200g
        val p200 = wellnessEngine.calculateNutritionForPortion("biryani_01", "Biryani", 200.0)
        assertNotNull(p200)

        // User adjusts to 300g (1.5x of 200g)
        val p300 = wellnessEngine.calculateNutritionForPortion("biryani_01", "Biryani", 300.0)
        assertNotNull(p300)

        assertEquals("Calories must be exactly 1.5x", p200!!.calories * 1.5, p300!!.calories, 0.1)
        assertEquals("Protein must be exactly 1.5x", p200.protein * 1.5, p300.protein, 0.1)
        assertEquals("Carbs must be exactly 1.5x", p200.carbohydrates * 1.5, p300.carbohydrates, 0.1)
        assertEquals("Fat must be exactly 1.5x", p200.fat * 1.5, p300.fat, 0.1)
        assertEquals("Fiber must be exactly 1.5x", p200.fiber * 1.5, p300.fiber, 0.1)
    }
}
