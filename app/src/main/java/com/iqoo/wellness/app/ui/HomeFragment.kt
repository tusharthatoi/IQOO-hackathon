package com.iqoo.wellness.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.R
import com.iqoo.wellness.app.WellnessManager
import com.iqoo.wellness.app.MainActivity
import com.iqoo.wellness.app.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var wellnessManager: WellnessManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wellnessManager = (requireActivity() as MainActivity).wellnessManager
        binding.btnQuickFood.setOnClickListener { (requireActivity() as MainActivity).openTab(R.id.nav_food) }
        binding.btnQuickExercise.setOnClickListener { (requireActivity() as MainActivity).openTab(R.id.nav_exercise) }
        binding.btnQuickInsights.setOnClickListener { (requireActivity() as MainActivity).openTab(R.id.nav_insights) }
        loadDailyStats()
    }

    private fun loadDailyStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = wellnessManager.engine.getDailyNutritionSummary(System.currentTimeMillis())
                binding.txtDailyCalories.text = String.format(Locale.US, "%,d", summary.totalCalories.toInt())
                
                val activity = wellnessManager.engine.getActivitySummary()
                binding.txtSteps.text = String.format(Locale.US, "%,d", activity.steps)
                
                binding.txtWorkoutCount.text = summary.mealCount.toString()
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading stats", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}