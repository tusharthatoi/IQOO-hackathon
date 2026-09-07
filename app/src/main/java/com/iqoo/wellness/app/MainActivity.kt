package com.iqoo.wellness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.iqoo.wellness.app.ui.ExerciseFragment
import com.iqoo.wellness.app.ui.FoodFragment
import com.iqoo.wellness.app.ui.HomeFragment
import com.iqoo.wellness.app.ui.InsightsFragment
import com.iqoo.wellness.app.ui.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    val wellnessManager: WellnessManager by lazy { WellnessManager(applicationContext) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for vision intelligence", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigation = findViewById(R.id.bottom_navigation)
        setupNavigation()

        checkPermissions()

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_food -> FoodFragment()
                R.id.nav_exercise -> ExerciseFragment()
                R.id.nav_insights -> InsightsFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            replaceFragment(fragment)
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    fun openTab(itemId: Int) {
        bottomNavigation.selectedItemId = itemId
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}