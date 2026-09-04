package com.iqoo.wellness.engine.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores deep preparation, ingredient, and cooking context learned from user confirmations/corrections.
 * This context is retrieved locally to personalize nutrition calculations without model retraining.
 */
@Entity(
    tableName = "food_preparation_contexts",
    indices = [
        Index(value = ["food_id"]),
        Index(value = ["food_name"])
    ]
)
data class FoodPreparationContextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "food_name")
    val foodName: String,

    @ColumnInfo(name = "typical_portion_grams")
    val typicalPortionGrams: Double,

    @ColumnInfo(name = "user_confirmed_quantity")
    val userConfirmedQuantity: Double,

    /**
     * Serialized key-value pairs of ingredients and quantities in grams,
     * e.g. '{"rice_g": 180.0, "chicken_g": 70.0, "fried_onions": 15.0}'
     */
    @ColumnInfo(name = "ingredient_quantities_json")
    val ingredientQuantitiesJson: String? = null,

    /**
     * Cooking / preparation method, e.g. "Pressure-cooked", "Dum / Steamed", "Deep-fried", "Tandoori / Grilled"
     */
    @ColumnInfo(name = "cooking_method")
    val cookingMethod: String? = null,

    /**
     * Oil / fat usage level: "NONE", "LIGHT", "MODERATE", "HEAVY"
     */
    @ColumnInfo(name = "oil_fat_level")
    val oilFatLevel: String? = null,

    /**
     * Explicit oil / fat quantity in grams if known, e.g. 15.0g
     */
    @ColumnInfo(name = "oil_grams")
    val oilGrams: Double? = null,

    /**
     * Frying / cooking intensity: "NONE", "PAN_FRIED", "DEEP_FRIED", "AIR_FRIED", "ROASTED"
     */
    @ColumnInfo(name = "frying_method")
    val fryingMethod: String? = null,

    /**
     * Ingredient substitutions, e.g. '{"cooking_fat": "olive_oil", "grain": "brown_rice"}'
     */
    @ColumnInfo(name = "substitutions_json")
    val substitutionsJson: String? = null,

    /**
     * User corrections and adjustments history notes
     */
    @ColumnInfo(name = "user_corrections")
    val userCorrections: String? = null,

    /**
     * Frequency / recurrence count of this dish
     */
    @ColumnInfo(name = "recurrence_count")
    val recurrenceCount: Int = 1,

    /**
     * Previous computed nutrition estimate in calories
     */
    @ColumnInfo(name = "last_calories_estimate")
    val lastCaloriesEstimate: Double? = null,

    @ColumnInfo(name = "last_protein_estimate")
    val lastProteinEstimate: Double? = null,

    @ColumnInfo(name = "last_carbs_estimate")
    val lastCarbsEstimate: Double? = null,

    @ColumnInfo(name = "last_fat_estimate")
    val lastFatEstimate: Double? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
