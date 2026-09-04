package com.gymapp.model.usecase

import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.domain.MuscleTarget
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import org.junit.Assert.fail
import org.junit.Test

class DomainValidationTest {
    @Test
    fun `muscle contribution accepts one through ten thousand basis points`() {
        MuscleTarget(MuscleGroup.CHEST, 1)
        MuscleTarget(MuscleGroup.CHEST, 10_000)

        assertIllegalArgument { MuscleTarget(MuscleGroup.CHEST, 0) }
        assertIllegalArgument { MuscleTarget(MuscleGroup.CHEST, 10_001) }
    }

    @Test
    fun `exercise rejects duplicate targets for the same muscle`() {
        assertIllegalArgument {
            Exercise(
                id = "bench",
                name = "Bench press",
                muscleTargets = listOf(
                    MuscleTarget(MuscleGroup.CHEST, 10_000),
                    MuscleTarget(MuscleGroup.CHEST, 5_000),
                ),
            )
        }
    }

    @Test
    fun `workout set rejects negative physical values`() {
        assertIllegalArgument { workoutSet(weightGrams = -1) }
        assertIllegalArgument { workoutSet(repetitions = -1) }
        assertIllegalArgument { workoutSet(orderIndex = -1) }
    }

    @Test
    fun `workout set validates rpe and rir bounds`() {
        workoutSet(rpe = 1.0, rir = 10)
        workoutSet(rpe = 10.0, rir = 0)

        assertIllegalArgument { workoutSet(rpe = 0.9) }
        assertIllegalArgument { workoutSet(rpe = 10.1) }
        assertIllegalArgument { workoutSet(rpe = Double.NaN) }
        assertIllegalArgument { workoutSet(rpe = Double.POSITIVE_INFINITY) }
        assertIllegalArgument { workoutSet(rir = -1) }
        assertIllegalArgument { workoutSet(rir = 11) }
    }

    @Test
    fun `session cannot end before it starts`() {
        assertIllegalArgument {
            WorkoutSession(
                id = "session",
                startedAtEpochMillis = 2_000,
                endedAtEpochMillis = 1_999,
            )
        }
    }

    @Test
    fun `container models require unique ids and order indexes`() {
        val first = workoutSet(id = "first", orderIndex = 0)
        val secondWithSameIndex = workoutSet(id = "second", orderIndex = 0)
        assertIllegalArgument {
            WorkoutExercise(
                id = "workout-exercise",
                exerciseId = "exercise",
                orderIndex = 0,
                sets = listOf(first, secondWithSameIndex),
            )
        }
    }

    private fun workoutSet(
        id: String = "set",
        orderIndex: Int = 0,
        weightGrams: Long = 100_000,
        repetitions: Int = 8,
        rpe: Double? = null,
        rir: Int? = null,
    ) = WorkoutSet(
        id = id,
        orderIndex = orderIndex,
        weightGrams = weightGrams,
        repetitions = repetitions,
        rpe = rpe,
        rir = rir,
        completed = true,
    )

    private inline fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
