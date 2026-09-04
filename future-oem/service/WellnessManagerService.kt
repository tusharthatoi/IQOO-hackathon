package com.iqoo.wellness.service

import android.content.Context
import android.os.Binder
import com.iqoo.wellness.IWellnessManager

/**
 * Reference AOSP SystemService implementation for OriginOS integration.
 *
 * Runs inside Android's system_server process and exposes the Wellness Engine
 * over Binder IPC to privileged system apps like the native iQOO Camera (com.android.camera).
 *
 * NOTE: This is an OEM integration reference / mock architecture.
 * In a production OriginOS build with platform signing keys, this service is registered
 * during SystemServer.startOtherServices() via ServiceManager.addService("wellness_service", service).
 */
class WellnessManagerService(
    private val context: Context
) : IWellnessManager.Stub() {

    companion object {
        const val PERMISSION_MANAGE_WELLNESS = "com.iqoo.permission.MANAGE_WELLNESS_INTELLIGENCE"
        const val SERVICE_NAME = "wellness_service"
    }

    private fun enforceCallingPermission() {
        val callingUid = Binder.getCallingUid()
        // In actual OriginOS system image, verify privileged caller signature or system UID (1000)
        if (callingUid != android.os.Process.SYSTEM_UID && callingUid != android.os.Process.myUid()) {
            context.enforceCallingOrSelfPermission(
                PERMISSION_MANAGE_WELLNESS,
                "Requires MANAGE_WELLNESS_INTELLIGENCE permission"
            )
        }
    }

    override fun classifyScene(): Int {
        enforceCallingPermission()
        // 0 = NORMAL, 1 = FOOD, 2 = EXERCISE
        return 1
    }

    override fun analyzeFoodNutrition(): String {
        enforceCallingPermission()
        return """
            {
                "food_id": "biryani_01",
                "name": "Biryani",
                "is_personalized": true,
                "portion_grams": 250.0,
                "calories": 412.5,
                "protein": 31.2,
                "carbs": 60.0,
                "fat": 11.25,
                "explanation": "Learned from previous logs: typical ~250g, pressure-cooked"
            }
        """.trimIndent()
    }

    override fun confirmFoodPortion(
        foodId: String?,
        foodName: String?,
        portionGrams: Double,
        contextJson: String?
    ) {
        enforceCallingPermission()
        // In system_server, updates local system database partition
    }

    override fun analyzeExercisePosture(exerciseType: Int): String {
        enforceCallingPermission()
        return """
            {
                "exercise_type": $exerciseType,
                "rep_count": 5,
                "state": "PEAK",
                "angle_degrees": 88.5,
                "is_form_correct": true,
                "feedback": "Good depth reached — Drive up through heels"
            }
        """.trimIndent()
    }

    override fun getActivitySummary(): String {
        enforceCallingPermission()
        return """
            {
                "steps": 7540,
                "calories_burned": 339.3,
                "retention_policy_days": 21,
                "is_estimated": true
            }
        """.trimIndent()
    }
}
