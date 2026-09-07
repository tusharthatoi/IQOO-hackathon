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
import kotlinx.coroutines.launch

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
        
        loadInsights()
    }

    private fun loadInsights() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = wellnessManager.engine.getDailyNutritionSummary()
                if (summary.totalProtein < 50) {
                    binding.txtNutritionInsight.text = "Your protein intake is low today. Consider adding some paneer or chicken to your next meal."
                } else {
                    binding.txtNutritionInsight.text = "Great job! You've met your protein target for today."
                }
                
                val memory = wellnessManager.engine.getFoodMemory()
                renderFoodMemory(memory)
            } catch (e: Exception) {
                // Error
            }
        }
    }

    private fun renderFoodMemory(records: List<com.iqoo.wellness.engine.storage.FoodMemoryRecord>) {
        binding.containerFoodMemory.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (record in records.take(3)) {
            val view = inflater.inflate(R.layout.item_dashboard_food_memory, binding.containerFoodMemory, false)
            view.findViewById<TextView>(R.id.txtMemoryFoodName).text = record.foodName
            view.findViewById<TextView>(R.id.txtMemoryUsualPortion).text = "Usual: ${record.averagePortionGrams.toInt()}g"
            binding.containerFoodMemory.addView(view)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}