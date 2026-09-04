package com.gymapp.model.repository

import com.gymapp.model.domain.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>

    fun observeExercise(exerciseId: String): Flow<Exercise?>

    suspend fun getExercise(exerciseId: String): Exercise?

    suspend fun saveExercise(exercise: Exercise)

    suspend fun deleteExercise(exerciseId: String)
}
