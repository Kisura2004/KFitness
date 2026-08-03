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
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WorkoutRepository
    private val sharedPreferences = application.getSharedPreferences("workout_preferences", Context.MODE_PRIVATE)

    // Firebase Auth & Cloud Sync States exposed to Compose
    val firebaseUser = FirebaseSyncManager.currentUser
    val isSyncing = FirebaseSyncManager.isSyncing
    val syncStatus = FirebaseSyncManager.syncStatus

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WorkoutRepository(database.workoutDao())
        
        // Verify Firebase status on startup and trigger sync if user is logged in
        viewModelScope.launch {
            if (FirebaseSyncManager.isFirebaseInitialized(application)) {
                FirebaseSyncManager.checkCurrentUser(application)
                if (FirebaseSyncManager.currentUser.value != null) {
                    FirebaseSyncManager.performBidirectionalSync(application, repository, sharedPreferences)
                }
            }
        }
    }

    val workoutLogs: StateFlow<List<WorkoutLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mealLogs: StateFlow<List<MealLog>> = repository.allMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalRecords: StateFlow<List<PersonalRecord>> = repository.allPRs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightEntriesAsc: StateFlow<List<WeightEntry>> = repository.allWeightsAsc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestWeight: StateFlow<WeightEntry?> = repository.latestWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // User profile preferences
    val isProfileRegistered = MutableStateFlow(sharedPreferences.getBoolean("is_profile_registered", false))
    val profileName = MutableStateFlow(sharedPreferences.getString("profile_name", "") ?: "")
    val profileBirthday = MutableStateFlow(sharedPreferences.getString("profile_birthday", "") ?: "")
    val profileAge = MutableStateFlow(sharedPreferences.getInt("profile_age", 25))
    val profileGender = MutableStateFlow(sharedPreferences.getString("profile_gender", "Male") ?: "Male")
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
        gender: String,
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
            putString("profile_gender", gender.trim())
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
        profileGender.value = gender.trim()
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

        // Suggest required calories automatically based on age, gender, weight, height, and goal
        applySuggestedCalories()
        scheduleSync()
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

    val mealTrackerEnabled = MutableStateFlow(sharedPreferences.getBoolean("meal_tracker_enabled", false))

    fun toggleMealTracker() {
        val newValue = !mealTrackerEnabled.value
        mealTrackerEnabled.value = newValue
        sharedPreferences.edit().putBoolean("meal_tracker_enabled", newValue).apply()
    }

    // Nutrition targets (editable calorie and macro targets/goals)
    val calorieGoal = MutableStateFlow(sharedPreferences.getInt("calorie_goal", 2200))
    val proteinGoal = MutableStateFlow(sharedPreferences.getFloat("protein_goal", 140.0f).toDouble())
    val carbsGoal = MutableStateFlow(sharedPreferences.getFloat("carbs_goal", 250.0f).toDouble())
    val fatsGoal = MutableStateFlow(sharedPreferences.getFloat("fats_goal", 75.0f).toDouble())

    fun updateNutritionGoals(calories: Int, protein: Double, carbs: Double, fats: Double) {
        calorieGoal.value = calories
        proteinGoal.value = protein
        carbsGoal.value = carbs
        fatsGoal.value = fats
        sharedPreferences.edit().apply {
            putInt("calorie_goal", calories)
            putFloat("protein_goal", protein.toFloat())
            putFloat("carbs_goal", carbs.toFloat())
            putFloat("fats_goal", fats.toFloat())
        }.apply()
    }

    // Automatic Mifflin-St Jeor calorie & macro target estimation
    fun calculateSuggestedCalories(): SuggestedNutrition {
        val age = profileAge.value
        val gender = profileGender.value
        val weight = profileWeight.value.toDoubleOrNull() ?: 70.0
        val height = profileHeight.value.toDoubleOrNull() ?: 178.0
        val goal = profileGoal.value
        return calculateSuggestedCalories(age, gender, weight, height, goal)
    }

    fun calculateSuggestedCalories(age: Int, gender: String, weightKg: Double, heightCm: Double, goal: String): SuggestedNutrition {
        // Mifflin-St Jeor formula
        val bmr = if (gender.equals("Male", ignoreCase = true)) {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + 5.0
        } else {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 161.0
        }
        
        // Lightly active / average physical trainer multiplier
        val tdee = bmr * 1.375
        
        // Adjust calories based on goal
        val calories = when {
            goal.contains("Loss", ignoreCase = true) || goal.contains("Cut", ignoreCase = true) || goal.contains("Slim", ignoreCase = true) -> (tdee - 500.0).toInt()
            goal.contains("Gain", ignoreCase = true) || goal.contains("Bulk", ignoreCase = true) -> (tdee + 350.0).toInt()
            else -> tdee.toInt()
        }.coerceIn(1200, 5000) // healthy safety bounds
        
        // Suggest macros based on goal:
        // Protein: 2.0g per kg of bodyweight, or slightly adjusted for goals
        val protein = when {
            goal.contains("Gain", ignoreCase = true) -> (2.2 * weightKg).coerceIn(60.0, 220.0)
            goal.contains("Loss", ignoreCase = true) -> (2.0 * weightKg).coerceIn(60.0, 200.0)
            else -> (1.8 * weightKg).coerceIn(50.0, 180.0)
        }
        
        // Fats: 25% of calorie intake (1g fat = 9 kcal)
        val fats = ((calories * 0.25) / 9.0).coerceIn(40.0, 120.0)
        
        // Carbs: remaining calories (1g carb = 4 kcal)
        val remainingCalories = calories - (protein * 4.0) - (fats * 9.0)
        val carbs = (remainingCalories / 4.0).coerceAtLeast(50.0)
        
        return SuggestedNutrition(calories, protein, carbs, fats)
    }

    fun applySuggestedCalories() {
        val age = profileAge.value
        val gender = profileGender.value
        val weight = profileWeight.value.toDoubleOrNull() ?: 70.0
        val height = profileHeight.value.toDoubleOrNull() ?: 178.0
        val goal = profileGoal.value
        
        val suggested = calculateSuggestedCalories(age, gender, weight, height, goal)
        updateNutritionGoals(
            calories = suggested.calories,
            protein = suggested.protein,
            carbs = suggested.carbs,
            fats = suggested.fats
        )
    }

    // Llama configuration states
    val llmProvider = MutableStateFlow(sharedPreferences.getString("llm_provider", "Llama (OpenRouter)") ?: "Llama (OpenRouter)")
    val llmBaseUrl = MutableStateFlow(sharedPreferences.getString("llm_base_url", "https://openrouter.ai/api/v1/") ?: "https://openrouter.ai/api/v1/")
    val llmModel = MutableStateFlow(sharedPreferences.getString("llm_model", "meta-llama/llama-3.2-3b-instruct:free") ?: "meta-llama/llama-3.2-3b-instruct:free")
    val llmApiKey = MutableStateFlow(sharedPreferences.getString("llm_api_key", "") ?: "")

    fun updateLlamaConfig(provider: String, baseUrl: String, model: String, apiKey: String) {
        llmProvider.value = provider
        llmBaseUrl.value = baseUrl
        llmModel.value = model
        llmApiKey.value = apiKey
        sharedPreferences.edit().apply {
            putString("llm_provider", provider)
            putString("llm_base_url", baseUrl)
            putString("llm_model", model)
            putString("llm_api_key", apiKey)
        }.apply()
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
            scheduleSync()
        }
    }

    fun deleteWorkoutLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLog(id)
            scheduleSync()
        }
    }

    fun addWeightEntry(weight: Double) {
        viewModelScope.launch {
            val entry = WeightEntry(weight = weight, timestamp = System.currentTimeMillis())
            repository.insertWeight(entry)
            scheduleSync()
        }
    }

    fun deleteWeightEntry(id: Int) {
        viewModelScope.launch {
            repository.deleteWeight(id)
            scheduleSync()
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
            scheduleSync()
        }
    }

    fun deletePersonalRecord(name: String) {
        viewModelScope.launch {
            repository.deletePR(name)
            scheduleSync()
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

    // Meal Logging Operations
    fun addMealLog(
        name: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fats: Double,
        mealType: String = "Breakfast",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val log = MealLog(
                name = name.trim(),
                calories = calories,
                protein = protein,
                carbs = carbs,
                fats = fats,
                mealType = mealType,
                timestamp = timestamp
            )
            repository.insertMeal(log)
            scheduleSync()
        }
    }

    fun deleteMealLog(id: Int) {
        viewModelScope.launch {
            repository.deleteMeal(id)
            scheduleSync()
        }
    }

    // Helper to trigger bi-directional sync on any data modification
    fun scheduleSync() {
        viewModelScope.launch {
            if (FirebaseSyncManager.isFirebaseInitialized(getApplication())) {
                FirebaseSyncManager.performBidirectionalSync(getApplication(), repository, sharedPreferences)
            }
        }
    }

    fun authenticateWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = FirebaseSyncManager.authenticateWithGoogleToken(getApplication(), idToken)
            if (success) {
                // Trigger full sync immediately after login
                FirebaseSyncManager.performBidirectionalSync(getApplication(), repository, sharedPreferences)
                onSuccess()
            } else {
                onError("Failed to sign in with Google credentials")
            }
        }
    }

    fun triggerManualSync() {
        scheduleSync()
    }

    fun signOutFirebase() {
        FirebaseSyncManager.signOut(getApplication())
    }

    fun updateFirebaseConfig(apiKey: String, projectId: String, appId: String, webClientId: String): Boolean {
        val success = FirebaseSyncManager.updateFirebaseCredentials(getApplication(), apiKey, projectId, appId, webClientId)
        if (success) {
            scheduleSync()
        }
        return success
    }

    fun getStoredFirebaseConfig(): Map<String, String> {
        val app = getApplication<Application>()
        return mapOf(
            "apiKey" to FirebaseSyncManager.getStoredApiKey(app),
            "projectId" to FirebaseSyncManager.getStoredProjectId(app),
            "appId" to FirebaseSyncManager.getStoredAppId(app),
            "webClientId" to FirebaseSyncManager.getStoredWebClientId(app)
        )
    }

    // Gemini Food Suggestions States
    private val _suggestedMeals = MutableStateFlow<String?>(null)
    val suggestedMeals: StateFlow<String?> = _suggestedMeals.asStateFlow()

    private val _isGeneratingSuggestions = MutableStateFlow(false)
    val isGeneratingSuggestions: StateFlow<Boolean> = _isGeneratingSuggestions.asStateFlow()

    private val _suggestionError = MutableStateFlow<String?>(null)
    val suggestionError: StateFlow<String?> = _suggestionError.asStateFlow()

    fun clearSuggestions() {
        _suggestedMeals.value = null
        _suggestionError.value = null
    }

    private val geminiService by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonConverter = retrofit2.converter.moshi.MoshiConverterFactory.create(
            com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
        )

        retrofit2.Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(jsonConverter)
            .build()
            .create(GeminiApiService::class.java)
    }

    fun fetchFoodSuggestions(country: String, requirement: String) {
        viewModelScope.launch {
            _isGeneratingSuggestions.value = true
            _suggestionError.value = null
            _suggestedMeals.value = null

            val provider = llmProvider.value
            val model = llmModel.value
            val key = llmApiKey.value.trim()
            val baseUrl = llmBaseUrl.value.trim()

            val prompt = """
                You are a professional nutrition expert or dietitian in a fitness app. 
                Please suggest several healthy, standard, and highly recommendation-focused meal options/dishes (at least 4 options) specifically popular/typical for a person living in "$country".
                The suggestions must meet the following nutritional/dietary requirement/goal: "$requirement".
                
                For each suggested meal option, provide:
                1. A clear, appetizing Name (Bold).
                2. A brief, polite Description of how to prepare or why it is good for "$requirement", localized to "$country".
                3. Estimated Calories, Protein (g), Carbs (g), and Fats (g) in a clean, professional, compact style.
                
                Format the entire response in a beautifully organized structure. Keep the tone friendly, helpful, and professional. 
                Maintain concise and highly scannable outputs.
            """.trimIndent()

            if (provider == "Gemini (Default)") {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    val fallbackMeals = getPreWrittenMeals(country, requirement)
                    _suggestedMeals.value = "[Local Menu Recommendation (Gemini Key not configured)]\n\n$fallbackMeals"
                    _isGeneratingSuggestions.value = false
                    return@launch
                }

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )

                try {
                    val response = geminiService.generateContent(apiKey, request)
                    val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!textResponse.isNullOrBlank()) {
                        _suggestedMeals.value = textResponse
                    } else {
                        val fallbackMeals = getPreWrittenMeals(country, requirement)
                        _suggestedMeals.value = "[Local Menu Recommendation (Empty API Response)]\n\n$fallbackMeals"
                    }
                } catch (e: Exception) {
                    val fallbackMeals = getPreWrittenMeals(country, requirement)
                    _suggestedMeals.value = "[Local Menu Recommendation (API Connection Offline)]\n\n$fallbackMeals\n\n*(Note: Connection failed with: ${e.message})*"
                } finally {
                    _isGeneratingSuggestions.value = false
                }
            } else {
                if (key.isEmpty()) {
                    val fallbackMeals = getPreWrittenMeals(country, requirement)
                    _suggestedMeals.value = "[Local Menu Recommendation (Llama Key not configured)]\n\n$fallbackMeals"
                    _isGeneratingSuggestions.value = false
                    return@launch
                }

                val request = ChatRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = prompt))
                )

                try {
                    val authHeader = "Bearer $key"
                    val service = getLlamaService(baseUrl)
                    val response = service.generateCompletion(authHeader, request)
                    val textResponse = response.choices?.firstOrNull()?.message?.content
                    if (!textResponse.isNullOrBlank()) {
                        _suggestedMeals.value = textResponse
                    } else {
                        val fallbackMeals = getPreWrittenMeals(country, requirement)
                        _suggestedMeals.value = "[Local Menu Recommendation (Empty Llama Response)]\n\n$fallbackMeals"
                    }
                } catch (e: Exception) {
                    val fallbackMeals = getPreWrittenMeals(country, requirement)
                    _suggestedMeals.value = "[Local Menu Recommendation (Llama API Connection Offline)]\n\n$fallbackMeals\n\n*(Note: Connection failed with: ${e.message})*"
                } finally {
                    _isGeneratingSuggestions.value = false
                }
            }
        }
    }

    fun getPreWrittenMeals(country: String, requirement: String): String {
        val reqLower = requirement.lowercase()
        val countryLower = country.lowercase()

        val isIndia = countryLower.contains("india")
        val isJapan = countryLower.contains("japan")
        val isMexico = countryLower.contains("mexico")

        return when {
            reqLower.contains("loss") || reqLower.contains("cut") || reqLower.contains("lean") || reqLower.contains("slim") || reqLower.contains("fat") -> {
                if (isIndia) {
                    """
                    **Dietary Goal: Weight Loss / Fat Loss (India Localized)**
                    
                    1. **Egg White Masala Bhurji & Roti**
                       * *Description:* Fluffy egg white bhurji (scrambled eggs) prepared with diced tomatoes, onions, green chilies, and coriander, served with 1 medium-sized whole wheat roti.
                       * *Nutrients:* 310 kcal | Protein: 22g | Carbs: 24g | Fats: 8g
                    
                    2. **Grilled Tofu / Paneer Tikka Salad**
                       * *Description:* Lightly marinated paneer cubes or firm tofu grilled with bell peppers and onions, tossed in a leafy green salad with fresh lemon juice and light spices.
                       * *Nutrients:* 340 kcal | Protein: 18g | Carbs: 14g | Fats: 16g
                    
                    3. **Sprouted Moong & Pomegranate Salad**
                       * *Description:* Steamed moong sprouts mixed with freshly cut cucumbers, tomatoes, green coriander, a handful of pomegranate seeds, and chat masala. High fiber and extremely filling.
                       * *Nutrients:* 280 kcal | Protein: 14g | Carbs: 38g | Fats: 4g
                    
                    4. **Light Dal Palak with Cauliflower Rice**
                       * *Description:* A highly comforting yellow moong dal cooked with chopped spinach and fragrant Indian spices, served over a bed of grain-free cauliflower rice.
                       * *Nutrients:* 250 kcal | Protein: 13g | Carbs: 28g | Fats: 6g
                    """.trimIndent()
                } else if (isJapan) {
                    """
                    **Dietary Goal: Weight Loss / Fat Loss (Japan Localized)**
                    
                    1. **Steamed Cod with Soy-Ginger Glaze**
                       * *Description:* Fresh white cod fillet steamed with shredded ginger and green onions, lightly drizzled with low-sodium soy sauce. Served with a side of steamed broccoli.
                       * *Nutrients:* 290 kcal | Protein: 32g | Carbs: 10g | Fats: 4g
                    
                    2. **Chilled Tofu (Hiyayakko) & Edamame Salad**
                       * *Description:* Silken tofu topped with grated ginger, green onions, and bonito flakes, served alongside a cup of freshly boiled edamame pods.
                       * *Nutrients:* 310 kcal | Protein: 22g | Carbs: 18g | Fats: 12g
                    
                    3. **Miso Soup with Shimeji Mushrooms & Seaweed**
                       * *Description:* A warm and comforting miso broth packed with shimeji mushrooms, wakame seaweed, and small cubes of soft tofu. Excellent low-calorie volume meal.
                       * *Nutrients:* 150 kcal | Protein: 10g | Carbs: 14g | Fats: 3g
                    
                    4. **Grilled Chicken Yakitori & Konjac Noodles**
                       * *Description:* Two skewers of lean chicken breast grilled with salt and green onions, served over low-calorie, high-fiber shirataki (konjac) noodles.
                       * *Nutrients:* 340 kcal | Protein: 35g | Carbs: 8g | Fats: 6g
                    """.trimIndent()
                } else if (isMexico) {
                    """
                    **Dietary Goal: Weight Loss / Fat Loss (Mexico Localized)**
                    
                    1. **Grilled Chicken Breast Tacos with Corn Tortillas**
                       * *Description:* Shredded skinless grilled chicken breast in two warm corn tortillas, topped with fresh pico de gallo, shredded cabbage, and fresh salsa verde.
                       * *Nutrients:* 330 kcal | Protein: 28g | Carbs: 22g | Fats: 6g
                    
                    2. **Nopal (Cactus) Salad with Panela Cheese**
                       * *Description:* Sliced tender nopales boiled and tossed with diced tomatoes, red onion, fresh cilantro, lemon juice, and small cubes of light panela cheese.
                       * *Nutrients:* 210 kcal | Protein: 12g | Carbs: 14g | Fats: 8g
                    
                    3. **Ceviche de Pescado (Fish Ceviche)**
                       * *Description:* Fresh white fish cured in lime juice, mixed with diced tomatoes, onions, cilantro, and cucumber. Served with baked baked tostadas.
                       * *Nutrients:* 290 kcal | Protein: 24g | Carbs: 18g | Fats: 4g
                    
                    4. **Spiced Black Bean Soup**
                       * *Description:* Puréed black bean soup cooked with garlic, onions, oregano, and cumin, topped with a dollop of fat-free sour cream and cilantro.
                       * *Nutrients:* 260 kcal | Protein: 14g | Carbs: 36g | Fats: 3g
                    """.trimIndent()
                } else {
                    """
                    **Dietary Goal: Weight Loss / Fat Loss (General Healthy Options)**
                    
                    1. **Lemon Garlic Grilled Whitefish with Asparagus**
                       * *Description:* Tender whitefish fillet grilled with zest of lemon, fresh garlic, and olive oil, served alongside lightly roasted asparagus spears.
                       * *Nutrients:* 340 kcal | Protein: 34g | Carbs: 12g | Fats: 9g
                    
                    2. **Mediterranean Chickpea & Avocado Salad**
                       * *Description:* A fiber-rich salad combining boiled chickpeas, cucumbers, red onions, fresh parsley, and diced avocado tossed in fresh lime juice.
                       * *Nutrients:* 380 kcal | Protein: 12g | Carbs: 34g | Fats: 14g
                    
                    3. **Egg White Omelet with Spinach & Whole Wheat Toast**
                       * *Description:* Fluffy egg whites cooked with fresh baby spinach and mushrooms, paired with a single slice of toasted artisanal whole-wheat bread.
                       * *Nutrients:* 320 kcal | Protein: 26g | Carbs: 22g | Fats: 6g
                    
                    4. **Roasted Turkey Breast with Steamed Cauliflower Mash**
                       * *Description:* Slices of oven-roasted lean turkey breast served over a velvety mash of steamed cauliflower whipped with Greek yogurt and herbs.
                       * *Nutrients:* 360 kcal | Protein: 42g | Carbs: 14g | Fats: 8g
                    """.trimIndent()
                }
            }
            reqLower.contains("gain") || reqLower.contains("bulk") || reqLower.contains("protein") || reqLower.contains("muscle") -> {
                if (isIndia) {
                    """
                    **Dietary Goal: Muscle Gain / High Protein (India Localized)**
                    
                    1. **Tandoori Chicken Tikka / Paneer Tikka with Quinoa**
                       * *Description:* Tender chicken breast cubes or paneer marinated in protein-rich spiced yogurt and grilled to perfection. Served with a side of fluffy quinoa.
                       * *Nutrients:* 620 kcal | Protein: 42g | Carbs: 55g | Fats: 16g
                    
                    2. **High-Protein Paneer Scramble & Whole Wheat Toast**
                       * *Description:* Fresh paneer crumbled and sautéed with green peas, onions, tomatoes, turmeric, and cumin, served with two slices of toasted whole wheat bread.
                       * *Nutrients:* 540 kcal | Protein: 24g | Carbs: 48g | Fats: 18g
                    
                    3. **Soya Chunks Masala Curry with Brown Rice**
                       * *Description:* Soya chunks (high in plant protein) simmered in a spiced onion-tomato gravy, served alongside a bowl of steamed brown basmati rice.
                       * *Nutrients:* 580 kcal | Protein: 36g | Carbs: 65g | Fats: 10g
                    
                    4. **Double Dal Tadka with Steamed Egg Whites**
                       * *Description:* A comforting and thick lentil soup served with 4 boiled egg whites (yellows removed) sprinkled with black pepper and salt.
                       * *Nutrients:* 460 kcal | Protein: 32g | Carbs: 45g | Fats: 8g
                    """.trimIndent()
                } else if (isJapan) {
                    """
                    **Dietary Goal: Muscle Gain / High Protein (Japan Localized)**
                    
                    1. **Grilled Salmon Teriyaki with Steamed Brown Rice**
                       * *Description:* Fresh salmon fillet grilled with a light teriyaki glaze, paired with a generous bowl of steamed brown rice and a side of spinach ohitashi.
                       * *Nutrients:* 650 kcal | Protein: 42g | Carbs: 60g | Fats: 18g
                    
                    2. **Chicken Katsu (Oven-Baked) with Cabbage & Rice**
                       * *Description:* Crispy panko-breaded chicken breast baked in the oven to reduce fat, served with shredded cabbage and steamed white rice.
                       * *Nutrients:* 590 kcal | Protein: 44g | Carbs: 58g | Fats: 12g
                    
                    3. **Agedashi Tofu & Beef Bowl (Gyudon style)**
                       * *Description:* Thinly sliced lean beef simmered with onions in a sweet dashi broth, served over rice with cubes of protein-rich firm tofu.
                       * *Nutrients:* 680 kcal | Protein: 38g | Carbs: 72g | Fats: 18g
                    
                    4. **Edamame & Tuna Power Salad**
                       * *Description:* Flaked light tuna packed in water tossed with edamame beans, sweet corn, boiled egg, and a light sesame dressing.
                       * *Nutrients:* 450 kcal | Protein: 36g | Carbs: 22g | Fats: 14g
                    """.trimIndent()
                } else if (isMexico) {
                    """
                    **Dietary Goal: Muscle Gain / High Protein (Mexico Localized)**
                    
                    1. **Beef Fajitas with Black Beans & Brown Rice**
                       * *Description:* Lean flank beef strips seared with bell peppers and onions, served with a cup of brown rice and a side of seasoned black beans.
                       * *Nutrients:* 640 kcal | Protein: 42g | Carbs: 58g | Fats: 16g
                    
                    2. **Chipotle Grilled Chicken Breast Bowl**
                       * *Description:* Spicy chicken breast seared with chipotle, served over a high-protein quinoa base with avocado slices and sweet corn kernels.
                       * *Nutrients:* 590 kcal | Protein: 45g | Carbs: 48g | Fats: 14g
                    
                    3. **Egg White Huevos Rancheros with Cotija**
                       * *Description:* 5 egg whites cooked over baked corn tortillas, layered with spicy ranchero salsa, whole black beans, and sprinkled cotija cheese.
                       * *Nutrients:* 440 kcal | Protein: 34g | Carbs: 38g | Fats: 8g
                    
                    4. **Baked Tilapia Fillet with Lime & Cilantro Rice**
                       * *Description:* Healthy baked fish seasoned with chili powder, lime, and cilantro, served with brown rice and grilled zucchini.
                       * *Nutrients:* 520 kcal | Protein: 38g | Carbs: 50g | Fats: 10g
                    """.trimIndent()
                } else {
                    """
                    **Dietary Goal: Muscle Gain / High Protein (General Healthy Options)**
                    
                    1. **High-Protein Grilled Chicken & Quinoa Bowl**
                       * *Description:* Plump grilled chicken breast slices layered over a bed of protein-packed quinoa, roasted sweet potatoes, and steamed broccoli, dressed with olive oil.
                       * *Nutrients:* 660 kcal | Protein: 48g | Carbs: 65g | Fats: 16g
                    
                    2. **Baked Salmon Fillet with Sweet Potato & Asparagus**
                       * *Description:* Healthy omega-3 rich salmon fillet baked with herbs, served with a large baked sweet potato and a handful of steamed asparagus spears.
                       * *Nutrients:* 610 kcal | Protein: 40g | Carbs: 52g | Fats: 20g
                    
                    3. **Lean Beef & Brown Rice Stir-Fry**
                       * *Description:* Seared lean beef strips sautéed with green bell peppers, sugar snap peas, and mushrooms, tossed together with steamed whole-grain brown rice.
                       * *Nutrients:* 640 kcal | Protein: 42g | Carbs: 68g | Fats: 15g
                    
                    4. **Creamy Peanut Butter, Banana, and Oats Bowl**
                       * *Description:* Whole rolled oats cooked in milk, stirred with 2 tablespoons of natural creamy peanut butter, sliced banana, and a scoop of whey protein powder.
                       * *Nutrients:* 580 kcal | Protein: 30g | Carbs: 72g | Fats: 18g
                    """.trimIndent()
                }
            }
            else -> { // Balanced, General Health, Endurance, Consistency
                if (isIndia) {
                    """
                    **Dietary Goal: Balanced Nutrition & Maintenance (India Localized)**
                    
                    1. **Mixed Vegetable Khichdi with Curd**
                       * *Description:* A highly nutritious, easily digestible one-pot dish of rice and yellow lentils cooked with mixed vegetables, served with fresh curd (yogurt).
                       * *Nutrients:* 420 kcal | Protein: 16g | Carbs: 62g | Fats: 10g
                    
                    2. **Chana Masala with 2 Whole Wheat Rotis**
                       * *Description:* Protein-rich chickpeas cooked in a flavorful tomato-onion gravy, paired with two freshly puffed whole wheat rotis.
                       * *Nutrients:* 490 kcal | Protein: 18g | Carbs: 68g | Fats: 12g
                    
                    3. **Paneer Veggie Wrap**
                       * *Description:* Sautéed paneer strips, bell peppers, and cabbage rolled inside a whole wheat tortilla with mint chutney.
                       * *Nutrients:* 460 kcal | Protein: 20g | Carbs: 45g | Fats: 14g
                    
                    4. **Stir-Fried Tofu with Mixed Vegetables & Quinoa**
                       * *Description:* Tossed broccoli, carrots, and mushrooms with firm tofu cubes in soy-ginger sauce, served over cooked quinoa.
                       * *Nutrients:* 410 kcal | Protein: 18g | Carbs: 50g | Fats: 11g
                    """.trimIndent()
                } else {
                    """
                    **Dietary Goal: Balanced Nutrition & Maintenance (General Healthy Options)**
                    
                    1. **Classic Turkey & Swiss Cheese Whole Wheat Wrap**
                       * *Description:* Slices of lean deli turkey breast, Swiss cheese, crisp lettuce, and fresh tomatoes wrapped in an artisanal whole wheat tortilla.
                       * *Nutrients:* 450 kcal | Protein: 32g | Carbs: 38g | Fats: 12g
                    
                    2. **Seared Salmon & Quinoa Medley**
                       * *Description:* Grilled salmon fillet seasoned with lemon pepper, served over seasoned quinoa mixed with chopped spinach and cherry tomatoes.
                       * *Nutrients:* 540 kcal | Protein: 38g | Carbs: 45g | Fats: 18g
                    
                    3. **Stir-Fried Chicken & Mixed Veggie Sauté**
                       * *Description:* Diced chicken breast sautéed with bell peppers, onions, carrots, and snap peas in a light teriyaki sauce, served over a cup of steamed brown rice.
                       * *Nutrients:* 510 kcal | Protein: 36g | Carbs: 55g | Fats: 111g
                    
                    4. **Greek Yogurt Power Berry Bowl**
                       * *Description:* Thick, unsweetened Greek yogurt topped with a handful of fresh blueberries, strawberries, chia seeds, and a light drizzle of organic honey.
                       * *Nutrients:* 340 kcal | Protein: 24g | Carbs: 38g | Fats: 8g
                    """.trimIndent()
                }
            }
        }
    }

    private fun getLlamaService(customBaseUrl: String): LlamaApiService {
        val formattedBaseUrl = if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonConverter = retrofit2.converter.moshi.MoshiConverterFactory.create(
            com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
        )

        return retrofit2.Retrofit.Builder()
            .baseUrl(formattedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(jsonConverter)
            .build()
            .create(LlamaApiService::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

@JsonClass(generateAdapter = true)
data class ChatMessage(val role: String, val content: String)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

@JsonClass(generateAdapter = true)
data class ChatChoice(val message: ChatMessage)

@JsonClass(generateAdapter = true)
data class ChatResponse(val choices: List<ChatChoice>?)

interface LlamaApiService {
    @POST("chat/completions")
    suspend fun generateCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class SuggestedNutrition(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fats: Double
)
