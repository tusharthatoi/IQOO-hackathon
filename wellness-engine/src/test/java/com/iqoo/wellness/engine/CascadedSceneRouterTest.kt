package com.iqoo.wellness.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iqoo.wellness.engine.scene.LightweightSceneClassifier
import com.iqoo.wellness.engine.scene.SceneType
import com.iqoo.wellness.engine.storage.WellnessDatabase
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
class CascadedSceneRouterTest {

    private lateinit var database: WellnessDatabase
    private lateinit var classifier: LightweightSceneClassifier
    private lateinit var engine: WellnessEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WellnessDatabase.createInMemory(context)
        classifier = LightweightSceneClassifier()
        engine = WellnessEngineImpl.createForTesting(
            database = database,
            sceneClassifier = classifier
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCascadedSceneRouting() = runBlocking {
        // Food scene routing
        classifier.setForcedScene(SceneType.FOOD)
        assertEquals(SceneType.FOOD, engine.analyzeScene())

        // Exercise posture routing
        classifier.setForcedScene(SceneType.EXERCISE)
        assertEquals(SceneType.EXERCISE, engine.analyzeScene())

        // Normal idle scene routing
        classifier.setForcedScene(SceneType.NORMAL)
        assertEquals(SceneType.NORMAL, engine.analyzeScene())
    }
}
