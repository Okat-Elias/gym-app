package com.gymapp.controller.routines

import androidx.lifecycle.ViewModelStore
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.Routine
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.RoutineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesExerciseSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val exercises = TestExercises()
    private val routines = TestRoutines()
    private var nextId = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `adding and replacing a middle exercise preserves unique ordered routine entries`() = runTest(dispatcher) {
        try {
            val editor = RoutinesController(exercises, routines, clock = { 1_000L }, newId = { "id-${nextId++}" })
            store.put("editor", editor)
            runCurrent()

            editor.onAction(RoutinesAction.ChangeName("Full body"))
            editor.onAction(RoutinesAction.AddExercise("bench"))
            editor.onAction(RoutinesAction.AddExercise("squat"))
            editor.onAction(RoutinesAction.AddExercise("row"))
            editor.onAction(RoutinesAction.AddExercise("squat"))
            editor.onAction(RoutinesAction.ReplaceExercise("squat", "bench"))
            assertEquals(listOf("bench", "squat", "row"), editor.state.value.selectedExerciseIds)

            editor.onAction(RoutinesAction.ReplaceExercise("squat", "press"))
            assertEquals(listOf("bench", "press", "row"), editor.state.value.selectedExerciseIds)
            editor.onAction(RoutinesAction.Save)
            runCurrent()
            val saved = routines.records.value.single()
            assertEquals(listOf("bench", "press", "row"), saved.exercises.map { it.exerciseId })
            assertEquals(listOf(0, 1, 2), saved.exercises.map { it.orderIndex })

            editor.onAction(RoutinesAction.Edit(saved.id))
            editor.onAction(RoutinesAction.RemoveExercise("press"))
            editor.onAction(RoutinesAction.AddExercise("squat"))
            assertEquals(listOf("bench", "row", "squat"), editor.state.value.selectedExerciseIds)
            editor.onAction(RoutinesAction.Save)
            runCurrent()
            val updated = routines.records.value.single()
            assertEquals(saved.id, updated.id)
            assertEquals(listOf("bench", "row", "squat"), updated.exercises.map { it.exerciseId })
            assertEquals(listOf(0, 1, 2), updated.exercises.map { it.orderIndex })
            assertTrue(updated.exercises.map { it.exerciseId }.distinct().size == updated.exercises.size)
        } finally {
            store.clear()
        }
    }

    private class TestExercises : ExerciseRepository {
        val records = MutableStateFlow(listOf(
            Exercise("bench", "Développé couché"), Exercise("squat", "Squat"),
            Exercise("row", "Rowing"), Exercise("press", "Développé militaire"),
        ))
        override fun observeExercises() = records
        override fun observeExercise(exerciseId: String) = records.map { list -> list.find { it.id == exerciseId } }
        override suspend fun getExercise(exerciseId: String) = records.value.find { it.id == exerciseId }
        override suspend fun saveExercise(exercise: Exercise) { records.value += exercise }
        override suspend fun deleteExercise(exerciseId: String) {
            records.value = records.value.filterNot { it.id == exerciseId }
        }
    }

    private class TestRoutines : RoutineRepository {
        val records = MutableStateFlow(emptyList<Routine>())
        override fun observeRoutines() = records
        override suspend fun saveRoutine(routine: Routine) {
            records.value = records.value.filterNot { it.id == routine.id } + routine
        }
        override suspend fun deleteRoutine(routineId: String) {
            records.value = records.value.filterNot { it.id == routineId }
        }
    }
}
