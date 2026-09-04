package com.gymapp.model.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gymapp.model.data.local.entity.ExerciseEntity
import com.gymapp.model.data.local.entity.MuscleTargetEntity
import com.gymapp.model.data.local.relation.ExerciseWithTargets
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseDao {
    @Transaction
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE, id")
    abstract fun observeExercises(): Flow<List<ExerciseWithTargets>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    abstract fun observeExercise(exerciseId: String): Flow<ExerciseWithTargets?>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    abstract suspend fun getExercise(exerciseId: String): ExerciseWithTargets?

    @Upsert
    abstract suspend fun upsertExercise(exercise: ExerciseEntity)

    @Upsert
    abstract suspend fun upsertMuscleTargets(targets: List<MuscleTargetEntity>)

    @Query("DELETE FROM muscle_targets WHERE exercise_id = :exerciseId")
    abstract suspend fun deleteMuscleTargets(exerciseId: String)

    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    abstract suspend fun deleteExercise(exerciseId: String)

    @Transaction
    open suspend fun replaceExercise(
        exercise: ExerciseEntity,
        muscleTargets: List<MuscleTargetEntity>,
    ) {
        require(muscleTargets.all { it.exerciseId == exercise.id }) {
            "Every muscle target must reference exercise ${exercise.id}."
        }
        require(muscleTargets.map { it.muscleGroup }.distinct().size == muscleTargets.size) {
            "An exercise cannot target the same muscle group more than once."
        }
        require(muscleTargets.all { it.contributionBasisPoints in 1..10_000 }) {
            "Muscle contribution must be between 1 and 10,000 basis points."
        }

        upsertExercise(exercise)
        deleteMuscleTargets(exercise.id)
        if (muscleTargets.isNotEmpty()) {
            upsertMuscleTargets(muscleTargets)
        }
    }
}
