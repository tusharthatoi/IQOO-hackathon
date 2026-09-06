package com.iqoo.wellness.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.camera.CameraManager
import com.iqoo.wellness.app.ui.CameraOverlayView
import com.iqoo.wellness.engine.food.FoodResultState
import com.iqoo.wellness.engine.scene.SceneType
import kotlinx.coroutines.launch

/**
 * Camera Intelligence Viewport.
 * Projects real-time AI nutrition, exercise correction, and activity metrics onto the camera viewfinder.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraOverlay: CameraOverlayView

    // Top Header & Mode Selectors
    private lateinit var txtHeaderSubtitle: TextView
    private lateinit var btnAuto: TextView
    private lateinit var btnFood: TextView
    private lateinit var btnPosture: TextView

    // Top Result Card Views
    private lateinit var cardResult: LinearLayout
    private lateinit var imgCropThumbnail: ImageView
    private lateinit var txtResultTitle: TextView
    private lateinit var txtResultConfidence: TextView
    private lateinit var iconState: ImageView
    private lateinit var txtResultSubtext: TextView
    private lateinit var txtResultMacros: TextView

    // Interactive Confirmation & Portion Selection Containers
    private lateinit var containerFoodConfirmation: LinearLayout
    private lateinit var btnConfirmFood: TextView
    private lateinit var btnChangeFood: TextView

    private lateinit var containerPortionSelection: LinearLayout
    private lateinit var txtPortionPrompt: TextView
    private lateinit var btnPresetSmall: TextView
    private lateinit var btnPresetMedium: TextView
    private lateinit var btnPresetLarge: TextView
    private lateinit var txtPortionGrams: TextView
    private lateinit var btnPortionMinus: TextView
    private lateinit var btnPortionPlus: TextView
    private lateinit var btnConfirmPortion: TextView

    private lateinit var containerConfirmedAction: LinearLayout
    private lateinit var btnScanNext: TextView

    // Bottom Controls
    private lateinit var txtBottomHint: TextView
    private lateinit var txtBottomSubhint: TextView

    private lateinit var wellnessManager: WellnessManager
    private var cameraManager: CameraManager? = null

    enum class FoodUIState {
        SCANNING,
        FOOD_DETECTED,
        PORTION_SELECTION,
        PORTION_CONFIRMED
    }

    private var currentFoodUIState = FoodUIState.SCANNING
    private var activeFoodResult: com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult? = null
    private var currentSelectedGrams: Double = 250.0
    private var presetSmallGrams: Double = 125.0
    private var presetMediumGrams: Double = 250.0
    private var presetLargeGrams: Double = 375.0


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for vision intelligence", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        wellnessManager = WellnessManager(this)

        // Seed offline nutrition data
        lifecycleScope.launch {
            wellnessManager.engine.initializeOfflineData()
        }

        setupUI()
        setupFoodInteractionListeners()
        checkPermissionsAndStart()
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        cameraOverlay = findViewById(R.id.cameraOverlay)

        txtHeaderSubtitle = findViewById(R.id.txtHeaderSubtitle)
        btnAuto = findViewById(R.id.btnAuto)
        btnFood = findViewById(R.id.btnFood)
        btnPosture = findViewById(R.id.btnPosture)

        cardResult = findViewById(R.id.cardResult)
        imgCropThumbnail = findViewById(R.id.imgCropThumbnail)
        txtResultTitle = findViewById(R.id.txtResultTitle)
        txtResultConfidence = findViewById(R.id.txtResultConfidence)
        iconState = findViewById(R.id.iconState)
        txtResultSubtext = findViewById(R.id.txtResultSubtext)
        txtResultMacros = findViewById(R.id.txtResultMacros)

        // Interactive containers
        containerFoodConfirmation = findViewById(R.id.containerFoodConfirmation)
        btnConfirmFood = findViewById(R.id.btnConfirmFood)
        btnChangeFood = findViewById(R.id.btnChangeFood)

        containerPortionSelection = findViewById(R.id.containerPortionSelection)
        txtPortionPrompt = findViewById(R.id.txtPortionPrompt)
        btnPresetSmall = findViewById(R.id.btnPresetSmall)
        btnPresetMedium = findViewById(R.id.btnPresetMedium)
        btnPresetLarge = findViewById(R.id.btnPresetLarge)
        txtPortionGrams = findViewById(R.id.txtPortionGrams)
        btnPortionMinus = findViewById(R.id.btnPortionMinus)
        btnPortionPlus = findViewById(R.id.btnPortionPlus)
        btnConfirmPortion = findViewById(R.id.btnConfirmPortion)

        containerConfirmedAction = findViewById(R.id.containerConfirmedAction)
        btnScanNext = findViewById(R.id.btnScanNext)

        txtBottomHint = findViewById(R.id.txtBottomHint)
        txtBottomSubhint = findViewById(R.id.txtBottomSubhint)
    }

    private fun setupUI() {
        btnAuto.setOnClickListener {
            switchMode(SceneType.NORMAL)
        }

        btnFood.setOnClickListener {
            switchMode(SceneType.FOOD)
        }

        btnPosture.setOnClickListener {
            switchMode(SceneType.EXERCISE)
        }

        // Set default to Food Mode matching the reference camera design
        switchMode(SceneType.FOOD)

        // Connect WellnessManager listeners to HUD Overlay & Result Card
        wellnessManager.onFoodAnalyzed = { result ->
            runOnUiThread {
                cameraOverlay.updateFoodResult(result)
                updateResultCard(result)
            }
        }

        wellnessManager.onPostureAnalyzed = { feedback ->
            runOnUiThread {
                cameraOverlay.updatePostureFeedback(feedback)
            }
        }

        wellnessManager.onSceneDetected = { scene ->
            runOnUiThread {
                txtHeaderSubtitle.text = "Detected: ${scene.name} Scene"
            }
        }
    }

    private fun setupFoodInteractionListeners() {
        btnConfirmFood.setOnClickListener {
            val result = activeFoodResult ?: return@setOnClickListener
            currentFoodUIState = FoodUIState.PORTION_SELECTION
            transitionToPortionSelection(result)
        }

        btnChangeFood.setOnClickListener {
            // Reject current prediction (not saved to history) and resume live scanning
            resetFoodScanning()
        }

        btnPresetSmall.setOnClickListener {
            selectPreset(presetSmallGrams, btnPresetSmall)
        }

        btnPresetMedium.setOnClickListener {
            selectPreset(presetMediumGrams, btnPresetMedium)
        }

        btnPresetLarge.setOnClickListener {
            selectPreset(presetLargeGrams, btnPresetLarge)
        }

        btnPortionMinus.setOnClickListener {
            currentSelectedGrams = (currentSelectedGrams - 25.0).coerceAtLeast(25.0)
            updateStepperSelection()
        }

        btnPortionPlus.setOnClickListener {
            currentSelectedGrams = (currentSelectedGrams + 25.0).coerceAtMost(1000.0)
            updateStepperSelection()
        }

        btnConfirmPortion.setOnClickListener {
            val result = activeFoodResult ?: return@setOnClickListener
            val foodId = result.foodItem.foodId
            val foodName = result.foodItem.name
            val grams = currentSelectedGrams

            lifecycleScope.launch {
                wellnessManager.engine.confirmFoodPortion(
                    foodId = foodId,
                    foodName = foodName,
                    confirmedPortionGrams = grams
                )
            }

            currentFoodUIState = FoodUIState.PORTION_CONFIRMED
            cardResult.setBackgroundResource(R.drawable.bg_result_card_food)
            txtResultTitle.text = "$foodName (${grams.toInt()}g)"
            txtResultTitle.setTextColor(Color.WHITE)
            txtResultConfidence.text = "✓ Confirmed"
            txtResultConfidence.setTextColor(Color.parseColor("#2ECC71"))
            iconState.setImageResource(R.drawable.ic_leaf)
            txtResultSubtext.text = if (result.isPersonalized) "Updated your personalized food history" else "Saved to local wellness history"

            containerPortionSelection.visibility = View.GONE
            containerFoodConfirmation.visibility = View.GONE
            containerConfirmedAction.visibility = View.VISIBLE
        }

        btnScanNext.setOnClickListener {
            resetFoodScanning()
        }
    }

    private fun transitionToPortionSelection(result: com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult) {
        containerFoodConfirmation.visibility = View.GONE
        containerPortionSelection.visibility = View.VISIBLE
        containerConfirmedAction.visibility = View.GONE

        txtResultTitle.text = result.foodItem.name

        val defaultGrams = if (result.context.typicalPortionGrams > 0) result.context.typicalPortionGrams else 250.0

        if (result.isPersonalized && result.context.hasHistory) {
            // Suggestion based on retrieved user history
            txtPortionPrompt.text = "Your usual portion: ~${defaultGrams.toInt()}g"
            presetSmallGrams = ((defaultGrams - 25.0).coerceAtLeast(25.0) / 5.0).toInt() * 5.0
            presetMediumGrams = (defaultGrams / 5.0).toInt() * 5.0
            presetLargeGrams = ((defaultGrams + 25.0) / 5.0).toInt() * 5.0

            btnPresetSmall.text = "${presetSmallGrams.toInt()}g"
            btnPresetMedium.text = "${presetMediumGrams.toInt()}g"
            btnPresetLarge.text = "${presetLargeGrams.toInt()}g"
        } else {
            // First scan defaults: Small (0.5x), Medium (1.0x), Large (1.5x)
            txtPortionPrompt.text = "How much did you eat?"
            presetSmallGrams = Math.round((defaultGrams * 0.5) / 10.0) * 10.0
            presetMediumGrams = Math.round(defaultGrams / 10.0) * 10.0
            presetLargeGrams = Math.round((defaultGrams * 1.5) / 10.0) * 10.0

            btnPresetSmall.text = "Small (${presetSmallGrams.toInt()}g)"
            btnPresetMedium.text = "Medium (${presetMediumGrams.toInt()}g)"
            btnPresetLarge.text = "Large (${presetLargeGrams.toInt()}g)"
        }

        // Highlight Medium by default
        selectPreset(presetMediumGrams, btnPresetMedium)
    }

    private fun selectPreset(grams: Double, selectedButton: TextView) {
        currentSelectedGrams = grams
        btnPresetSmall.setBackgroundResource(if (selectedButton == btnPresetSmall) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        btnPresetMedium.setBackgroundResource(if (selectedButton == btnPresetMedium) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        btnPresetLarge.setBackgroundResource(if (selectedButton == btnPresetLarge) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)

        updateLiveNutritionPreview()
    }

    private fun updateStepperSelection() {
        btnPresetSmall.setBackgroundResource(if (currentSelectedGrams == presetSmallGrams) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        btnPresetMedium.setBackgroundResource(if (currentSelectedGrams == presetMediumGrams) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)
        btnPresetLarge.setBackgroundResource(if (currentSelectedGrams == presetLargeGrams) R.drawable.bg_pill_preset_selected else R.drawable.bg_pill_preset_unselected)

        updateLiveNutritionPreview()
    }

    private fun updateLiveNutritionPreview() {
        txtPortionGrams.text = "Portion: ${currentSelectedGrams.toInt()} g"
        val foodId = activeFoodResult?.foodItem?.foodId ?: return
        val foodName = activeFoodResult?.foodItem?.name ?: return

        lifecycleScope.launch {
            val nutrition = wellnessManager.engine.calculateNutritionForPortion(foodId, foodName, currentSelectedGrams)
            runOnUiThread {
                if (nutrition != null && nutrition.calories > 0.0) {
                    txtResultMacros.visibility = View.VISIBLE
                    txtResultMacros.text = "~${nutrition.calories.toInt()} kcal  •  Protein ${nutrition.protein}g  •  Carbs ${nutrition.carbohydrates}g  •  Fat ${nutrition.fat}g"
                } else {
                    txtResultMacros.visibility = View.VISIBLE
                    txtResultMacros.text = "Food recognized, but nutrition data is unavailable."
                }
            }
        }
    }

    private fun resetFoodScanning() {
        currentFoodUIState = FoodUIState.SCANNING
        activeFoodResult = null
        containerFoodConfirmation.visibility = View.GONE
        containerPortionSelection.visibility = View.GONE
        containerConfirmedAction.visibility = View.GONE
        txtResultMacros.visibility = View.GONE
        cardResult.setBackgroundResource(R.drawable.bg_result_card_scanning)
        txtResultTitle.text = "Scanning food..."
        txtResultTitle.setTextColor(Color.parseColor("#90A4AE"))
        txtResultConfidence.text = ""
        iconState.setImageResource(R.drawable.ic_leaf)
        txtResultSubtext.text = "Hold steady for best results"
    }

    private fun updateResultCard(result: com.iqoo.wellness.engine.personalization.PersonalizedNutritionResult?) {
        // If user is currently confirming or adjusting portion, do not overwrite card!
        if (currentFoodUIState != FoodUIState.SCANNING) {
            return
        }

        if (result == null) {
            resetFoodScanning()
            return
        }

        val item = result.foodItem
        item.thumbnail?.let { imgCropThumbnail.setImageBitmap(it) }

        when (item.state) {
            FoodResultState.NOT_FOOD -> {
                cardResult.setBackgroundResource(R.drawable.bg_result_card_not_food)
                txtResultTitle.text = "Not Food"
                txtResultTitle.setTextColor(Color.parseColor("#EF5350"))
                txtResultConfidence.text = "${(item.confidence * 100).toInt()}%"
                txtResultConfidence.setTextColor(Color.parseColor("#EF5350"))
                iconState.setImageResource(R.drawable.ic_warning_circle)
                txtResultSubtext.text = "This doesn't look like a food item.\nTry scanning a dish or meal."
                txtResultMacros.visibility = View.GONE
                containerFoodConfirmation.visibility = View.GONE
                containerPortionSelection.visibility = View.GONE
                containerConfirmedAction.visibility = View.GONE
            }
            FoodResultState.LOW_CONFIDENCE -> {
                cardResult.setBackgroundResource(R.drawable.bg_result_card_scanning)
                txtResultTitle.text = "Unknown Food"
                txtResultTitle.setTextColor(Color.parseColor("#FFB74D"))
                txtResultConfidence.text = "${(item.confidence * 100).toInt()}%"
                txtResultConfidence.setTextColor(Color.parseColor("#FFB74D"))
                iconState.setImageResource(R.drawable.ic_warning_circle)
                txtResultSubtext.text = "Hold steady and point the camera at a food item."
                txtResultMacros.visibility = View.GONE
                containerFoodConfirmation.visibility = View.GONE
                containerPortionSelection.visibility = View.GONE
                containerConfirmedAction.visibility = View.GONE
            }
            FoodResultState.SCANNING -> {
                cardResult.setBackgroundResource(R.drawable.bg_result_card_scanning)
                txtResultTitle.text = "Scanning food..."
                txtResultTitle.setTextColor(Color.parseColor("#90A4AE"))
                txtResultConfidence.text = ""
                iconState.setImageResource(R.drawable.ic_leaf)
                txtResultSubtext.text = "Analyzing camera stream..."
                txtResultMacros.visibility = View.GONE
                containerFoodConfirmation.visibility = View.GONE
                containerPortionSelection.visibility = View.GONE
                containerConfirmedAction.visibility = View.GONE
            }
            FoodResultState.FOOD_DETECTED -> {
                // Real TFLite Food Detected -> Freeze and present confirmation to user!
                activeFoodResult = result
                currentFoodUIState = FoodUIState.FOOD_DETECTED
                cardResult.setBackgroundResource(R.drawable.bg_result_card_food)
                txtResultTitle.text = "Is this ${item.name}?"
                txtResultTitle.setTextColor(Color.WHITE)
                txtResultConfidence.text = "${(item.confidence * 100).toInt()}%"
                txtResultConfidence.setTextColor(Color.parseColor("#2ECC71"))
                iconState.setImageResource(R.drawable.ic_leaf)
                txtResultSubtext.text = if (result.isPersonalized) result.displaySubtext else "Please confirm dish to calculate nutrition."
                txtResultMacros.visibility = View.GONE

                containerFoodConfirmation.visibility = View.VISIBLE
                containerPortionSelection.visibility = View.GONE
                containerConfirmedAction.visibility = View.GONE
            }
        }
    }

    private fun switchMode(mode: SceneType) {
        wellnessManager.setMode(mode)
        cameraOverlay.setMode(mode)

        // Reset food scanning state when switching away from food
        if (mode != SceneType.FOOD) {
            resetFoodScanning()
            cardResult.visibility = View.GONE
        } else {
            cardResult.visibility = View.VISIBLE
            resetFoodScanning()
        }

        // Update pill button styling
        btnAuto.setBackgroundResource(if (mode == SceneType.NORMAL) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnAuto.setTextColor(if (mode == SceneType.NORMAL) Color.WHITE else Color.parseColor("#CFD8DC"))
        btnFood.setBackgroundResource(if (mode == SceneType.FOOD) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnFood.setTextColor(if (mode == SceneType.FOOD) Color.WHITE else Color.parseColor("#CFD8DC"))
        btnPosture.setBackgroundResource(if (mode == SceneType.EXERCISE) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnPosture.setTextColor(if (mode == SceneType.EXERCISE) Color.WHITE else Color.parseColor("#CFD8DC"))

        // Update bottom hints
        when (mode) {
            SceneType.FOOD -> {
                txtBottomHint.text = "Point the camera at a food item"
                txtBottomSubhint.text = "Hold steady for best results"
            }
            SceneType.EXERCISE -> {
                txtBottomHint.text = "Stand in frame for posture check"
                txtBottomSubhint.text = "Full body visibility recommended"
            }
            SceneType.NORMAL -> {
                txtBottomHint.text = "Auto-detecting scene"
                txtBottomSubhint.text = "Camera intelligence active"
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            setupCamera()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupCamera() {
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            frameListener = wellnessManager
        )
        cameraManager?.startCamera(
            onSuccess = {
                txtHeaderSubtitle.text = "Scan your food"
            },
            onError = { exc ->
                Toast.makeText(this, "Camera initialization failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
    }
}
