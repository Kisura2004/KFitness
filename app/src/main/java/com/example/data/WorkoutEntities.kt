package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_logs")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exerciseName: String,
    val weight: Double, // in kg
    val reps: Int,
    val sets: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val category: String = ""
)

@Entity(tableName = "personal_records")
data class PersonalRecord(
    @PrimaryKey val exerciseName: String,
    val maxWeight: Double, // in kg
    val reps: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val weight: Double, // in kg
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "meal_logs")
data class MealLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val calories: Int,
    val protein: Double, // in g
    val carbs: Double,   // in g
    val fats: Double,    // in g
    val timestamp: Long = System.currentTimeMillis(),
    val mealType: String = "Breakfast" // E.g., Breakfast, Lunch, Dinner, Snack
)

