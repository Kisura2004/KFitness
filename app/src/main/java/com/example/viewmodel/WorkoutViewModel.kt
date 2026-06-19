package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WorkoutRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WorkoutRepository(database.workoutDao())
    }

    val workoutLogs: StateFlow<List<WorkoutLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalRecords: StateFlow<List<PersonalRecord>> = repository.allPRs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightEntriesAsc: StateFlow<List<WeightEntry>> = repository.allWeightsAsc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestWeight: StateFlow<WeightEntry?> = repository.latestWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val sharedPreferences = application.getSharedPreferences("workout_preferences", Context.MODE_PRIVATE)

    // User profile preferences
    val isProfileRegistered = MutableStateFlow(sharedPreferences.getBoolean("is_profile_registered", false))
    val profileName = MutableStateFlow(sharedPreferences.getString("profile_name", "") ?: "")
    val profileBirthday = MutableStateFlow(sharedPreferences.getString("profile_birthday", "") ?: "")
    val profileAge = MutableStateFlow(sharedPreferences.getInt("profile_age", 25))
    val profileHeight = MutableStateFlow(sharedPreferences.getString("profile_height", "178") ?: "178")
    val profileWeight = MutableStateFlow(sharedPreferences.getString("profile_weight", "70.0") ?: "70.0")
    val profileGoal = MutableStateFlow(sharedPreferences.getString("profile_goal", "Muscle Gain & Consistency") ?: "Muscle Gain & Consistency")
    val workoutsTargetPerWeek = MutableStateFlow(sharedPreferences.getInt("workouts_target_per_week", 4))
    val weightTarget = MutableStateFlow(sharedPreferences.getString("weight_target", "75.0") ?: "75.0")
    val profilePicture = MutableStateFlow(sharedPreferences.getString("profile_picture", "") ?: "")

    fun saveProfilePicture(picture: String) {
        sharedPreferences.edit().putString("profile_picture", picture).apply()
        profilePicture.value = picture
    }

    fun saveProfile(
        name: String,
        birthday: String,
        age: Int,
        weight: String,
        height: String,
        goal: String,
        targetPerWeek: Int,
        targetWeightVal: String
    ) {
        sharedPreferences.edit().apply {
            putBoolean("is_profile_registered", true)
            putString("profile_name", name.trim())
            putString("profile_birthday", birthday.trim())
            putInt("profile_age", age)
            putString("profile_weight", weight.trim())
            putString("profile_height", height.trim())
            putString("profile_goal", goal.trim())
            putInt("workouts_target_per_week", targetPerWeek)
            putString("weight_target", targetWeightVal.trim())
            apply()
        }

        isProfileRegistered.value = true
        profileName.value = name.trim()
        profileBirthday.value = birthday.trim()
        profileAge.value = age
        profileWeight.value = weight.trim()
        profileHeight.value = height.trim()
        profileGoal.value = goal.trim()
        workoutsTargetPerWeek.value = targetPerWeek
        weightTarget.value = targetWeightVal.trim()

        // Sync with the weight database automatically!
        val weightDb = weight.toDoubleOrNull()
        if (weightDb != null) {
            addWeightEntry(weightDb)
        }
    }

    fun updateWorkoutsTargetPerWeek(target: Int) {
        workoutsTargetPerWeek.value = target
        sharedPreferences.edit().putInt("workouts_target_per_week", target).apply()
    }

    // Log customization preferences
    val setTrackerEnabled = MutableStateFlow(sharedPreferences.getBoolean("set_tracker_enabled", true))
    val repTrackerEnabled = MutableStateFlow(sharedPreferences.getBoolean("rep_tracker_enabled", true))
    val timeTrackerEnabled = MutableStateFlow(sharedPreferences.getBoolean("time_tracker_enabled", true))

    fun toggleSetTracker() {
        val newValue = !setTrackerEnabled.value
        setTrackerEnabled.value = newValue
        sharedPreferences.edit().putBoolean("set_tracker_enabled", newValue).apply()
    }

    fun toggleRepTracker() {
        val newValue = !repTrackerEnabled.value
        repTrackerEnabled.value = newValue
        sharedPreferences.edit().putBoolean("rep_tracker_enabled", newValue).apply()
    }

    fun toggleTimeTracker() {
        val newValue = !timeTrackerEnabled.value
        timeTrackerEnabled.value = newValue
        sharedPreferences.edit().putBoolean("time_tracker_enabled", newValue).apply()
    }

    // Compute workout streak dynamically
    val streakFlow: StateFlow<Int> = repository.allLogs
        .map { logs -> calculateConsecutiveDaysStreak(logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun addWorkoutLog(
        exerciseName: String,
        weight: Double,
        reps: Int,
        sets: Int,
        notes: String = "",
        category: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val log = WorkoutLog(
                exerciseName = exerciseName.trim(),
                weight = weight,
                reps = reps,
                sets = sets,
                notes = notes,
                timestamp = timestamp,
                category = category.trim()
            )
            repository.insertLog(log)
            
            // Check if this workout beats or creates a personal record for the exercise
            val existingPR = personalRecords.value.find { it.exerciseName.equals(exerciseName.trim(), ignoreCase = true) }
            if (existingPR == null || weight > existingPR.maxWeight) {
                repository.insertPR(
                    PersonalRecord(
                        exerciseName = exerciseName.trim(),
                        maxWeight = weight,
                        reps = reps,
                        timestamp = timestamp
                    )
                )
            }
        }
    }

    fun deleteWorkoutLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    fun addWeightEntry(weight: Double) {
        viewModelScope.launch {
            val entry = WeightEntry(weight = weight, timestamp = System.currentTimeMillis())
            repository.insertWeight(entry)
        }
    }

    fun deleteWeightEntry(id: Int) {
        viewModelScope.launch {
            repository.deleteWeight(id)
        }
    }

    fun addPersonalRecord(exerciseName: String, weight: Double, reps: Int) {
        viewModelScope.launch {
            val record = PersonalRecord(
                exerciseName = exerciseName.trim(),
                maxWeight = weight,
                reps = reps,
                timestamp = System.currentTimeMillis()
            )
            repository.insertPR(record)
        }
    }

    fun deletePersonalRecord(name: String) {
        viewModelScope.launch {
            repository.deletePR(name)
        }
    }

    private fun calculateConsecutiveDaysStreak(logs: List<WorkoutLog>): Int {
        if (logs.isEmpty()) return 0
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        
        // Extract unique formatted dates sorted descending
        val uniqueDates = logs.map { sdf.format(Date(it.timestamp)) }
            .distinct()
            .sortedDescending()

        if (uniqueDates.isEmpty()) return 0

        val todayStr = sdf.format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(calendar.time)

        // The current streak can start from either today or yesterday.
        // If the last workout was before yesterday, the streak is 0.
        val firstDateStr = uniqueDates.first()
        if (firstDateStr != todayStr && firstDateStr != yesterdayStr) {
            return 0
        }

        var streak = 0
        val targetCalendar = Calendar.getInstance() // represents days we're checking: start checking from today
        
        // If the first gym log is yesterday, start checking sequentially from yesterday
        if (firstDateStr == yesterdayStr) {
            targetCalendar.setTime(Date()) // Reset to today
            targetCalendar.add(Calendar.DAY_OF_YEAR, -1) // Go to yesterday
        }
        
        var expectedDateStr = sdf.format(targetCalendar.time)
        
        for (dateStr in uniqueDates) {
            // Check if dateStr matches expected date
            if (dateStr == expectedDateStr) {
                streak++
                // Set target calendar to previous day
                targetCalendar.add(Calendar.DAY_OF_YEAR, -1)
                expectedDateStr = sdf.format(targetCalendar.time)
            } else if (dateStr > expectedDateStr) {
                // If we have multiple entries on the same day or somehow scrambled, skip
                continue
            } else {
                // Gap encountered, streak is broken
                break
            }
        }
        return streak
    }
}
