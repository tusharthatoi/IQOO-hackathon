package com.iqoo.wellness.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.R
import com.iqoo.wellness.app.WellnessManager
import com.iqoo.wellness.app.MainActivity
import com.iqoo.wellness.app.databinding.FragmentInsightsBinding
import com.iqoo.wellness.engine.storage.FoodMemoryRecord
import com.iqoo.wellness.engine.storage.WorkoutSessionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    private lateinit var wellnessManager: WellnessManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wellnessManager = (requireActivity() as MainActivity).wellnessManager
        
        binding.cardFoodNutrition.setOnClickListener {
            (requireActivity() as MainActivity).openTab(R.id.nav_food)
        }

        loadInsights()
    }

    private fun loadInsights() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = wellnessManager.engine.getDailyNutritionSummary()
                binding.txtNutritionInsight.text = if (summary.mealCount == 0) {
                    "No meals logged today yet."
                } else {
                    "You've logged ${summary.mealCount} meal${if (summary.mealCount == 1) "" else "s"} and ${summary.totalCalories.toInt()} kcal today."
                }

                val workouts = wellnessManager.engine.getWorkoutSessionsSince(
                    System.currentTimeMillis() - 6L * 86_400_000L
                )
                renderWorkoutInsights(workouts)
            } catch (e: Exception) {
                android.util.Log.e("INSIGHTS", "Failed to load wellness insights", e)
            }
        }
    }

    private fun renderWorkoutInsights(workouts: List<WorkoutSessionEntity>) {
        binding.containerWorkoutHistory.removeAllViews()
        if (workouts.isEmpty()) {
            binding.txtExerciseInsight.text = "Complete a workout to start building your exercise history."
            binding.txtPostureInsight.text = "Complete a workout to start building posture history."
            return
        }

        binding.txtExerciseInsight.text = "You completed ${workouts.size} workout${if (workouts.size == 1) "" else "s"} in the last 5 days."
        val latest = workouts.first()
        android.util.Log.i(
            "POSTURE_INSIGHTS",
            "latestExercise=${latest.exerciseType} latestPosture=${latest.formScore != null} latestConfidence=${latest.formScore}"
        )
        binding.txtPostureInsight.text = latest.formScore?.let {
            "Your latest workout recorded ${(it * 100).toInt()}% posture confidence."
        } ?: "Posture data unavailable for the latest workout."

        val dateFormat = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
        workouts.forEach { workout ->
            android.util.Log.i(
                "POSTURE_HISTORY",
                "exercise=${workout.exerciseType} posture=${workout.formScore != null} confidence=${workout.formScore}"
            )
            val row = TextView(requireContext()).apply {
                text = "${displayExercise(workout.exerciseType)}  •  ${workout.reps} reps  •  " +
                    "${workout.formScore?.let { "${(it * 100).toInt()}% form" } ?: "form unavailable"}\n" +
                    dateFormat.format(Date(workout.timestamp))
                setTextColor(resources.getColor(R.color.iqoo_text_primary, null))
                textSize = 14f
                setPadding(16, 14, 16, 14)
            }
            binding.containerWorkoutHistory.addView(row)
        }
    }

    private fun displayExercise(value: String): String = value.replace('_', ' ').lowercase()
        .split(' ').joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}