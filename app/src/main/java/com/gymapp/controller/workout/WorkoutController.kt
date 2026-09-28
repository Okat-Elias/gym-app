package com.gymapp.controller.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.RoutineRepository
import com.gymapp.model.repository.WorkoutRepository
import com.gymapp.model.usecase.ParseSetInput
import com.gymapp.model.usecase.StartRoutineWorkout
import com.gymapp.model.usecase.SummarizeWorkout
import com.gymapp.model.usecase.WorkoutSummary
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SetDraft(val weight: String = "", val repetitions: String = "", val rpe: String = "")

data class WorkoutUiState(
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val selectedRoutineId: String? = null,
    val workout: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
    val drafts: Map<String, SetDraft> = emptyMap(),
    val notes: String = "",
    val summary: WorkoutSummary? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = workout != null && workout.endedAtEpochMillis == null
}

sealed interface WorkoutAction {
    data class SelectRoutine(val id: String) : WorkoutAction
    data object StartWorkout : WorkoutAction
    data class ChangeWeight(val exerciseId: String, val value: String) : WorkoutAction
    data class ChangeRepetitions(val exerciseId: String, val value: String) : WorkoutAction
    data class ChangeRpe(val exerciseId: String, val value: String) : WorkoutAction
    data class LogSet(val exerciseId: String) : WorkoutAction
    data class RemoveSet(val exerciseId: String, val setId: String) : WorkoutAction
    data class ChangeNotes(val value: String) : WorkoutAction
    data object FinishWorkout : WorkoutAction
    data object StartNewWorkout : WorkoutAction
    data class ViewHistory(val id: String) : WorkoutAction
}

