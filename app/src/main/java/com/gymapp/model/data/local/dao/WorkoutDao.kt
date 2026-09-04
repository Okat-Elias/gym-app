package com.gymapp.model.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gymapp.model.data.local.entity.WorkoutEntity
import com.gymapp.model.data.local.entity.WorkoutExerciseEntity
import com.gymapp.model.data.local.entity.WorkoutSetEntity
import com.gymapp.model.data.local.relation.WorkoutExerciseWithSets
import com.gymapp.model.data.local.relation.WorkoutWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY started_at_epoch_millis DESC, id")
    abstract fun observeWorkouts(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId LIMIT 1")
    abstract fun observeWorkout(workoutId: String): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId LIMIT 1")
    abstract suspend fun getWorkout(workoutId: String): WorkoutWithExercises?

    @Upsert
    abstract suspend fun upsertWorkout(workout: WorkoutEntity)

    @Upsert
    abstract suspend fun upsertWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Upsert
    abstract suspend fun upsertWorkoutExercises(workoutExercises: List<WorkoutExerciseEntity>)

    @Upsert
    abstract suspend fun upsertWorkoutSet(workoutSet: WorkoutSetEntity)

    @Upsert
    abstract suspend fun upsertWorkoutSets(workoutSets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets WHERE id = :workoutSetId")
    abstract suspend fun deleteWorkoutSet(workoutSetId: String)

    @Query("DELETE FROM workout_exercises WHERE id = :workoutExerciseId")
    abstract suspend fun deleteWorkoutExercise(workoutExerciseId: String)

    @Query("DELETE FROM workout_exercises WHERE workout_id = :workoutId")
    abstract suspend fun deleteWorkoutExercises(workoutId: String)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    abstract suspend fun deleteWorkout(workoutId: String)

    @Transaction
    open suspend fun replaceWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseWithSets>,
    ) {
        require(exercises.all { it.workoutExercise.workoutId == workout.id }) {
            "Every workout exercise must reference workout ${workout.id}."
        }
        require(exercises.all { it.workoutExercise.orderIndex >= 0 }) {
            "Workout exercise order indexes must be non-negative."
        }
        require(
            exercises.map { it.workoutExercise.orderIndex }.distinct().size == exercises.size,
        ) {
            "Workout exercise order indexes must be unique within a workout."
        }
        require(
            exercises.all { exercise ->
                exercise.sets.all {
                    it.workoutExerciseId == exercise.workoutExercise.id && it.orderIndex >= 0
                }
            },
        ) {
            "Every workout set must reference its workout exercise and have a non-negative order."
        }
        require(
            exercises.all { exercise ->
                exercise.sets.map { it.orderIndex }.distinct().size == exercise.sets.size
            },
        ) {
            "Workout set order indexes must be unique within an exercise."
        }

        upsertWorkout(workout)
        deleteWorkoutExercises(workout.id)
        if (exercises.isNotEmpty()) {
            upsertWorkoutExercises(exercises.map { it.workoutExercise })
            val sets = exercises.flatMap { it.sets }
            if (sets.isNotEmpty()) {
                upsertWorkoutSets(sets)
            }
        }
    }
}
