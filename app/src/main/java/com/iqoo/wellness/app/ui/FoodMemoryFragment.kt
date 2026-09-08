package com.iqoo.wellness.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iqoo.wellness.app.MainActivity
import com.iqoo.wellness.app.R
import com.iqoo.wellness.engine.storage.DailyNutritionSummary
import com.iqoo.wellness.engine.storage.FoodHistoryEntity
import com.iqoo.wellness.engine.storage.FoodMemoryRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoodMemoryFragment : Fragment() {
    private var selectedDayOffset = 0
    private lateinit var engine: com.iqoo.wellness.engine.WellnessEngine
    private lateinit var btnToday: TextView
    private lateinit var btnYesterday: TextView
    private lateinit var btnPrevious: TextView
    private lateinit var txtDateHeader: TextView
    private lateinit var txtHeroCalories: TextView
    private lateinit var txtHeroProtein: TextView
    private lateinit var txtMacroCarbs: TextView
    private lateinit var txtMacroFat: TextView
    private lateinit var txtMacroFiber: TextView
    private lateinit var txtMacroMeals: TextView
    private lateinit var txtMealsTitle: TextView
    private lateinit var txtMealsBadge: TextView
    private lateinit var mealsContainer: LinearLayout
    private lateinit var emptyMeals: TextView
    private lateinit var memoryContainer: LinearLayout
    private lateinit var emptyMemory: TextView
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.activity_dashboard, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        engine = (requireActivity() as MainActivity).wellnessManager.engine
        btnToday = view.findViewById(R.id.btnDayToday)
        btnYesterday = view.findViewById(R.id.btnDayYesterday)
        btnPrevious = view.findViewById(R.id.btnDayPrevious)
        txtDateHeader = view.findViewById(R.id.txtDateHeader)
        txtHeroCalories = view.findViewById(R.id.txtHeroCalories)
        txtHeroProtein = view.findViewById(R.id.txtHeroProtein)
        txtMacroCarbs = view.findViewById(R.id.txtMacroCarbs)
        txtMacroFat = view.findViewById(R.id.txtMacroFat)
        txtMacroFiber = view.findViewById(R.id.txtMacroFiber)
        txtMacroMeals = view.findViewById(R.id.txtMacroMeals)
        txtMealsTitle = view.findViewById(R.id.txtMealsSectionTitle)
        txtMealsBadge = view.findViewById(R.id.txtMealsCountBadge)
        mealsContainer = view.findViewById(R.id.containerMealsList)
        emptyMeals = view.findViewById(R.id.txtEmptyMeals)
        memoryContainer = view.findViewById(R.id.containerFoodMemoryList)
        emptyMemory = view.findViewById(R.id.txtEmptyFoodMemory)

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        btnToday.setOnClickListener { selectDay(0) }
        btnYesterday.setOnClickListener { selectDay(1) }
        btnPrevious.setOnClickListener { selectDay(2) }
        updateDayPills()
        loadDay()
        loadMemory()
    }

    private fun selectDay(offset: Int) {
        if (selectedDayOffset == offset) return
        selectedDayOffset = offset
        updateDayPills()
        loadDay()
    }

    private fun updateDayPills() {
        setPill(btnToday, selectedDayOffset == 0)
        setPill(btnYesterday, selectedDayOffset == 1)
        setPill(btnPrevious, selectedDayOffset == 2)
    }

    private fun setPill(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        view.setTextColor(if (selected) Color.WHITE else Color.parseColor("#CFD8DC"))
    }

    private fun loadDay() {
        val timestamp = System.currentTimeMillis() - selectedDayOffset * 86_400_000L
        val dayName = when (selectedDayOffset) {
            0 -> "Today's"
            1 -> "Yesterday's"
            else -> "2 Days Ago"
        }
        txtDateHeader.text = "$dayName Nutrition"
        txtMealsTitle.text = "$dayName Meals"
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = engine.getDailyNutritionSummary(timestamp)
            val meals = engine.getTodayConfirmedMeals(timestamp)
            renderSummary(summary)
            renderMeals(meals)
        }
    }

    private fun renderSummary(summary: DailyNutritionSummary) {
        txtHeroCalories.text = "${summary.totalCalories.toInt()} kcal"
        txtHeroProtein.text = "${summary.totalProtein.toInt()}g protein"
        txtMacroCarbs.text = "${summary.totalCarbs.toInt()}g"
        txtMacroFat.text = "${summary.totalFat.toInt()}g"
        txtMacroFiber.text = "${summary.totalFiber.toInt()}g"
        txtMacroMeals.text = summary.mealCount.toString()
        txtMealsBadge.text = "${summary.mealCount} ${if (summary.mealCount == 1) "meal" else "meals"}"
    }

    private fun renderMeals(meals: List<FoodHistoryEntity>) {
        mealsContainer.removeAllViews()
        emptyMeals.visibility = if (meals.isEmpty()) View.VISIBLE else View.GONE
        mealsContainer.visibility = if (meals.isEmpty()) View.GONE else View.VISIBLE
        if (meals.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        meals.forEach { meal ->
            val item = inflater.inflate(R.layout.item_dashboard_meal, mealsContainer, false)
            item.findViewById<TextView>(R.id.txtMealIcon).text = foodEmoji(meal.foodName)
            item.findViewById<TextView>(R.id.txtMealName).text = meal.foodName
            item.findViewById<TextView>(R.id.txtMealPortionAndMacros).text =
                "${meal.portionGrams.toInt()}g  •  P: ${meal.protein.toInt()}g  •  C: ${meal.carbs.toInt()}g  •  F: ${meal.fat.toInt()}g"
            item.findViewById<TextView>(R.id.txtMealCalories).text = "~${meal.calories.toInt()} kcal"
            item.findViewById<TextView>(R.id.txtMealTime).text = timeFormat.format(Date(meal.timestamp))
            mealsContainer.addView(item)
        }
    }

    private fun loadMemory() {
        viewLifecycleOwner.lifecycleScope.launch { renderMemory(engine.getFoodMemory()) }
    }

    private fun renderMemory(records: List<FoodMemoryRecord>) {
        memoryContainer.removeAllViews()
        emptyMemory.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        memoryContainer.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        if (records.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        records.forEach { record ->
            val item = inflater.inflate(R.layout.item_dashboard_food_memory, memoryContainer, false)
            item.findViewById<TextView>(R.id.txtMemoryIcon).text = foodEmoji(record.foodName)
            item.findViewById<TextView>(R.id.txtMemoryFoodName).text = record.foodName
            item.findViewById<TextView>(R.id.txtMemorySubtext).text = "Based on your previous meals"
            item.findViewById<TextView>(R.id.txtMemoryUsualPortion).text = "Usual: ~${record.averagePortionGrams.toInt()}g"
            item.findViewById<TextView>(R.id.txtMemoryCount).text =
                "${record.totalTimesEaten} ${if (record.totalTimesEaten == 1) "meal" else "meals"} recorded"
            memoryContainer.addView(item)
        }
    }

    private fun foodEmoji(name: String): String {
        val value = name.lowercase(Locale.ROOT)
        return when {
            "biryani" in value -> "🍛"
            "chicken" in value -> "🍗"
            "paneer" in value -> "🧀"
            "jalebi" in value || "jamun" in value -> "🍬"
            "poha" in value -> "🥣"
            else -> "🍽️"
        }
    }
}
