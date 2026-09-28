package com.gymapp.model.repository

import com.gymapp.model.domain.Routine
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    suspend fun saveRoutine(routine: Routine)
}
