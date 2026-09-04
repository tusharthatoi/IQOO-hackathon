// IWellnessManager.aidl
// IPC Interface contract between Native iQOO Camera and OriginOS WellnessManagerService
package com.iqoo.wellness;

/**
 * Android System Service IPC Interface for the iQOO Wellness AI Engine.
 * Implemented by WellnessManagerService inside Android system_server.
 */
interface IWellnessManager {

    /**
     * Identifies intent from an incoming camera frame stream.
     * Return: 0 = NORMAL, 1 = FOOD, 2 = EXERCISE
     */
    int classifyScene();

    /**
     * Executes food recognition and structured retrieval on the current frame.
     * Returns a JSON-formatted string with dish, portion, macros, and personalized context.
     */
    String analyzeFoodNutrition();

    /**
     * Confirms or corrects a food portion and preparation context into local system storage.
     */
    void confirmFoodPortion(String foodId, String foodName, double portionGrams, String contextJson);

    /**
     * Analyzes exercise posture for the specified exercise type.
     * Exercise types: 0 = SQUAT, 1 = PUSH_UP, 2 = BICEP_CURL, 3 = LUNGE, 4 = SHOULDER_PRESS.
     * Returns a JSON-formatted string with rep count, current state, joint angle, and form feedback.
     */
    String analyzeExercisePosture(int exerciseType);

    /**
     * Retrieves daily physical activity metrics (steps, calories burned).
     * Enforces the 21-day rolling local retention policy.
     */
    String getActivitySummary();
}
