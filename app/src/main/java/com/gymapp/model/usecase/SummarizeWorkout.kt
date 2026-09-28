package com.gymapp.model.usecase

import com.gymapp.model.domain.WorkoutSession

data class WorkoutSummary(
    val durationMillis: Long,
    val completedSets: Int,
    val repetitions: Long,
    val tonnageGrams: Long,
    val performedExercises: Int,
)

/** Derived from persisted sets and timestamps: no redundant totals that could become stale. */
class SummarizeWorkout {
    operator fun invoke(workout: WorkoutSession, nowEpochMillis: Long): WorkoutSummary {
        val sets = workout.exercises.flatMap { it.sets }.filter { it.completed }
        return WorkoutSummary(
            durationMillis = ((workout.endedAtEpochMillis ?: nowEpochMillis) -
                workout.startedAtEpochMillis).coerceAtLeast(0),
            completedSets = sets.size,
            repetitions = sets.sumOf { it.repetitions.toLong() },
            tonnageGrams = sets.fold(0L) { total, set ->
                Math.addExact(total, Math.multiplyExact(set.weightGrams, set.repetitions.toLong()))
            },
            performedExercises = workout.exercises.count { exercise ->
                exercise.sets.any { it.completed }
            },
        )
    }
}
