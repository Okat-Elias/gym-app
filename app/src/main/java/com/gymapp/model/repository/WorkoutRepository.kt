package com.gymapp.model.repository

import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<WorkoutSession>>

    fun observeWorkout(workoutId: String): Flow<WorkoutSession?>

    suspend fun getWorkout(workoutId: String): WorkoutSession?

    suspend fun saveWorkout(workout: WorkoutSession)

    suspend fun saveWorkoutSet(workoutExerciseId: String, workoutSet: WorkoutSet)

    suspend fun deleteWorkoutSet(workoutSetId: String)

    suspend fun deleteWorkout(workoutId: String)
}
