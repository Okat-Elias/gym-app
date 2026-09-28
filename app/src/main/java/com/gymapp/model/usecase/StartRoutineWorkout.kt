package com.gymapp.model.usecase

import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import java.util.UUID

/** Copies the plan, allocating new identities so subsequent sessions remain independent. */
class StartRoutineWorkout {
    operator fun invoke(
        routine: Routine,
        startedAtEpochMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ) = WorkoutSession(
        id = newId(),
        routineId = routine.id,
        startedAtEpochMillis = startedAtEpochMillis,
        exercises = routine.exercises.sortedBy { it.orderIndex }.mapIndexed { index, exercise ->
            WorkoutExercise(
                id = newId(),
                exerciseId = exercise.exerciseId,
                orderIndex = index,
                supersetGroupId = exercise.supersetGroupId,
            )
        },
    )
}
