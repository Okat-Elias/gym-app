package com.gymapp.model.usecase

import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.domain.MuscleTarget
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.domain.WorkoutSetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateWeeklyMuscleVolumeTest {
    private val calculate = CalculateWeeklyMuscleVolume()

    @Test
    fun `counts only completed working sets with muscle coefficients`() {
        val benchPress = Exercise(
            id = "bench",
            name = "Bench press",
            muscleTargets = listOf(
                MuscleTarget(MuscleGroup.CHEST, 10_000),
                MuscleTarget(MuscleGroup.TRICEPS, 5_000),
            ),
        )
        val session = session(
            startedAt = 1_500,
            exerciseId = benchPress.id,
            sets = listOf(
                set("working-1", WorkoutSetType.WORKING, completed = true),
                set("working-2", WorkoutSetType.WORKING, completed = true),
                set("draft", WorkoutSetType.WORKING, completed = false),
                set("warm-up", WorkoutSetType.WARM_UP, completed = true),
                set("drop", WorkoutSetType.DROP_SET, completed = true),
                set("back-off", WorkoutSetType.BACK_OFF, completed = true),
            ),
        )

        val result = calculate(
            sessions = listOf(session),
            exercisesById = mapOf(benchPress.id to benchPress),
            weekStartEpochMillis = 1_000,
            weekEndExclusiveEpochMillis = 2_000,
        )

        assertEquals(20_000, result[MuscleGroup.CHEST])
        assertEquals(10_000, result[MuscleGroup.TRICEPS])
        assertEquals(2, result.size)
    }

    @Test
    fun `uses a half-open weekly interval`() {
        val squat = Exercise(
            id = "squat",
            name = "Squat",
            muscleTargets = listOf(MuscleTarget(MuscleGroup.QUADRICEPS, 10_000)),
        )
        val sessions = listOf(
            session(999, squat.id, listOf(set("before", completed = true))),
            session(1_000, squat.id, listOf(set("start", completed = true))),
            session(1_999, squat.id, listOf(set("inside", completed = true))),
            session(2_000, squat.id, listOf(set("end", completed = true))),
        )

        val result = calculate(
            sessions = sessions,
            exercisesById = mapOf(squat.id to squat),
            weekStartEpochMillis = 1_000,
            weekEndExclusiveEpochMillis = 2_000,
        )

        assertEquals(20_000, result[MuscleGroup.QUADRICEPS])
    }

    @Test
    fun `ignores stale workout exercise references`() {
        val result = calculate(
            sessions = listOf(
                session(1_500, "missing", listOf(set("set", completed = true))),
            ),
            exercisesById = emptyMap(),
            weekStartEpochMillis = 1_000,
            weekEndExclusiveEpochMillis = 2_000,
        )

        assertTrue(result.isEmpty())
    }

    private fun session(
        startedAt: Long,
        exerciseId: String,
        sets: List<WorkoutSet>,
    ) = WorkoutSession(
        id = "session-$startedAt",
        startedAtEpochMillis = startedAt,
        exercises = listOf(
            WorkoutExercise(
                id = "workout-exercise-$startedAt",
                exerciseId = exerciseId,
                orderIndex = 0,
                sets = sets.mapIndexed { index, workoutSet ->
                    workoutSet.copy(orderIndex = index)
                },
            ),
        ),
    )

    private fun set(
        id: String,
        type: WorkoutSetType = WorkoutSetType.WORKING,
        completed: Boolean,
    ) = WorkoutSet(
        id = id,
        orderIndex = 0,
        type = type,
        weightGrams = 100_000,
        repetitions = 8,
        completed = completed,
    )
}
