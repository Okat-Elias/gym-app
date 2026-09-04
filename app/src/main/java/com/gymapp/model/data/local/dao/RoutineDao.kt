package com.gymapp.model.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gymapp.model.data.local.entity.RoutineEntity
import com.gymapp.model.data.local.entity.RoutineExerciseEntity
import com.gymapp.model.data.local.relation.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY updated_at_epoch_millis DESC, id")
    abstract fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    abstract fun observeRoutine(routineId: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    abstract suspend fun getRoutine(routineId: String): RoutineWithExercises?

    @Upsert
    abstract suspend fun upsertRoutine(routine: RoutineEntity)

    @Upsert
    abstract suspend fun upsertRoutineExercise(routineExercise: RoutineExerciseEntity)

    @Upsert
    abstract suspend fun upsertRoutineExercises(routineExercises: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE id = :routineExerciseId")
    abstract suspend fun deleteRoutineExercise(routineExerciseId: String)

    @Query("DELETE FROM routine_exercises WHERE routine_id = :routineId")
    abstract suspend fun deleteRoutineExercises(routineId: String)

    @Query("DELETE FROM routines WHERE id = :routineId")
    abstract suspend fun deleteRoutine(routineId: String)

    @Transaction
    open suspend fun replaceRoutine(
        routine: RoutineEntity,
        routineExercises: List<RoutineExerciseEntity>,
    ) {
        require(routineExercises.all { it.routineId == routine.id }) {
            "Every routine exercise must reference routine ${routine.id}."
        }
        require(routineExercises.all { it.orderIndex >= 0 }) {
            "Routine exercise order indexes must be non-negative."
        }
        require(routineExercises.map { it.orderIndex }.distinct().size == routineExercises.size) {
            "Routine exercise order indexes must be unique within a routine."
        }

        upsertRoutine(routine)
        deleteRoutineExercises(routine.id)
        if (routineExercises.isNotEmpty()) {
            upsertRoutineExercises(routineExercises)
        }
    }
}
