package com.gymapp.model.usecase

import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSetType

/**
 * Calculates effective-set volume for the half-open interval [weekStartEpochMillis,
 * weekEndExclusiveEpochMillis).
 *
 * Values are expressed in basis points where 10,000 represents one full effective set. Only
 * completed WORKING sets contribute. Missing exercise definitions are ignored so a stale
 * workout reference cannot make the complete statistics screen fail.
 */
class CalculateWeeklyMuscleVolume {
    operator fun invoke(
        sessions: Iterable<WorkoutSession>,
        exercisesById: Map<String, Exercise>,
        weekStartEpochMillis: Long,
        weekEndExclusiveEpochMillis: Long,
    ): Map<MuscleGroup, Int> {
        require(weekStartEpochMillis >= 0) { "weekStartEpochMillis must be non-negative" }
        require(weekEndExclusiveEpochMillis > weekStartEpochMillis) {
            "weekEndExclusiveEpochMillis must be after weekStartEpochMillis"
        }

        val volumeByMuscle = linkedMapOf<MuscleGroup, Int>()

        sessions
            .asSequence()
            .filter { it.startedAtEpochMillis in weekStartEpochMillis until weekEndExclusiveEpochMillis }
            .flatMap { it.exercises.asSequence() }
            .forEach workoutExerciseLoop@{ workoutExercise ->
                val exercise = exercisesById[workoutExercise.exerciseId]
                    ?: return@workoutExerciseLoop
                val completedWorkingSetCount = workoutExercise.sets.count { workoutSet ->
                    workoutSet.completed && workoutSet.type == WorkoutSetType.WORKING
                }

                if (completedWorkingSetCount == 0) return@workoutExerciseLoop

                exercise.muscleTargets.forEach { target ->
                    val contribution = Math.multiplyExact(
                        completedWorkingSetCount,
                        target.contributionBasisPoints,
                    )
                    volumeByMuscle[target.muscleGroup] = Math.addExact(
                        volumeByMuscle[target.muscleGroup] ?: 0,
                        contribution,
                    )
                }
            }

        return volumeByMuscle.toMap()
    }
}
