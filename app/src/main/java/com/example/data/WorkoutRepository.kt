package com.example.data

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    val allLogs: Flow<List<WorkoutLog>> = workoutDao.getAllWorkoutLogs()
    val allPRs: Flow<List<PersonalRecord>> = workoutDao.getAllPersonalRecords()
    val allWeightsDesc: Flow<List<WeightEntry>> = workoutDao.getAllWeightEntriesDesc()
    val allWeightsAsc: Flow<List<WeightEntry>> = workoutDao.getAllWeightEntriesAsc()
    val latestWeight: Flow<WeightEntry?> = workoutDao.getLatestWeightEntry()
    val allMeals: Flow<List<MealLog>> = workoutDao.getAllMealLogs()

    suspend fun insertLog(log: WorkoutLog) {
        workoutDao.insertWorkoutLog(log)
    }

    suspend fun deleteLog(id: Int) {
        workoutDao.deleteWorkoutLog(id)
    }

    suspend fun insertPR(record: PersonalRecord) {
        workoutDao.insertPersonalRecord(record)
    }

    suspend fun deletePR(exerciseName: String) {
        workoutDao.deletePersonalRecord(exerciseName)
    }

    suspend fun insertWeight(entry: WeightEntry) {
        workoutDao.insertWeightEntry(entry)
    }

    suspend fun deleteWeight(id: Int) {
        workoutDao.deleteWeightEntry(id)
    }

    suspend fun insertMeal(meal: MealLog) {
        workoutDao.insertMealLog(meal)
    }

    suspend fun deleteMeal(id: Int) {
        workoutDao.deleteMealLog(id)
    }
}
