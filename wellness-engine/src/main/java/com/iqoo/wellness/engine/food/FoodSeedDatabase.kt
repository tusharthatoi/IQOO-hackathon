package com.iqoo.wellness.engine.food

import com.iqoo.wellness.engine.storage.FoodEntity

object FoodSeedDatabase {
    val SEED_FOODS: List<FoodEntity> = listOf(
        FoodEntity(
            foodId = "biryani_01",
            name = "Biryani",
            category = "Rice Dishes",
            servingUnit = "plate",
            defaultGrams = 250.0,
            caloriesPer100g = 165.0,
            proteinPer100g = 7.5,
            carbsPer100g = 24.0,
            fatPer100g = 4.5,
            fiberPer100g = 1.2
        ),
        FoodEntity(
            foodId = "dosa_01",
            name = "Dosa",
            category = "South Indian",
            servingUnit = "piece",
            defaultGrams = 150.0,
            caloriesPer100g = 168.0,
            proteinPer100g = 3.9,
            carbsPer100g = 29.0,
            fatPer100g = 3.7,
            fiberPer100g = 1.4
        ),
        FoodEntity(
            foodId = "paneer_tikka_01",
            name = "Paneer Tikka",
            category = "Starters",
            servingUnit = "portion",
            defaultGrams = 180.0,
            caloriesPer100g = 240.0,
            proteinPer100g = 14.0,
            carbsPer100g = 6.0,
            fatPer100g = 18.0,
            fiberPer100g = 1.0
        ),
        FoodEntity(
            foodId = "roti_01",
            name = "Roti",
            category = "Breads",
            servingUnit = "piece",
            defaultGrams = 40.0,
            caloriesPer100g = 264.0,
            proteinPer100g = 9.0,
            carbsPer100g = 55.0,
            fatPer100g = 3.0,
            fiberPer100g = 9.0
        ),
        FoodEntity(
            foodId = "dal_tadka_01",
            name = "Dal Tadka",
            category = "Lentils",
            servingUnit = "bowl",
            defaultGrams = 200.0,
            caloriesPer100g = 110.0,
            proteinPer100g = 6.0,
            carbsPer100g = 14.0,
            fatPer100g = 3.5,
            fiberPer100g = 4.0
        ),
        FoodEntity(
            foodId = "chicken_curry_01",
            name = "Chicken Curry",
            category = "Main Course",
            servingUnit = "bowl",
            defaultGrams = 220.0,
            caloriesPer100g = 145.0,
            proteinPer100g = 16.0,
            carbsPer100g = 4.0,
            fatPer100g = 7.5,
            fiberPer100g = 1.0
        ),
        FoodEntity(
            foodId = "salad_01",
            name = "Green Salad",
            category = "Salads",
            servingUnit = "bowl",
            defaultGrams = 150.0,
            caloriesPer100g = 35.0,
            proteinPer100g = 1.5,
            carbsPer100g = 7.0,
            fatPer100g = 0.5,
            fiberPer100g = 2.5
        )
    )
}
