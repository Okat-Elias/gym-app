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

    @Test fun `edit and delete a routine while preserving completed workouts`() = flowTest {
        seedRoutine()
        val editor = keep(RoutinesController(exercises, routines, clock = { now }, newId = { "id-${sequence++}" }))
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        val pastSession = workouts.records.value.single()

        editor.onAction(RoutinesAction.Edit("r"))
        editor.onAction(RoutinesAction.ChangeName("Haut du corps"))
        editor.onAction(RoutinesAction.ToggleExercise("squat"))
        editor.onAction(RoutinesAction.Save)
        runCurrent()
        val updated = routines.records.value.single()
        assertEquals("r", updated.id)
        assertEquals("Haut du corps", updated.name)
        assertEquals(listOf("bench"), updated.exercises.map { it.exerciseId })
        assertEquals(pastSession, workouts.records.value.single())

        editor.onAction(RoutinesAction.Delete("r"))
        runCurrent()
        assertTrue(routines.records.value.isEmpty())
        assertEquals(pastSession, workouts.records.value.single())
    }

    @Test fun `previous completed sets are proposed in sequence with their type and can be deleted from history`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val exerciseId = controller.state.value.workout!!.exercises.first().id
        listOf(Triple("93", "8", WorkoutSetType.WARM_UP),
            Triple("93", "7", WorkoutSetType.WORKING),
            Triple("86", "8", WorkoutSetType.DROP_SET)).forEach { (weight, reps, type) ->
            controller.onAction(WorkoutAction.ChangeWeight(exerciseId, weight))
            controller.onAction(WorkoutAction.ChangeRepetitions(exerciseId, reps))
            controller.onAction(WorkoutAction.ChangeSetType(exerciseId, type))
            controller.onAction(WorkoutAction.LogSet(exerciseId))
            runCurrent()
        }
        now += 10_000
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        val previousId = controller.state.value.workout!!.id
        controller.onAction(WorkoutAction.StartNewWorkout)
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val nextExercise = controller.state.value.workout!!.exercises.first()
        assertTrue(nextExercise.sets.isEmpty())
        assertEquals(listOf(8, 7, 8), controller.state.value.previousSetsFor(nextExercise.id).map { it.repetitions })
        assertEquals("93", controller.state.value.drafts[nextExercise.id]?.weight)
        assertEquals(WorkoutSetType.WARM_UP, controller.state.value.drafts[nextExercise.id]?.type)
        controller.onAction(WorkoutAction.LogSet(nextExercise.id))
        runCurrent()
        assertEquals("7", controller.state.value.drafts[nextExercise.id]?.repetitions)
        assertEquals(WorkoutSetType.WORKING, controller.state.value.drafts[nextExercise.id]?.type)
        controller.onAction(WorkoutAction.LogSet(nextExercise.id))
        runCurrent()
        assertEquals("86", controller.state.value.drafts[nextExercise.id]?.weight)
        assertEquals(WorkoutSetType.DROP_SET, controller.state.value.drafts[nextExercise.id]?.type)
        assertEquals(2, controller.state.value.summary!!.completedSets)

        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertEquals(2, controller.state.value.summary!!.completedSets)

        controller.onAction(WorkoutAction.DeleteHistory(previousId))
        runCurrent()
        assertEquals(1, workouts.records.value.size)
        assertTrue(controller.state.value.previousSetsFor(nextExercise.id).isEmpty())
    }

    @Test fun `added and replaced exercises keep their order and previous sets after restart`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()

        val squatSlot = controller.state.value.workout!!.exercises.last().id
        controller.onAction(WorkoutAction.AddExercise("lat"))
        runCurrent()
        val latSlot = controller.state.value.workout!!.exercises.last().id
        controller.onAction(WorkoutAction.ChangeWeight(latSlot, "93"))
        controller.onAction(WorkoutAction.ChangeRepetitions(latSlot, "8"))
        controller.onAction(WorkoutAction.LogSet(latSlot))
        runCurrent()
        controller.onAction(WorkoutAction.ReplaceExercise(squatSlot, "row"))
        runCurrent()
        controller.onAction(WorkoutAction.ChangeWeight(squatSlot, "50"))
        controller.onAction(WorkoutAction.ChangeRepetitions(squatSlot, "10"))
        controller.onAction(WorkoutAction.LogSet(squatSlot))
        runCurrent()
        val first = controller.state.value.workout!!
        assertEquals(listOf("bench", "row", "lat"), first.exercises.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), first.exercises.map { it.orderIndex })
        assertEquals(2, first.exercises.sumOf { it.sets.size })

        now += 10_000
        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        controller.onAction(WorkoutAction.StartNewWorkout)
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val nextSquatSlot = controller.state.value.workout!!.exercises.last().id
        controller.onAction(WorkoutAction.AddExercise("lat"))
        runCurrent()
        val nextLatSlot = controller.state.value.workout!!.exercises.last().id
        assertEquals("93", controller.state.value.drafts[nextLatSlot]?.weight)
        assertTrue(controller.state.value.workout!!.exercises.last().sets.isEmpty())
        controller.onAction(WorkoutAction.ReplaceExercise(nextSquatSlot, "row"))
        runCurrent()
        assertEquals("50", controller.state.value.drafts[nextSquatSlot]?.weight)
        assertEquals("10", controller.state.value.drafts[nextSquatSlot]?.repetitions)

        val restored = controller()
        runCurrent()
        assertEquals(listOf("bench", "row", "lat"),
            restored.state.value.workout!!.exercises.map { it.exerciseId })
        assertEquals("93", restored.state.value.drafts[nextLatSlot]?.weight)
        assertEquals("50", restored.state.value.drafts[nextSquatSlot]?.weight)
        assertEquals(0, restored.state.value.summary!!.completedSets)
        assertEquals(listOf("bench", "squat"), routines.records.value.single().exercises.map { it.exerciseId })
    }

    @Test fun `duplicate selection and replacement of logged sets are rejected`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val original = controller.state.value.workout!!
        val benchSlot = original.exercises.first().id
        val squatSlot = original.exercises.last().id

        controller.onAction(WorkoutAction.AddExercise("bench"))
        assertEquals(original, controller.state.value.workout)
        assertNotNull(controller.state.value.errorMessage)
        controller.onAction(WorkoutAction.ReplaceExercise(squatSlot, "bench"))
        assertEquals(original, controller.state.value.workout)

        controller.onAction(WorkoutAction.ChangeWeight(benchSlot, "30"))
        controller.onAction(WorkoutAction.ChangeRepetitions(benchSlot, "8"))
        controller.onAction(WorkoutAction.LogSet(benchSlot))
        runCurrent()
        val logged = controller.state.value.workout!!
        controller.onAction(WorkoutAction.ReplaceExercise(benchSlot, "row"))
        assertEquals(logged, controller.state.value.workout)
        assertEquals(1, controller.state.value.summary!!.completedSets)
        assertNotNull(controller.state.value.errorMessage)
    }

    @Test fun `replacement waits for draft to be cleared and failed save keeps original exercise`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val original = controller.state.value.workout!!
        val slot = original.exercises.last().id

        controller.onAction(WorkoutAction.ChangeWeight(slot, "42"))
        controller.onAction(WorkoutAction.ReplaceExercise(slot, "row"))
        assertEquals(original, controller.state.value.workout)
        assertEquals("42", controller.state.value.drafts[slot]?.weight)
        controller.onAction(WorkoutAction.ClearDraft(slot))
        controller.onAction(WorkoutAction.ChangeSetType(slot, WorkoutSetType.DROP_SET))
        workouts.fail = true
        controller.onAction(WorkoutAction.ReplaceExercise(slot, "row"))
        runCurrent()
        assertEquals(original, workouts.records.value.single())
        assertEquals(original, controller.state.value.workout)
        assertEquals(WorkoutSetType.DROP_SET, controller.state.value.drafts[slot]?.type)

        workouts.fail = false
        controller.onAction(WorkoutAction.ReplaceExercise(slot, "row"))
        runCurrent()
        assertEquals("row", controller.state.value.workout!!.exercises.last().exerciseId)
        assertNull(controller.state.value.drafts[slot])
        assertEquals(listOf(0, 1), controller.state.value.workout!!.exercises.map { it.orderIndex })
    }

    @Test fun `finishing a modified session without updating keeps the reusable routine`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val squatSlot = controller.state.value.workout!!.exercises.last().id
        controller.onAction(WorkoutAction.ReplaceExercise(squatSlot, "row"))
        runCurrent()
        controller.onAction(WorkoutAction.AddExercise("lat"))
        runCurrent()
        assertTrue(controller.state.value.hasRoutinePlanChanges)
        val completedPlan = controller.state.value.workout!!.exercises.map { it.exerciseId }

        controller.onAction(WorkoutAction.FinishWorkout)
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertEquals(completedPlan, workouts.records.value.single().exercises.map { it.exerciseId })
        assertEquals(listOf("bench", "squat"), routines.records.value.single().exercises.map { it.exerciseId })

        controller.onAction(WorkoutAction.StartNewWorkout)
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        assertEquals(listOf("bench", "squat"), controller.state.value.workout!!.exercises.map { it.exerciseId })
    }

    @Test fun `finishing and updating routine changes future sessions but preserves historical workouts`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        val firstWorkout = controller.state.value.workout!!
        val squatSlot = firstWorkout.exercises.last().id
        controller.onAction(WorkoutAction.ReplaceExercise(squatSlot, "row"))
        runCurrent()
        controller.onAction(WorkoutAction.AddExercise("lat"))
        runCurrent()
        val changedWorkoutId = controller.state.value.workout!!.id
        now += 10_000
        controller.onAction(WorkoutAction.FinishAndUpdateRoutine)
        runCurrent()

        val template = routines.records.value.single()
        assertEquals("r", template.id)
        assertEquals("Full body", template.name)
        assertEquals(0L, template.createdAtEpochMillis)
        assertEquals(listOf("bench", "row", "lat"), template.exercises.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), template.exercises.map { it.orderIndex })
        assertEquals("ra", template.exercises.first().id)
        assertEquals(changedWorkoutId, workouts.records.value.single().id)
        assertNotNull(workouts.records.value.single().endedAtEpochMillis)
        assertFalse(controller.state.value.hasRoutinePlanChanges)

        controller.onAction(WorkoutAction.StartNewWorkout)
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        assertEquals(listOf("bench", "row", "lat"), controller.state.value.workout!!.exercises.map { it.exerciseId })
        assertEquals(listOf("bench", "row", "lat"), workouts.records.value.first { it.id == changedWorkoutId }
            .exercises.map { it.exerciseId })
    }

    @Test fun `failed routine update leaves completed session saved and allows retry`() = flowTest {
        seedRoutine()
        val controller = controller()
        runCurrent()
        controller.onAction(WorkoutAction.SelectRoutine("r"))
        controller.onAction(WorkoutAction.StartWorkout)
        runCurrent()
        controller.onAction(WorkoutAction.AddExercise("lat"))
        runCurrent()
        val workoutId = controller.state.value.workout!!.id
        routines.failSave = true
        controller.onAction(WorkoutAction.FinishAndUpdateRoutine)
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertNotNull(workouts.records.value.single().endedAtEpochMillis)
        assertEquals(listOf("bench", "squat"), routines.records.value.single().exercises.map { it.exerciseId })
        assertTrue(controller.state.value.hasRoutinePlanChanges)
        assertNotNull(controller.state.value.errorMessage)

        routines.failSave = false
        controller.onAction(WorkoutAction.RetryRoutineUpdate)
        runCurrent()
        assertEquals(listOf("bench", "squat", "lat"), routines.records.value.single().exercises.map { it.exerciseId })
        assertEquals(1, workouts.records.value.size)
        assertEquals(workoutId, workouts.records.value.single().id)
        assertFalse(controller.state.value.hasRoutinePlanChanges)
    }

    private class FakeExercises : ExerciseRepository {
        val records = MutableStateFlow(listOf(
            Exercise("bench", "Développé couché"), Exercise("squat", "Squat"),
            Exercise("lat", "Tirage vertical"), Exercise("row", "Rowing"),
        ))
        override fun observeExercises() = records
        override fun observeExercise(exerciseId: String) = records.map { list -> list.find { it.id == exerciseId } }
        override suspend fun getExercise(exerciseId: String) = records.value.find { it.id == exerciseId }
        override suspend fun saveExercise(exercise: Exercise) { records.value += exercise }
        override suspend fun deleteExercise(exerciseId: String) { records.value = records.value.filterNot { it.id == exerciseId } }
    }

    private class FakeRoutines : RoutineRepository {
        val records = MutableStateFlow(emptyList<Routine>())
        var failSave = false
        override fun observeRoutines() = records
        override suspend fun saveRoutine(routine: Routine) {
            if (failSave) error("Simulated routine save error")
            records.value = records.value.filterNot { it.id == routine.id } + routine
        }
        override suspend fun deleteRoutine(routineId: String) { records.value = records.value.filterNot { it.id == routineId } }
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
