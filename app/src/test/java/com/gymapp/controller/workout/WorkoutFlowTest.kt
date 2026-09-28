package com.gymapp.controller.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.gymapp.controller.routines.RoutinesAction
import com.gymapp.controller.routines.RoutinesController
import com.gymapp.model.domain.*
import com.gymapp.model.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val exercises = FakeExercises()
    private val routines = FakeRoutines()
    private val workouts = FakeWorkouts()
    private var now = 1_000L
    private var sequence = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun flowTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { store.clear() }
    }

    private fun <T : ViewModel> keep(controller: T): T {
        store.put("controller-${sequence++}", controller)
        return controller
    }

    private fun controller() = keep(WorkoutController(exercises, routines, workouts,
        clock = { now }, newId = { "id-${sequence++}" }))

    private fun seedRoutine() {
        routines.records.value = listOf(Routine("r", "Full body", createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0, exercises = listOf(
                RoutineExercise("ra", "bench", 0), RoutineExercise("rb", "squat", 1),
            )))
    }

    @Test fun `create ordered routine start log both exercises finish and reopen history`() = flowTest {
        val editor = keep(RoutinesController(exercises, routines, clock = { now }, newId = { "id-${sequence++}" }))
        val controller = controller()
        runCurrent()
        editor.onAction(RoutinesAction.ChangeName("Full body"))
        editor.onAction(RoutinesAction.ToggleExercise("squat"))
        editor.onAction(RoutinesAction.ToggleExercise("bench"))
        editor.onAction(RoutinesAction.MoveExercise("bench", -1))
        editor.onAction(RoutinesAction.Save)
        runCurrent()
        val routine = routines.records.value.single()
        assertEquals(listOf("bench", "squat"), routine.exercises.map { it.exerciseId })
        controller.onAction(WorkoutAction.SelectRoutine(routine.id))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val active = controller.state.value.workout!!
        active.exercises.forEach { exercise ->
            controller.onAction(WorkoutAction.ChangeWeight(exercise.id, "62,5"))
            controller.onAction(WorkoutAction.ChangeRepetitions(exercise.id, "8"))
            controller.onAction(WorkoutAction.LogSet(exercise.id))
            runCurrent()
        }
        now = 61_000
        controller.onAction(WorkoutAction.ChangeNotes("Bonne séance"))
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        val finished = workouts.records.value.single()
        assertEquals(61_000L, finished.endedAtEpochMillis)
        assertEquals("Bonne séance", finished.notes)
        assertEquals(2, controller.state.value.summary!!.completedSets)
        assertEquals(1_000_000L, controller.state.value.summary!!.tonnageGrams)
        assertEquals(60_000L, controller.state.value.summary!!.durationMillis)
        assertFalse(controller.state.value.isActive)
        val reopened = controller()
        runCurrent()
        reopened.onAction(WorkoutAction.ViewHistory(finished.id))
        assertEquals(finished, reopened.state.value.workout)
        assertEquals(1_000_000L, reopened.state.value.summary!!.tonnageGrams)
    }

    @Test fun `restore active session with no sets and update elapsed time after restart`() = flowTest {
        seedRoutine()
        val original = controller()
        runCurrent()
        original.onAction(WorkoutAction.SelectRoutine("r"))
        original.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        now = 121_000
        val restored = controller()
        runCurrent()
        assertTrue(restored.state.value.isActive)
        assertEquals(original.state.value.workout, restored.state.value.workout)
        assertEquals(120_000L, restored.state.value.summary!!.durationMillis)
        now += 1_000
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(121_000L, restored.state.value.summary!!.durationMillis)
    }

    @Test fun `failed save retains input double taps do not duplicate and finish respects drafts`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        assertEquals(1, workouts.records.value.size)
        val id = controller.state.value.workout!!.exercises.first().id
        controller.onAction(WorkoutAction.ChangeWeight(id, "42,5"))
        controller.onAction(WorkoutAction.ChangeRepetitions(id, "10"))
        controller.onAction(WorkoutAction.FinishWorkout)
        assertTrue(controller.state.value.isActive)
        assertNotNull(controller.state.value.errorMessage)
        workouts.fail = true
        controller.onAction(WorkoutAction.LogSet(id))
        runCurrent()
        assertEquals("42,5", controller.state.value.drafts[id]!!.weight)
        assertEquals(0, controller.state.value.summary!!.completedSets)
        workouts.fail = false
        workouts.gate = CompletableDeferred()
        controller.onAction(WorkoutAction.LogSet(id))
        runCurrent()
        controller.onAction(WorkoutAction.LogSet(id))
        workouts.gate!!.complete(Unit)
        runCurrent()
        assertEquals(1, controller.state.value.summary!!.completedSets)
        assertTrue(controller.state.value.drafts.isEmpty())
        workouts.fail = true
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        assertTrue(controller.state.value.isActive)
        assertNull(workouts.records.value.single().endedAtEpochMillis)
        workouts.fail = false
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        assertFalse(controller.state.value.isActive)
    }

    @Test fun `removing a mistaken set updates persisted tonnage and preserves other exercises`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val id = controller.state.value.workout!!.exercises.first().id
        repeat(2) {
            controller.onAction(WorkoutAction.ChangeWeight(id, "20"))
            controller.onAction(WorkoutAction.ChangeRepetitions(id, "10"))
            controller.onAction(WorkoutAction.LogSet(id))
            runCurrent()
        }
        val setId = controller.state.value.workout!!.exercises.first().sets.first().id
        controller.onAction(WorkoutAction.RemoveSet(id, setId))
        runCurrent()
        assertEquals(200_000L, controller.state.value.summary!!.tonnageGrams)
        assertEquals(2, workouts.records.value.single().exercises.size)
        assertEquals(0, workouts.records.value.single().exercises.first().sets.single().orderIndex)
    }

    private class FakeExercises : ExerciseRepository {
        val records = MutableStateFlow(listOf(Exercise("bench", "Développé couché"), Exercise("squat", "Squat")))
        override fun observeExercises() = records
        override fun observeExercise(exerciseId: String) = records.map { list -> list.find { it.id == exerciseId } }
        override suspend fun getExercise(exerciseId: String) = records.value.find { it.id == exerciseId }
        override suspend fun saveExercise(exercise: Exercise) { records.value += exercise }
        override suspend fun deleteExercise(exerciseId: String) { records.value = records.value.filterNot { it.id == exerciseId } }
    }

    private class FakeRoutines : RoutineRepository {
        val records = MutableStateFlow(emptyList<Routine>())
        override fun observeRoutines() = records
        override suspend fun saveRoutine(routine: Routine) { records.value = records.value.filterNot { it.id == routine.id } + routine }
    }

    private class FakeWorkouts : WorkoutRepository {
        val records = MutableStateFlow(emptyList<WorkoutSession>())
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override fun observeWorkouts() = records
        override fun observeWorkout(workoutId: String) = records.map { list -> list.find { it.id == workoutId } }
        override suspend fun getWorkout(workoutId: String) = records.value.find { it.id == workoutId }
        override suspend fun saveWorkout(workout: WorkoutSession) {
            if (fail) error("Simulated disk error")
            gate?.await()
            records.value = records.value.filterNot { it.id == workout.id } + workout
        }
        override suspend fun saveWorkoutSet(workoutExerciseId: String, workoutSet: WorkoutSet) = error("Not used")
        override suspend fun deleteWorkoutSet(workoutSetId: String) = error("Not used")
        override suspend fun deleteWorkout(workoutId: String) { records.value = records.value.filterNot { it.id == workoutId } }
    }
}
