package com.gymapp.model.data.local.repository

import com.gymapp.model.data.local.dao.RoutineDao
import com.gymapp.model.data.local.entity.RoutineEntity
import com.gymapp.model.data.local.entity.RoutineExerciseEntity
import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.RoutineExercise
import com.gymapp.model.repository.RoutineRepository
import kotlinx.coroutines.flow.map

class RoomRoutineRepository(private val dao: RoutineDao) : RoutineRepository {
    override fun observeRoutines() = dao.observeRoutines().map { records ->
        records.map { record ->
            Routine(
                id = record.routine.id,
                name = record.routine.name,
                notes = record.routine.notes,
                createdAtEpochMillis = record.routine.createdAtEpochMillis,
                updatedAtEpochMillis = record.routine.updatedAtEpochMillis,
                exercises = record.exercises.sortedBy { it.orderIndex }.map {
                    RoutineExercise(it.id, it.exerciseId, it.orderIndex, it.supersetGroupId)
                },
            )
        }
    }

    override suspend fun saveRoutine(routine: Routine) {
        dao.replaceRoutine(
            RoutineEntity(
                id = routine.id,
                name = routine.name,
                notes = routine.notes,
                createdAtEpochMillis = routine.createdAtEpochMillis,
                updatedAtEpochMillis = routine.updatedAtEpochMillis,
            ),
            routine.exercises.map {
                RoutineExerciseEntity(it.id, routine.id, it.exerciseId, it.orderIndex, it.supersetGroupId)
            },
        )
    }

    override suspend fun deleteRoutine(routineId: String) {
        dao.deleteRoutine(routineId)
    }
}
