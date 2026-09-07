package com.iqoo.wellness.engine

import com.iqoo.wellness.engine.food.FoodResultState
import com.iqoo.wellness.engine.food.FramePrediction
import com.iqoo.wellness.engine.food.GateDecision
import com.iqoo.wellness.engine.food.TemporalStabilityTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class TemporalStabilityTrackerTest {

    @Test
    fun rejectedGateCannotBecomeStableFood() {
        val tracker = TemporalStabilityTracker()

        val result = tracker.addPrediction(
            FramePrediction(
                dishId = "jalebi",
                dishName = "Jalebi",
                confidence = 0.95f,
                gateDecision = GateDecision.NOT_FOOD
            )
        )

        assertEquals(FoodResultState.NOT_FOOD, result.resolvedState)
    }
}