package com.iqoo.wellness.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.food.TFLiteFoodRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TFLiteFoodRecognizerTest {

    private lateinit var context: Context
    private lateinit var recognizer: TFLiteFoodRecognizer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        recognizer = TFLiteFoodRecognizer(
            context = context,
            primaryModelName = "food_classifier_quantized.tflite",
            confidenceThreshold = 0.50f
        )
    }

    @Test
    fun testLabelContractMatches80Classes() {
        assertEquals("labels.txt must contain exactly 80 Indian food classes", 80, recognizer.classCount)
    }

    @Test
    fun testSafeExecutionWithoutCrashing() = runBlocking {
        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (x in 0 until 224) {
            for (y in 0 until 224) {
                testBitmap.setPixel(x, y, Color.rgb((x % 256), (y % 256), ((x + y) % 256)))
            }
        }

        val results = recognizer.recognizeFood(testBitmap)
        assertNotNull("Recognition result must not be null", results)
        // If running on target Android device with native library loaded:
        if (recognizer.isModelLoaded) {
            assertTrue("Should return prediction when model is loaded", results.isNotEmpty())
            val topResult = results.first()
            assertNotNull(topResult.name)
            assertTrue(topResult.confidence in 0.0f..1.0f)
        }
    }
}
