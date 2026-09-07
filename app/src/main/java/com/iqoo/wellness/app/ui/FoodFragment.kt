package com.iqoo.wellness.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.R
import com.iqoo.wellness.app.WellnessManager
import com.iqoo.wellness.app.MainActivity
import com.iqoo.wellness.app.camera.CameraManager
import com.iqoo.wellness.app.databinding.FragmentFoodBinding
import com.iqoo.wellness.engine.food.FoodResultState
import com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult
import com.iqoo.wellness.engine.scene.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class FoodFragment : Fragment() {

    private var _binding: FragmentFoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var wellnessManager: WellnessManager
    private var cameraManager: CameraManager? = null

    enum class FoodUIState {
        SCANNING,
        FOOD_DETECTED,
        PORTION_SELECTION,
        PORTION_CONFIRMED
    }

    private var currentFoodUIState = FoodUIState.SCANNING
    private var activeFoodResult: PersonalizedNutritionResult? = null
    private var currentSelectedGrams: Double = 250.0
    private var presetSmallGrams: Double = 125.0
    private var presetMediumGrams: Double = 250.0
    private var presetLargeGrams: Double = 375.0
    private var isSavingMeal: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wellnessManager = (requireActivity() as MainActivity).wellnessManager
        
        setupUI()
        setupListeners()
        setupCamera()
    }

    private fun setupUI() {
        wellnessManager.setMode(SceneType.FOOD)
        binding.cameraOverlay.setMode(SceneType.FOOD)
        updateModeButtons(SceneType.FOOD)

        wellnessManager.onFoodAnalyzed = { result ->
            activity?.runOnUiThread {
                binding.cameraOverlay.updateFoodResult(result)
                updateResultCard(result)
            }
        }
    }

    private fun setupListeners() {
        binding.btnAuto.setOnClickListener {
            wellnessManager.setMode(SceneType.NORMAL)
            binding.cameraOverlay.setMode(SceneType.NORMAL)
            updateModeButtons(SceneType.NORMAL)
        }
        binding.btnFood.setOnClickListener {
            wellnessManager.setMode(SceneType.FOOD)
            binding.cameraOverlay.setMode(SceneType.FOOD)
            updateModeButtons(SceneType.FOOD)
        }
        binding.btnPosture.setOnClickListener {
            cameraManager?.shutdown()
            (requireActivity() as MainActivity).openTab(R.id.nav_exercise)
        }

        binding.btnConfirmFood.setOnClickListener {
            val result = activeFoodResult ?: return@setOnClickListener
            currentFoodUIState = FoodUIState.PORTION_SELECTION
            transitionToPortionSelection(result)
        }

        binding.btnChangeFood.setOnClickListener {
            resetFoodScanning()
        }

        binding.btnPresetSmall.setOnClickListener {
            selectPreset(presetSmallGrams, binding.btnPresetSmall)
        }

        binding.btnPresetMedium.setOnClickListener {
            selectPreset(presetMediumGrams, binding.btnPresetMedium)
        }

        binding.btnPresetLarge.setOnClickListener {
            selectPreset(presetLargeGrams, binding.btnPresetLarge)
        }

        binding.btnConfirmPortion.setOnClickListener {
            confirmMeal()
        }
    }

    private fun selectPreset(grams: Double, selectedButton: TextView) {
        currentSelectedGrams = grams
        binding.btnPresetSmall.setBackgroundResource(if (selectedButton == binding.btnPresetSmall) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        binding.btnPresetMedium.setBackgroundResource(if (selectedButton == binding.btnPresetMedium) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        binding.btnPresetLarge.setBackgroundResource(if (selectedButton == binding.btnPresetLarge) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)

        updateLiveNutritionPreview()
    }

    private fun updateLiveNutritionPreview() {
        binding.txtPortionGrams.text = "Portion: ${currentSelectedGrams.toInt()}g"
        val foodId = activeFoodResult?.foodItem?.foodId ?: return
        val foodName = activeFoodResult?.foodItem?.name ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val nutrition = wellnessManager.engine.calculateNutritionForPortion(foodId, foodName, currentSelectedGrams)
            withContext(Dispatchers.Main) {
                if (nutrition != null && nutrition.isAvailable) {
                    binding.txtResultMacros.visibility = View.VISIBLE
                    binding.txtResultMacros.text = "~${nutrition.calories.toInt()} kcal  •  P ${nutrition.protein}g  •  C ${nutrition.carbohydrates}g  •  F ${nutrition.fat}g"
                }
            }
        }
    }

    private fun confirmMeal() {
        if (isSavingMeal) return
        val result = activeFoodResult ?: return
        isSavingMeal = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                wellnessManager.engine.confirmFoodPortion(
                    foodId = result.foodItem.foodId,
                    foodName = result.foodItem.name,
                    confirmedPortionGrams = currentSelectedGrams
                )

                withContext(Dispatchers.Main) {
                    currentFoodUIState = FoodUIState.PORTION_CONFIRMED
                    binding.txtResultTitle.text = "${result.foodItem.name} Confirmed"
                    binding.txtResultConfidence.text = "✓"
                    binding.containerPortionSelection.visibility = View.GONE
                    Toast.makeText(requireContext(), "Meal saved!", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSavingMeal = false
            }
        }
    }

    private fun transitionToPortionSelection(result: PersonalizedNutritionResult) {
        binding.containerFoodConfirmation.visibility = View.GONE
        binding.containerPortionSelection.visibility = View.VISIBLE
        binding.txtResultTitle.text = result.foodItem.name
        
        updateLiveNutritionPreview()
    }

    private fun updateResultCard(result: PersonalizedNutritionResult?) {
        if (currentFoodUIState != FoodUIState.SCANNING) return

        if (result == null) {
            resetFoodScanning()
            return
        }

        val item = result.foodItem
        item.thumbnail?.let { binding.imgCropThumbnail.setImageBitmap(it) }

        when (item.state) {
            FoodResultState.FOOD_DETECTED -> {
                activeFoodResult = result
                currentFoodUIState = FoodUIState.FOOD_DETECTED
                binding.txtResultTitle.text = item.name
                binding.txtResultConfidence.text = "${(item.confidence * 100).toInt()}%"
                binding.containerFoodConfirmation.visibility = View.VISIBLE
            }
            else -> {
                binding.txtResultTitle.text = "Scanning..."
                binding.txtResultConfidence.text = ""
                binding.containerFoodConfirmation.visibility = View.GONE
            }
        }
    }

    private fun resetFoodScanning() {
        wellnessManager.resetFoodScanning()
        currentFoodUIState = FoodUIState.SCANNING
        activeFoodResult = null
        binding.containerFoodConfirmation.visibility = View.GONE
        binding.containerPortionSelection.visibility = View.GONE
        binding.txtResultMacros.visibility = View.GONE
        binding.txtResultTitle.text = "Scanning..."
        binding.imgCropThumbnail.setImageDrawable(null)
    }

    private fun setupCamera() {
        cameraManager = CameraManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            frameListener = wellnessManager
        )
        cameraManager?.startCamera()
    }

    private fun updateModeButtons(mode: SceneType) {
        binding.btnAuto.setBackgroundResource(if (mode == SceneType.NORMAL) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnFood.setBackgroundResource(if (mode == SceneType.FOOD) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnPosture.setBackgroundResource(if (mode == SceneType.EXERCISE) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnAuto.setTextColor(if (mode == SceneType.NORMAL) Color.WHITE else Color.parseColor("#CFD8DC"))
        binding.btnFood.setTextColor(if (mode == SceneType.FOOD) Color.WHITE else Color.parseColor("#CFD8DC"))
        binding.btnPosture.setTextColor(if (mode == SceneType.EXERCISE) Color.WHITE else Color.parseColor("#CFD8DC"))
    }

    override fun onDestroyView() {
        wellnessManager.clearCallbacks()
        super.onDestroyView()
        cameraManager?.shutdown()
        _binding = null
    }
}