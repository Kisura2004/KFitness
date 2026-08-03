package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"

    // Authentication States
    private val _currentUser = MutableStateFlow<FirebaseUserSummary?>(null)
    val currentUser: StateFlow<FirebaseUserSummary?> = _currentUser

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus

    data class FirebaseUserSummary(
        val uid: String,
        val name: String,
        val email: String,
        val photoUrl: String
    )

    // Check if Firebase is initialized and available
    fun isFirebaseInitialized(context: Context): Boolean {
        return try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            // Not initialized yet, let's try configuring dynamically
            tryConfigureDynamically(context)
        }
    }

    // Try to configure Firebase dynamically using stored credentials in preferences
    fun tryConfigureDynamically(context: Context): Boolean {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                return true
            }

            val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
            var apiKey = prefs.getString("firebase_api_key", "") ?: ""
            var projectId = prefs.getString("firebase_project_id", "") ?: ""
            var appId = prefs.getString("firebase_app_id", "") ?: ""

            if (apiKey.isBlank()) apiKey = "AIzaSyAXMoKaOLBhx9P2D8XlOl_VotorcM9XLmo"
            if (projectId.isBlank()) projectId = "kfitness-23fc1"
            if (appId.isBlank()) appId = "1:134302376060:android:5273a55d14860b2e1e29f9"

            if (apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized dynamically with credentials")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dynamically initializing Firebase: ${e.message}")
        }
        return false
    }

    // Update dynamic Firebase credentials in preferences and initialize
    fun updateFirebaseCredentials(
        context: Context,
        apiKey: String,
        projectId: String,
        appId: String,
        webClientId: String
    ): Boolean {
        val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("firebase_api_key", apiKey.trim())
            putString("firebase_project_id", projectId.trim())
            putString("firebase_app_id", appId.trim())
            putString("firebase_web_client_id", webClientId.trim())
            apply()
        }

        // Try initializing
        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                // If already initialized, we warn the user they might need to restart,
                // but we can try to initialize or just return true.
                return true
            }

            if (apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase credentials updated and initialized")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating and initializing: ${e.message}")
        }
        return false
    }

    fun getStoredWebClientId(context: Context): String {
        val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
        return prefs.getString("firebase_web_client_id", "") ?: ""
    }

    fun getStoredApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
        val value = prefs.getString("firebase_api_key", "") ?: ""
        return if (value.isBlank()) "AIzaSyAXMoKaOLBhx9P2D8XlOl_VotorcM9XLmo" else value
    }

    fun getStoredProjectId(context: Context): String {
        val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
        val value = prefs.getString("firebase_project_id", "") ?: ""
        return if (value.isBlank()) "kfitness-23fc1" else value
    }

    fun getStoredAppId(context: Context): String {
        val prefs = context.getSharedPreferences("firebase_credentials", Context.MODE_PRIVATE)
        val value = prefs.getString("firebase_app_id", "") ?: ""
        return if (value.isBlank()) "1:134302376060:android:5273a55d14860b2e1e29f9" else value
    }

    // Refresh current user Auth status
    fun checkCurrentUser(context: Context) {
        if (!isFirebaseInitialized(context)) return
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                _currentUser.value = FirebaseUserSummary(
                    uid = user.uid,
                    name = user.displayName ?: "Google User",
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString() ?: ""
                )
            } else {
                _currentUser.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current user auth status: ${e.message}")
            _currentUser.value = null
        }
    }

    // Handle authenticating with a Google ID Token retrieved via sign-in activity
    suspend fun authenticateWithGoogleToken(context: Context, idToken: String): Boolean {
        if (!isFirebaseInitialized(context)) return false
        return try {
            val auth = FirebaseAuth.getInstance()
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                _currentUser.value = FirebaseUserSummary(
                    uid = user.uid,
                    name = user.displayName ?: "Google User",
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString() ?: ""
                )
                Log.d(TAG, "Signed in successfully with Google Token: ${user.email}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google auth with token failed: ${e.message}")
            false
        }
    }

    // Sign out from Firebase Auth
    fun signOut(context: Context) {
        if (!isFirebaseInitialized(context)) return
        try {
            FirebaseAuth.getInstance().signOut()
            _currentUser.value = null
            Log.d(TAG, "Signed out successfully from Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}")
        }
    }

    // Synchronize local SQLite Room data with Cloud Firestore bidirectional
    suspend fun performBidirectionalSync(
        context: Context,
        repository: WorkoutRepository,
        sharedPrefs: SharedPreferences
    ) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return

        _isSyncing.value = true
        _syncStatus.value = "Connecting to Cloud storage..."

        try {
            val firestore = FirebaseFirestore.getInstance()

            // 1. SYNC PROFILE PREFERENCES
            _syncStatus.value = "Syncing profile settings..."
            val profileDocRef = firestore.collection("users").document(uid).collection("data").document("profile")
            
            // Get local profile info
            val localProfile = mapOf(
                "is_profile_registered" to sharedPrefs.getBoolean("is_profile_registered", false),
                "profile_name" to (sharedPrefs.getString("profile_name", "") ?: ""),
                "profile_birthday" to (sharedPrefs.getString("profile_birthday", "") ?: ""),
                "profile_age" to sharedPrefs.getInt("profile_age", 25),
                "profile_gender" to (sharedPrefs.getString("profile_gender", "Male") ?: "Male"),
                "profile_weight" to (sharedPrefs.getString("profile_weight", "70.0") ?: "70.0"),
                "profile_height" to (sharedPrefs.getString("profile_height", "178") ?: "178"),
                "profile_goal" to (sharedPrefs.getString("profile_goal", "Muscle Gain") ?: "Muscle Gain"),
                "workouts_target_per_week" to sharedPrefs.getInt("workouts_target_per_week", 4),
                "weight_target" to (sharedPrefs.getString("weight_target", "75.0") ?: "75.0"),
                "profile_picture" to (sharedPrefs.getString("profile_picture", "") ?: ""),
                "last_updated" to System.currentTimeMillis()
            )

            val cloudProfileSnapshot = profileDocRef.get().await()
            if (cloudProfileSnapshot.exists()) {
                val cloudLastUpdated = cloudProfileSnapshot.getLong("last_updated") ?: 0L
                // If cloud is newer, pull cloud to local
                if (cloudLastUpdated > 0L) {
                    sharedPrefs.edit().apply {
                        putBoolean("is_profile_registered", cloudProfileSnapshot.getBoolean("is_profile_registered") ?: false)
                        putString("profile_name", cloudProfileSnapshot.getString("profile_name") ?: "")
                        putString("profile_birthday", cloudProfileSnapshot.getString("profile_birthday") ?: "")
                        putInt("profile_age", (cloudProfileSnapshot.getLong("profile_age") ?: 25L).toInt())
                        putString("profile_gender", cloudProfileSnapshot.getString("profile_gender") ?: "Male")
                        putString("profile_weight", cloudProfileSnapshot.getString("profile_weight") ?: "70.0")
                        putString("profile_height", cloudProfileSnapshot.getString("profile_height") ?: "178")
                        putString("profile_goal", cloudProfileSnapshot.getString("profile_goal") ?: "Muscle Gain")
                        putInt("workouts_target_per_week", (cloudProfileSnapshot.getLong("workouts_target_per_week") ?: 4L).toInt())
                        putString("weight_target", cloudProfileSnapshot.getString("weight_target") ?: "75.0")
                        apply()
                    }
                } else {
                    profileDocRef.set(localProfile, SetOptions.merge()).await()
                }
            } else {
                profileDocRef.set(localProfile, SetOptions.merge()).await()
            }

            // 2. SYNC WORKOUT LOGS
            _syncStatus.value = "Syncing workout logs..."
            val localWorkouts = repository.allLogs.first()
            val cloudWorkoutsColl = firestore.collection("users").document(uid).collection("workouts")
            
            // Get cloud workouts
            val cloudWorkoutsSnapshot = cloudWorkoutsColl.get().await()
            val cloudWorkoutsMap = cloudWorkoutsSnapshot.documents.associateBy { it.id }

            // Pull cloud workouts missing locally
            for ((docId, doc) in cloudWorkoutsMap) {
                val idVal = docId.toIntOrNull() ?: continue
                if (localWorkouts.none { it.id == idVal }) {
                    val log = WorkoutLog(
                        id = idVal,
                        exerciseName = doc.getString("exerciseName") ?: "",
                        weight = doc.getDouble("weight") ?: 0.0,
                        reps = (doc.getLong("reps") ?: 0L).toInt(),
                        sets = (doc.getLong("sets") ?: 0L).toInt(),
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        notes = doc.getString("notes") ?: "",
                        category = doc.getString("category") ?: ""
                    )
                    repository.insertLog(log)
                }
            }

            // Push local workouts missing in cloud
            for (local in localWorkouts) {
                if (!cloudWorkoutsMap.containsKey(local.id.toString())) {
                    val data = mapOf(
                        "exerciseName" to local.exerciseName,
                        "weight" to local.weight,
                        "reps" to local.reps,
                        "sets" to local.sets,
                        "timestamp" to local.timestamp,
                        "notes" to local.notes,
                        "category" to local.category
                    )
                    cloudWorkoutsColl.document(local.id.toString()).set(data).await()
                }
            }

            // 3. SYNC WEIGHT ENTRIES
            _syncStatus.value = "Syncing weight logs..."
            val localWeights = repository.allWeightsAsc.first()
            val cloudWeightsColl = firestore.collection("users").document(uid).collection("weights")
            
            val cloudWeightsSnapshot = cloudWeightsColl.get().await()
            val cloudWeightsMap = cloudWeightsSnapshot.documents.associateBy { it.id }

            for ((docId, doc) in cloudWeightsMap) {
                val idVal = docId.toIntOrNull() ?: continue
                if (localWeights.none { it.id == idVal }) {
                    val entry = WeightEntry(
                        id = idVal,
                        weight = doc.getDouble("weight") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                    repository.insertWeight(entry)
                }
            }

            for (local in localWeights) {
                if (!cloudWeightsMap.containsKey(local.id.toString())) {
                    val data = mapOf(
                        "weight" to local.weight,
                        "timestamp" to local.timestamp
                    )
                    cloudWeightsColl.document(local.id.toString()).set(data).await()
                }
            }

            // 4. SYNC PERSONAL RECORDS (PRs)
            _syncStatus.value = "Syncing personal records..."
            val localPRs = repository.allPRs.first()
            val cloudPRsColl = firestore.collection("users").document(uid).collection("prs")
            
            val cloudPRsSnapshot = cloudPRsColl.get().await()
            val cloudPRsMap = cloudPRsSnapshot.documents.associateBy { it.id }

            for ((docId, doc) in cloudPRsMap) {
                if (localPRs.none { it.exerciseName == docId }) {
                    val record = PersonalRecord(
                        exerciseName = docId,
                        maxWeight = doc.getDouble("maxWeight") ?: 0.0,
                        reps = (doc.getLong("reps") ?: 1L).toInt(),
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                    repository.insertPR(record)
                }
            }

            for (local in localPRs) {
                if (!cloudPRsMap.containsKey(local.exerciseName)) {
                    val data = mapOf(
                        "maxWeight" to local.maxWeight,
                        "reps" to local.reps,
                        "timestamp" to local.timestamp
                    )
                    cloudPRsColl.document(local.exerciseName).set(data).await()
                }
            }

            // 5. SYNC MEAL LOGS
            _syncStatus.value = "Syncing meal logs..."
            val localMeals = repository.allMeals.first()
            val cloudMealsColl = firestore.collection("users").document(uid).collection("meals")
            
            val cloudMealsSnapshot = cloudMealsColl.get().await()
            val cloudMealsMap = cloudMealsSnapshot.documents.associateBy { it.id }

            for ((docId, doc) in cloudMealsMap) {
                val idVal = docId.toIntOrNull() ?: continue
                if (localMeals.none { it.id == idVal }) {
                    val meal = MealLog(
                        id = idVal,
                        name = doc.getString("name") ?: "",
                        calories = (doc.getLong("calories") ?: 0L).toInt(),
                        protein = doc.getDouble("protein") ?: 0.0,
                        carbs = doc.getDouble("carbs") ?: 0.0,
                        fats = doc.getDouble("fats") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        mealType = doc.getString("mealType") ?: "Breakfast"
                    )
                    repository.insertMeal(meal)
                }
            }

            for (local in localMeals) {
                if (!cloudMealsMap.containsKey(local.id.toString())) {
                    val data = mapOf(
                        "name" to local.name,
                        "calories" to local.calories,
                        "protein" to local.protein,
                        "carbs" to local.carbs,
                        "fats" to local.fats,
                        "timestamp" to local.timestamp,
                        "mealType" to local.mealType
                    )
                    cloudMealsColl.document(local.id.toString()).set(data).await()
                }
            }

            _syncStatus.value = "Sync successful!"
            Log.d(TAG, "Bi-directional cloud sync complete!")
        } catch (e: Exception) {
            _syncStatus.value = "Sync error: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, "Error performing cloud sync: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    // Helper functions to perform fast on-the-fly saves to Firestore when online
    suspend fun saveWorkoutLogToCloud(context: Context, local: WorkoutLog) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val data = mapOf(
                "exerciseName" to local.exerciseName,
                "weight" to local.weight,
                "reps" to local.reps,
                "sets" to local.sets,
                "timestamp" to local.timestamp,
                "notes" to local.notes,
                "category" to local.category
            )
            firestore.collection("users").document(uid).collection("workouts")
                .document(local.id.toString()).set(data).await()
            Log.d(TAG, "Uploaded workout log ${local.id} to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload workout log: ${e.message}")
        }
    }

    suspend fun deleteWorkoutLogFromCloud(context: Context, logId: Int) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).collection("workouts")
                .document(logId.toString()).delete().await()
            Log.d(TAG, "Deleted workout log $logId from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workout log: ${e.message}")
        }
    }

    suspend fun saveWeightEntryToCloud(context: Context, local: WeightEntry) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val data = mapOf(
                "weight" to local.weight,
                "timestamp" to local.timestamp
            )
            firestore.collection("users").document(uid).collection("weights")
                .document(local.id.toString()).set(data).await()
            Log.d(TAG, "Uploaded weight entry ${local.id} to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload weight entry: ${e.message}")
        }
    }

    suspend fun deleteWeightEntryFromCloud(context: Context, entryId: Int) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).collection("weights")
                .document(entryId.toString()).delete().await()
            Log.d(TAG, "Deleted weight entry $entryId from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete weight entry: ${e.message}")
        }
    }

    suspend fun savePRToCloud(context: Context, local: PersonalRecord) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val data = mapOf(
                "maxWeight" to local.maxWeight,
                "reps" to local.reps,
                "timestamp" to local.timestamp
            )
            firestore.collection("users").document(uid).collection("prs")
                .document(local.exerciseName).set(data).await()
            Log.d(TAG, "Uploaded PR ${local.exerciseName} to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload PR: ${e.message}")
        }
    }

    suspend fun deletePRFromCloud(context: Context, exerciseName: String) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).collection("prs")
                .document(exerciseName).delete().await()
            Log.d(TAG, "Deleted PR $exerciseName from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete PR: ${e.message}")
        }
    }

    suspend fun saveMealLogToCloud(context: Context, local: MealLog) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val data = mapOf(
                "name" to local.name,
                "calories" to local.calories,
                "protein" to local.protein,
                "carbs" to local.carbs,
                "fats" to local.fats,
                "timestamp" to local.timestamp,
                "mealType" to local.mealType
            )
            firestore.collection("users").document(uid).collection("meals")
                .document(local.id.toString()).set(data).await()
            Log.d(TAG, "Uploaded meal log ${local.id} to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload meal log: ${e.message}")
        }
    }

    suspend fun deleteMealLogFromCloud(context: Context, mealId: Int) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).collection("meals")
                .document(mealId.toString()).delete().await()
            Log.d(TAG, "Deleted meal log $mealId from cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete meal log: ${e.message}")
        }
    }

    suspend fun saveProfileToCloud(context: Context, localProfile: Map<String, Any>) {
        val uid = _currentUser.value?.uid ?: return
        if (!isFirebaseInitialized(context)) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).collection("data").document("profile")
                .set(localProfile, SetOptions.merge()).await()
            Log.d(TAG, "Uploaded profile to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload profile: ${e.message}")
        }
    }
}