class WorkoutController(
    exerciseRepository: ExerciseRepository,
    routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel(), ScreenController<WorkoutUiState, WorkoutAction> {
    private val mutableState = MutableStateFlow(WorkoutUiState())
    override val state = mutableState.asStateFlow()
    private val summarize = SummarizeWorkout()
    private val parseInput = ParseSetInput()
    private var restored = false

    init {
        viewModelScope.launch {
            combine(
                exerciseRepository.observeExercises(), routineRepository.observeRoutines(),
                workoutRepository.observeWorkouts(),
            ) { exercises, routines, workouts -> Triple(exercises, routines, workouts) }
                .catch {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false, isReady = false,
                        errorMessage = "Impossible de charger les séances. Rouvrez l’application pour réessayer.",
                    )
                }.collect { (exercises, routines, workouts) ->
                    var current = mutableState.value.copy(
                        exercises = exercises, routines = routines,
                        history = workouts.filter { it.endedAtEpochMillis != null }
                            .sortedByDescending { it.startedAtEpochMillis },
                        isLoading = false, isReady = true,
                    )
                    if (!restored) {
                        restored = true
                        val active = workouts.filter { it.endedAtEpochMillis == null }
                            .maxByOrNull { it.startedAtEpochMillis }
                        if (active != null) current = current.copy(
                            workout = active, notes = active.notes,
                            summary = summarize(active, clock()),
                        )
                    }
                    mutableState.value = current
                }
        }
        viewModelScope.launch {
            // Derive from the persisted start instant, not from the number of timer ticks.
            while (isActive) {
                delay(1_000)
                val current = mutableState.value
                if (current.isActive) mutableState.value = current.copy(
                    summary = summarize(requireNotNull(current.workout), clock()),
                )
            }
        }
    }

    override fun onAction(action: WorkoutAction) {
        val current = mutableState.value
        if (!current.isReady || current.isSaving) return
        when (action) {
            is WorkoutAction.SelectRoutine -> if (!current.isActive) {
                mutableState.value = current.copy(selectedRoutineId = action.id, errorMessage = null)
            }
            WorkoutAction.StartWorkout -> {
                if (current.isActive) return
                val routine = current.routines.find { it.id == current.selectedRoutineId } ?: return
                persist(StartRoutineWorkout()(routine, clock(), newId), resetDrafts = true)
            }
            is WorkoutAction.ChangeWeight -> editDraft(action.exerciseId) { it.copy(weight = action.value) }
            is WorkoutAction.ChangeRepetitions -> editDraft(action.exerciseId) { it.copy(repetitions = action.value) }
            is WorkoutAction.ChangeRpe -> editDraft(action.exerciseId) { it.copy(rpe = action.value) }
            is WorkoutAction.ChangeNotes -> if (current.isActive) {
                mutableState.value = current.copy(notes = action.value)
            }
            is WorkoutAction.LogSet -> logSet(action.exerciseId)
            is WorkoutAction.RemoveSet -> {
                val workout = current.workout ?: return
                if (!current.isActive) return
                persist(workout.copy(notes = current.notes, exercises = workout.exercises.map { exercise ->
                    if (exercise.id != action.exerciseId) exercise else exercise.copy(
                        sets = exercise.sets.filterNot { it.id == action.setId }
                            .mapIndexed { index, set -> set.copy(orderIndex = index) },
                    )
                }))
            }
            WorkoutAction.FinishWorkout -> {
                val workout = current.workout ?: return
                if (!current.isActive) return
                if (current.drafts.values.any { draft ->
                    draft.weight.isNotBlank() || draft.repetitions.isNotBlank() || draft.rpe.isNotBlank()
                }) {
                    mutableState.value = current.copy(
                        errorMessage = "Validez les séries saisies ou videz leurs champs avant de terminer.",
                    )
                    return
                }
                persist(workout.copy(
                    notes = current.notes.trim(),
                    endedAtEpochMillis = clock().coerceAtLeast(workout.startedAtEpochMillis),
                ))
            }
            WorkoutAction.StartNewWorkout -> if (!current.isActive) {
                mutableState.value = current.copy(
                    workout = null, summary = null, drafts = emptyMap(), notes = "", errorMessage = null,
                )
            }
            is WorkoutAction.ViewHistory -> if (!current.isActive) {
                val workout = current.history.find { it.id == action.id } ?: return
                mutableState.value = current.copy(
                    workout = workout, notes = workout.notes, summary = summarize(workout, clock()),
                    drafts = emptyMap(), errorMessage = null,
                )
            }
        }
    }

    private fun editDraft(id: String, transform: (SetDraft) -> SetDraft) {
        val current = mutableState.value
        if (!current.isActive || current.workout?.exercises?.none { it.id == id } != false) return
        mutableState.value = current.copy(
            drafts = current.drafts + (id to transform(current.drafts[id] ?: SetDraft())),
            errorMessage = null,
        )
    }

    private fun logSet(exerciseId: String) {
        val current = mutableState.value
        val workout = current.workout ?: return
        if (!current.isActive) return
        val exercise = workout.exercises.find { it.id == exerciseId } ?: return
        val draft = current.drafts[exerciseId] ?: SetDraft()
        val input = parseInput(draft.weight, draft.repetitions, draft.rpe)
        if (input == null) {
            mutableState.value = current.copy(
                errorMessage = "Saisissez une charge de 0 à 1 000 kg (3 décimales maximum), " +
                    "1 à 1 000 répétitions et, si renseigné, un RPE de 1 à 10.",
            )
            return
        }
        val set = WorkoutSet(
            id = newId(), orderIndex = exercise.sets.size, weightGrams = input.weightGrams,
            repetitions = input.repetitions, rpe = input.rpe, completed = true,
        )
        persist(workout.copy(notes = current.notes, exercises = workout.exercises.map {
            if (it.id == exerciseId) it.copy(sets = it.sets + set) else it
        }), clearDraftId = exerciseId)
    }

    private fun persist(workout: WorkoutSession, resetDrafts: Boolean = false, clearDraftId: String? = null) {
        mutableState.value = mutableState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                workoutRepository.saveWorkout(workout)
                val current = mutableState.value
                mutableState.value = current.copy(
                    workout = workout, summary = summarize(workout, clock()), notes = workout.notes,
                    isSaving = false,
                    drafts = if (resetDrafts) emptyMap() else current.drafts - listOfNotNull(clearDraftId).toSet(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false, errorMessage = "Enregistrement impossible. Vos saisies sont conservées ; réessayez.",
                )
            }
        }
    }
}
