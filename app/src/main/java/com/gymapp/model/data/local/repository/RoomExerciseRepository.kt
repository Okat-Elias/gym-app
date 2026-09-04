package com.gymapp.model.data.local.repository

import com.gymapp.model.data.local.dao.ExerciseDao
import com.gymapp.model.data.local.mapper.toDomain
import com.gymapp.model.data.local.mapper.toEntity
import com.gymapp.model.domain.Exercise
import com.gymapp.model.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomExerciseRepository(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {
    override fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeExercises().map { records ->
            records.map { it.toDomain() }
        }

    override fun observeExercise(exerciseId: String): Flow<Exercise?> =
        exerciseDao.observeExercise(exerciseId).map { it?.toDomain() }

    override suspend fun getExercise(exerciseId: String): Exercise? =
        exerciseDao.getExercise(exerciseId)?.toDomain()

    override suspend fun saveExercise(exercise: Exercise) {
        exerciseDao.replaceExercise(
            exercise = exercise.toEntity(),
            muscleTargets = exercise.muscleTargets.map { it.toEntity(exercise.id) },
        )
    }

    override suspend fun deleteExercise(exerciseId: String) {
        exerciseDao.deleteExercise(exerciseId)
    }
}
