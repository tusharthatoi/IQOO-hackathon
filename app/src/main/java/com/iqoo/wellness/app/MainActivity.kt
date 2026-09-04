package com.iqoo.wellness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.camera.CameraManager
import com.iqoo.wellness.app.ui.CameraOverlayView
import com.iqoo.wellness.engine.scene.SceneType
import kotlinx.coroutines.launch

/**
 * Camera Intelligence Viewport.
 * Projects real-time AI nutrition, exercise correction, and activity metrics onto the camera viewfinder.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraOverlay: CameraOverlayView
    private lateinit var statusText: TextView

    private lateinit var wellnessManager: WellnessManager
    private var cameraManager: CameraManager? = null

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

        previewView = findViewById(R.id.previewView)
        cameraOverlay = findViewById(R.id.cameraOverlay)
        statusText = findViewById(R.id.statusText)

        wellnessManager = WellnessManager(this)

        // Seed offline nutrition data
        lifecycleScope.launch {
            wellnessManager.engine.initializeOfflineData()
        }

        setupUI()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        val btnAuto = findViewById<Button>(R.id.btnAuto)
        val btnFood = findViewById<Button>(R.id.btnFood)
        val btnPosture = findViewById<Button>(R.id.btnPosture)
        val btnActivity = findViewById<Button>(R.id.btnActivity)

        btnAuto.setOnClickListener {
            switchMode(SceneType.NORMAL, "iQOO Camera Intelligence (Auto Cascaded)")
        }

        btnFood.setOnClickListener {
            switchMode(SceneType.FOOD, "Food Nutrition Intelligence")
        }

        btnPosture.setOnClickListener {
            switchMode(SceneType.EXERCISE, "Real-Time Posture Coach (Squat)")
        }

        btnActivity.setOnClickListener {
            switchMode(SceneType.NORMAL, "Walking & Activity Intelligence")
            lifecycleScope.launch {
                val summary = wellnessManager.engine.getActivitySummary()
                cameraOverlay.updateActivitySummary(summary)
            }
        }

        // Connect WellnessManager listeners to HUD Overlay
        wellnessManager.onFoodAnalyzed = { result ->
            runOnUiThread {
                cameraOverlay.updateFoodResult(result)
            }
        }

        wellnessManager.onPostureAnalyzed = { feedback ->
            runOnUiThread {
                cameraOverlay.updatePostureFeedback(feedback)
            }
        }

        wellnessManager.onSceneDetected = { scene ->
            runOnUiThread {
                statusText.text = "Detected: ${scene.name} Scene"
            }
        }
    }

    private fun switchMode(mode: SceneType, label: String) {
        wellnessManager.setMode(mode)
        cameraOverlay.setMode(mode)
        statusText.text = label
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
                statusText.text = "iQOO Camera Intelligence Active"
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
