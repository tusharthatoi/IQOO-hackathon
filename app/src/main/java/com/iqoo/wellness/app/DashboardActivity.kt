package com.iqoo.wellness.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.engine.storage.DailyNutritionSummary
import com.iqoo.wellness.engine.storage.FoodHistoryEntity
import com.iqoo.wellness.engine.storage.FoodMemoryRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnDayToday: TextView
    private lateinit var btnDayYesterday: TextView
    private lateinit var btnDayPrevious: TextView

    private lateinit var txtDateHeader: TextView
    private lateinit var txtHeroCalories: TextView
    private lateinit var txtHeroProtein: TextView
    private lateinit var txtMacroCarbs: TextView
    private lateinit var txtMacroFat: TextView
    private lateinit var txtMacroFiber: TextView
    private lateinit var txtMacroMeals: TextView

    private lateinit var txtMealsSectionTitle: TextView
    private lateinit var txtMealsCountBadge: TextView
    private lateinit var containerMealsList: LinearLayout
    private lateinit var txtEmptyMeals: TextView

    private lateinit var containerFoodMemoryList: LinearLayout
    private lateinit var txtEmptyFoodMemory: TextView

    private lateinit var engine: com.iqoo.wellness.engine.WellnessEngine
    private var selectedDayOffset: Int = 0

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        engine = com.iqoo.wellness.engine.WellnessEngineImpl.create(this)
        bindViews()
        setupListeners()
        loadDataForSelectedDay()
        loadFoodMemory()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnDayToday = findViewById(R.id.btnDayToday)
        btnDayYesterday = findViewById(R.id.btnDayYesterday)
        btnDayPrevious = findViewById(R.id.btnDayPrevious)

        txtDateHeader = findViewById(R.id.txtDateHeader)
        txtHeroCalories = findViewById(R.id.txtHeroCalories)
        txtHeroProtein = findViewById(R.id.txtHeroProtein)
        txtMacroCarbs = findViewById(R.id.txtMacroCarbs)
        txtMacroFat = findViewById(R.id.txtMacroFat)
        txtMacroFiber = findViewById(R.id.txtMacroFiber)
        txtMacroMeals = findViewById(R.id.txtMacroMeals)

        txtMealsSectionTitle = findViewById(R.id.txtMealsSectionTitle)
        txtMealsCountBadge = findViewById(R.id.txtMealsCountBadge)
        containerMealsList = findViewById(R.id.containerMealsList)
        txtEmptyMeals = findViewById(R.id.txtEmptyMeals)

        containerFoodMemoryList = findViewById(R.id.containerFoodMemoryList)
        txtEmptyFoodMemory = findViewById(R.id.txtEmptyFoodMemory)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnDayToday.setOnClickListener {
            if (selectedDayOffset != 0) {
                selectedDayOffset = 0
                updateDayPills()
                loadDataForSelectedDay()
            }
        }

        btnDayYesterday.setOnClickListener {
            if (selectedDayOffset != 1) {
                selectedDayOffset = 1
                updateDayPills()
                loadDataForSelectedDay()
            }
        }

        btnDayPrevious.setOnClickListener {
            if (selectedDayOffset != 2) {
                selectedDayOffset = 2
                updateDayPills()
                loadDataForSelectedDay()
            }
        }
    }

    private fun updateDayPills() {
        btnDayToday.setBackgroundResource(if (selectedDayOffset == 0) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnDayToday.setTextColor(if (selectedDayOffset == 0) Color.WHITE else Color.parseColor("#CFD8DC"))

        btnDayYesterday.setBackgroundResource(if (selectedDayOffset == 1) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnDayYesterday.setTextColor(if (selectedDayOffset == 1) Color.WHITE else Color.parseColor("#CFD8DC"))

        btnDayPrevious.setBackgroundResource(if (selectedDayOffset == 2) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        btnDayPrevious.setTextColor(if (selectedDayOffset == 2) Color.WHITE else Color.parseColor("#CFD8DC"))
    }

    private fun loadDataForSelectedDay() {
        val targetTimestamp = System.currentTimeMillis() - (selectedDayOffset * 86400000L)

        when (selectedDayOffset) {
            0 -> {
                txtDateHeader.text = "Today's Nutrition"
                txtMealsSectionTitle.text = "Today's Meals"
            }
            1 -> {
                txtDateHeader.text = "Yesterday's Nutrition"
                txtMealsSectionTitle.text = "Yesterday's Meals"
            }
            else -> {
                txtDateHeader.text = "2 Days Ago Nutrition"
                txtMealsSectionTitle.text = "2 Days Ago Meals"
            }
        }

        lifecycleScope.launch {
            val summary = engine.getDailyNutritionSummary(targetTimestamp)
            val meals = engine.getTodayConfirmedMeals(targetTimestamp)

            renderDailySummary(summary)
            renderMealsList(meals)
        }
    }

    private fun renderDailySummary(summary: DailyNutritionSummary) {
        txtHeroCalories.text = "${summary.totalCalories.toInt()} kcal"
        txtHeroProtein.text = "${summary.totalProtein.toInt()}g protein"

        txtMacroCarbs.text = "${summary.totalCarbs.toInt()}g"
        txtMacroFat.text = "${summary.totalFat.toInt()}g"
        txtMacroFiber.text = "${summary.totalFiber.toInt()}g"
        txtMacroMeals.text = "${summary.mealCount}"

        txtMealsCountBadge.text = "${summary.mealCount} ${if (summary.mealCount == 1) "meal" else "meals"}"
    }

    private fun renderMealsList(meals: List<FoodHistoryEntity>) {
        containerMealsList.removeAllViews()

        if (meals.isEmpty()) {
            txtEmptyMeals.visibility = View.VISIBLE
            txtEmptyMeals.text = when (selectedDayOffset) {
                0 -> "No meals confirmed today."
                1 -> "No meals confirmed yesterday."
                else -> "No meals confirmed for this day."
            }
            containerMealsList.visibility = View.GONE
            return
        }

        txtEmptyMeals.visibility = View.GONE
        containerMealsList.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        for (meal in meals) {
            val itemView = inflater.inflate(R.layout.item_dashboard_meal, containerMealsList, false)
            val txtIcon: TextView = itemView.findViewById(R.id.txtMealIcon)
            val txtName: TextView = itemView.findViewById(R.id.txtMealName)
            val txtMacros: TextView = itemView.findViewById(R.id.txtMealPortionAndMacros)
            val txtCalories: TextView = itemView.findViewById(R.id.txtMealCalories)
            val txtTime: TextView = itemView.findViewById(R.id.txtMealTime)

            txtIcon.text = getFoodEmoji(meal.foodName)
            txtName.text = meal.foodName
            txtMacros.text = "${meal.portionGrams.toInt()}g  •  P: ${meal.protein.toInt()}g  •  C: ${meal.carbs.toInt()}g  •  F: ${meal.fat.toInt()}g"
            txtCalories.text = "~${meal.calories.toInt()} kcal"
            txtTime.text = timeFormat.format(Date(meal.timestamp))

            containerMealsList.addView(itemView)
        }
    }

    private fun loadFoodMemory() {
        lifecycleScope.launch {
            val records = engine.getFoodMemory()
            renderFoodMemory(records)
        }
    }

    private fun renderFoodMemory(records: List<FoodMemoryRecord>) {
        containerFoodMemoryList.removeAllViews()

        if (records.isEmpty()) {
            txtEmptyFoodMemory.visibility = View.VISIBLE
            containerFoodMemoryList.visibility = View.GONE
            return
        }

        txtEmptyFoodMemory.visibility = View.GONE
        containerFoodMemoryList.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        for (record in records) {
            val itemView = inflater.inflate(R.layout.item_dashboard_food_memory, containerFoodMemoryList, false)
            val txtIcon: TextView = itemView.findViewById(R.id.txtMemoryIcon)
            val txtName: TextView = itemView.findViewById(R.id.txtMemoryFoodName)
            val txtSubtext: TextView = itemView.findViewById(R.id.txtMemorySubtext)
            val txtPortion: TextView = itemView.findViewById(R.id.txtMemoryUsualPortion)
            val txtCount: TextView = itemView.findViewById(R.id.txtMemoryCount)

            txtIcon.text = getFoodEmoji(record.foodName)
            txtName.text = record.foodName
            txtSubtext.text = "Based on your previous meals"
            txtPortion.text = "Usual: ~${record.averagePortionGrams.toInt()}g"
            txtCount.text = "${record.totalTimesEaten} ${if (record.totalTimesEaten == 1) "meal" else "meals"} recorded"

            containerFoodMemoryList.addView(itemView)
        }
    }

    private fun getFoodEmoji(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("biryani") -> "🍛"
            lower.contains("dosa") -> "🥞"
            lower.contains("bhatura") -> "🫓"
            lower.contains("poha") -> "🥣"
            lower.contains("chicken") -> "🍗"
            lower.contains("paneer") -> "🧀"
            lower.contains("tikki") || lower.contains("kachori") || lower.contains("samosa") -> "🥟"
            lower.contains("paniyaram") -> "🍘"
            lower.contains("jalebi") || lower.contains("jamun") || lower.contains("sweet") || lower.contains("modak") -> "🍬"
            lower.contains("roti") || lower.contains("chapati") || lower.contains("naan") -> "🫓"
            lower.contains("dal") -> "🍲"
            lower.contains("salad") -> "🥗"
            else -> "🍽️"
        }
    }
}