package com.gymapp.di

import android.content.Context
import androidx.room.Room
import com.gymapp.model.data.local.GymDatabase
import com.gymapp.model.data.local.repository.RoomExerciseRepository
import com.gymapp.model.data.local.repository.RoomWorkoutRepository
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.domain.MuscleTarget
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-level composition root. Kept manual until the dependency graph justifies Hilt. */
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        GymDatabase::class.java,
        DATABASE_NAME,
    ).build()

    val exerciseRepository: ExerciseRepository = RoomExerciseRepository(database.exerciseDao())
    val workoutRepository: WorkoutRepository = RoomWorkoutRepository(database.workoutDao())

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        applicationScope.launch { seedExerciseCatalogIfNeeded() }
    }

    private suspend fun seedExerciseCatalogIfNeeded() {
        DEFAULT_EXERCISES.forEach { exercise ->
            if (exerciseRepository.getExercise(exercise.id) == null) {
                exerciseRepository.saveExercise(exercise)
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "gym_app.db"

        val DEFAULT_EXERCISES = listOf(
            Exercise(
                id = "barbell-bench-press",
                name = "Développé couché barre",
                muscleTargets = listOf(
                    MuscleTarget(MuscleGroup.CHEST, 10_000),
                    MuscleTarget(MuscleGroup.TRICEPS, 5_000),
                    MuscleTarget(MuscleGroup.SHOULDERS, 5_000),
                ),
            ),
            Exercise(
                id = "lat-pulldown",
                name = "Tirage vertical",
                muscleTargets = listOf(
                    MuscleTarget(MuscleGroup.BACK, 10_000),
                    MuscleTarget(MuscleGroup.BICEPS, 5_000),
                ),
            ),
            Exercise(
                id = "barbell-squat",
                name = "Squat barre",
                muscleTargets = listOf(
                    MuscleTarget(MuscleGroup.QUADRICEPS, 10_000),
                    MuscleTarget(MuscleGroup.GLUTES, 5_000),
                ),
            ),
            Exercise(
                id = "romanian-deadlift",
                name = "Soulevé de terre roumain",
                muscleTargets = listOf(
                    MuscleTarget(MuscleGroup.HAMSTRINGS, 10_000),
                    MuscleTarget(MuscleGroup.GLUTES, 5_000),
                ),
            ),
        )
    }
}

