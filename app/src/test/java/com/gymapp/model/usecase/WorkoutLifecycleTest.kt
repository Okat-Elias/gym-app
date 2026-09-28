package com.gymapp.model.usecase

import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.RoutineExercise
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import org.junit.Assert.*
import org.junit.Test

class WorkoutLifecycleTest {
    @Test
    fun `starting the same routine twice creates independent empty sessions in plan order`() {
        val routine = Routine("r", "Full body", createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
            exercises = listOf(RoutineExercise("b", "squat", 1), RoutineExercise("a", "bench", 0)))
        var sequence = 0
        val ids = { "id-${sequence++}" }
        val first = StartRoutineWorkout()(routine, 1000, ids)
        val second = StartRoutineWorkout()(routine, 2000, ids)
        assertEquals(listOf("bench", "squat"), first.exercises.map { it.exerciseId })
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.exercises[0].id, second.exercises[0].id)
        assertTrue(second.exercises.all { it.sets.isEmpty() })
        assertEquals("r", first.routineId)
    }

    @Test
    fun `summary counts only completed sets across all exercises and freezes finished duration`() {
        val workout = WorkoutSession("w", startedAtEpochMillis = 1000, endedAtEpochMillis = 61_000,
            exercises = listOf(
                WorkoutExercise("a", "bench", 0, sets = listOf(
                    WorkoutSet("s1", 0, weightGrams = 62_500, repetitions = 8, completed = true),
                    WorkoutSet("s2", 1, weightGrams = 500_000, repetitions = 10, completed = false),
                )),
                WorkoutExercise("b", "squat", 1, sets = listOf(
                    WorkoutSet("s3", 0, weightGrams = 100_000, repetitions = 5, completed = true),
                )),
                WorkoutExercise("c", "pull", 2),
            ))
        assertEquals(WorkoutSummary(60_000, 2, 13, 1_000_000, 2), SummarizeWorkout()(workout, 200_000))
    }

    @Test
    fun `manual weight accepts comma point and zero without rounding away grams`() {
        val parse = ParseSetInput()
        assertEquals(SetInput(62_500, 8, 8.5), parse("62,5", "8", "8,5"))
        assertEquals(SetInput(1_125, 10, null), parse("1.125", "10", ""))
        assertEquals(SetInput(0, 12, null), parse("0", "12", ""))
    }

    @Test
    fun `manual input rejects partial negative overflow and invalid effort`() {
        val parse = ParseSetInput()
        listOf("", "-1", "NaN", "1e3", "2,", "0.0001", "1001", "999999999999999999999")
            .forEach { assertNull(parse(it, "8", "")) }
        listOf("0", "-1", "1.5", "1001", "9999999999999999999")
            .forEach { assertNull(parse("20", it, "")) }
        assertNull(parse("20", "8", "11"))
        assertNull(parse("20", "8", "NaN"))
    }
}
