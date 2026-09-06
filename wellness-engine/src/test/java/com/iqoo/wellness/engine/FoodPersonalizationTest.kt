package com.iqoo.wellness.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.food.BoundingBox
import com.iqoo.wellness.engine.food.FoodRecognizer
import com.iqoo.wellness.engine.food.FoodSeedDatabase
import com.iqoo.wellness.engine.food.NutritionCalculator
import com.iqoo.wellness.engine.food.RecognizedFoodItem
import com.iqoo.wellness.engine.personalization.FoodPersonalizationEngine
import com.iqoo.wellness.engine.personalization.PersonalizedFoodContext
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodPersonalizationTest {

    private lateinit var database: WellnessDatabase
    private lateinit var personalizationEngine: FoodPersonalizationEngine
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
        personalizationEngine = wellnessEngine.personalizationEngine
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testFirstTimeFoodWithNoHistory() = runBlocking {
        // First scan of Biryani: No prior logs in database
        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertEquals("Biryani", result!!.foodItem.name)
        assertFalse("First scan must not be flagged as personalized", result.isPersonalized)
        assertFalse("First scan context must report hasHistory = false", result.context.hasHistory)
        assertEquals(250.0, result.context.typicalPortionGrams, 0.1) // Default serving from seed DB
        assertTrue("Display subtext should mention standard estimate", result.displaySubtext.contains("Standard estimate", ignoreCase = true))
    }

    @Test
    fun testRepeatedFoodWithLearnedPortion() = runBlocking {
        // 1. First encounter
        val initialResult = wellnessEngine.analyzeFood()
        assertNotNull(initialResult)
        assertFalse(initialResult!!.isPersonalized)

        // 2. User confirms custom portion of 280g
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 280.0,
            cookingMethod = "Pressure-cooked",
            oilFatLevel = "MODERATE"
        )

        // 3. Subsequent scan retrieves learned portion
        val subsequentResult = wellnessEngine.analyzeFood()
        assertNotNull(subsequentResult)
        assertTrue("Subsequent scan must be personalized", subsequentResult!!.isPersonalized)
        assertTrue(subsequentResult.context.hasHistory)
        assertEquals(280.0, subsequentResult.context.typicalPortionGrams, 0.1)
        assertTrue("Personalized subtext should mention usual portion", subsequentResult.displaySubtext.contains("usual portion", ignoreCase = true))
    }

    @Test
    fun testIngredientQuantityRetrievalAndNutritionCalculation() = runBlocking {
        // User confirms specific ingredient split: 180g rice + 70g chicken
        val ingredients = mapOf("rice" to 180.0, "chicken" to 70.0)
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 250.0,
            ingredientQuantities = ingredients,
            cookingMethod = "Dum / Steamed"
        )

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertTrue(result!!.isPersonalized)
        assertEquals(180.0, result.context.ingredientQuantities["rice"] ?: 0.0, 0.1)
        assertEquals(70.0, result.context.ingredientQuantities["chicken"] ?: 0.0, 0.1)

        // Verified calculated macros based on ingredient split:
        // Rice 180g -> 234 kcal, 4.86g P, 50.76g C, 0.54g F
        // Chicken 70g -> 115.5 kcal, 17.5g P, 0g C, 4.9g F
        // Expected total calories ~ 349.5 kcal, protein ~ 22.4g
        assertTrue("Calculated protein must reflect high chicken content (>20g)", result.nutrition.protein > 20.0)
        assertTrue("Calculated carbs must reflect rice content (>45g)", result.nutrition.carbohydrates > 45.0)
    }

    @Test
    fun testCookingAndPreparationMethodRetrieval() = runBlocking {
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 250.0,
            cookingMethod = "Traditional Dum",
            fryingMethod = "NONE"
        )

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertEquals("Traditional Dum", result!!.context.cookingMethod)
        assertTrue(result.displaySubtext.contains("Traditional Dum"))
    }

    @Test
    fun testOilAndFatUsageRetrieval() = runBlocking {
        // Test heavy oil adjustment: +15g oil specified
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 250.0,
            oilGrams = 15.0,
            oilFatLevel = "HEAVY"
        )

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertEquals(15.0, result!!.context.oilGrams ?: 0.0, 0.1)
        assertEquals("HEAVY", result.context.oilFatLevel)

        // Baseline Biryani 250g fat = 11.25g + 15g oil = 26.25g fat
        assertTrue("Fat must incorporate the 15g oil (~26g total)", result.nutrition.fat >= 25.0)
    }

    @Test
    fun testUserCorrectionUpdatingHistory() = runBlocking {
        // First confirmation: 200g
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 200.0
        )
        val scan1 = wellnessEngine.analyzeFood()
        assertEquals(200.0, scan1!!.context.typicalPortionGrams, 0.1)

        // User correction on subsequent day: adjusted to 320g
        wellnessEngine.confirmFoodPortion(
            foodId = "biryani_01",
            foodName = "Biryani",
            confirmedPortionGrams = 320.0,
            userCorrections = "Increased portion size for bulk diet"
        )

        // Next scan retrieves the updated correction
        val scan2 = wellnessEngine.analyzeFood()
        // typicalPortionGrams uses rolling average: (200 + 320) / 2 = 260, or latest context 320
        // Either way, the context should have history and userCorrections should be present
        assertTrue("typicalPortionGrams should reflect updated portion", scan2!!.context.typicalPortionGrams >= 260.0)
        assertEquals("Increased portion size for bulk diet", scan2.context.userCorrections)
    }

    @Test
    fun testFallbackWhenPersonalizationDataUnavailable() = runBlocking {
        // Food that is NOT in seed database and has NO prior history
        val exoticDish = RecognizedFoodItem(
            foodId = "exotic_unknown_dish_99",
            name = "Exotic Curry",
            confidence = 0.82f,
            estimatedAreaPortionGrams = 220.0
        )
        testRecognizer.currentFood = exoticDish

        val result = wellnessEngine.analyzeFood()
        assertNotNull(result)
        assertFalse("Uncatalogued food must not crash and default gracefully", result!!.isPersonalized)
        assertFalse(result.context.hasHistory)
        assertEquals(220.0, result.nutrition.servingGrams, 0.1)
        // Uncatalogued food must NOT fabricate calories — reports 0.0 by design
        assertEquals("Uncatalogued food calories must be 0.0 (no fabrication)", 0.0, result.nutrition.calories, 0.1)
    }
}
