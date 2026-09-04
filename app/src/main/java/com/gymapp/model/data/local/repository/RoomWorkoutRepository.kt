package com.gymapp.model.data.local.repository

import com.gymapp.model.data.local.dao.WorkoutDao
import com.gymapp.model.data.local.mapper.toDomain
import com.gymapp.model.data.local.mapper.toEntity
import com.gymapp.model.data.local.relation.WorkoutExerciseWithSets
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWorkoutRepository(
    private val workoutDao: WorkoutDao,
) : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<WorkoutSession>> =
        workoutDao.observeWorkouts().map { records ->
            records.map { it.toDomain() }
        }

    override fun observeWorkout(workoutId: String): Flow<WorkoutSession?> =
        workoutDao.observeWorkout(workoutId).map { it?.toDomain() }

    override suspend fun getWorkout(workoutId: String): WorkoutSession? =
        workoutDao.getWorkout(workoutId)?.toDomain()

    override suspend fun saveWorkout(workout: WorkoutSession) {
        workoutDao.replaceWorkout(
            workout = workout.toEntity(),
            exercises = workout.exercises.map { exercise ->
                WorkoutExerciseWithSets(
                    workoutExercise = exercise.toEntity(workout.id),
                    sets = exercise.sets.map { it.toEntity(exercise.id) },
                )
            },
        )
    }

    override suspend fun saveWorkoutSet(
        workoutExerciseId: String,
        workoutSet: WorkoutSet,
    ) {
        workoutDao.upsertWorkoutSet(workoutSet.toEntity(workoutExerciseId))
    }

    override suspend fun deleteWorkoutSet(workoutSetId: String) {
        workoutDao.deleteWorkoutSet(workoutSetId)
    }

    override suspend fun deleteWorkout(workoutId: String) {
        workoutDao.deleteWorkout(workoutId)
    }
}
