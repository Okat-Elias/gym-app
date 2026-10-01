package com.gymapp.controller.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.RoutineExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.domain.WorkoutSetType
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

data class SetDraft(
    val weight: String = "", val repetitions: String = "", val rpe: String = "",
    val type: WorkoutSetType = WorkoutSetType.WORKING,
    val isSuggested: Boolean = false,
)

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
    val routineUpdatePendingRetry: Boolean = false,
) {
    val isActive: Boolean get() = workout != null && workout.endedAtEpochMillis == null

    val hasRoutinePlanChanges: Boolean get() {
        val session = workout ?: return false
        val routine = routines.find { it.id == session.routineId } ?: return false
        return session.exercises.sortedBy { it.orderIndex }.map { it.exerciseId } !=
            routine.exercises.sortedBy { it.orderIndex }.map { it.exerciseId }
    }

    /** Previous results are references only; they are not part of the new session's sets. */
    fun previousSetsFor(exerciseId: String): List<WorkoutSet> {
        val session = workout ?: return emptyList()
        val routineId = session.routineId ?: return emptyList()
        val exercise = session.exercises.find { it.id == exerciseId } ?: return emptyList()
        val previous = history.firstOrNull {
            it.routineId == routineId && it.startedAtEpochMillis <= session.startedAtEpochMillis && it.id != session.id
        } ?: return emptyList()
        return previous.exercises.find { it.exerciseId == exercise.exerciseId }
            ?.sets?.filter { it.completed }?.sortedBy { it.orderIndex }.orEmpty()
    }
}

sealed interface WorkoutAction {
    data class SelectRoutine(val id: String) : WorkoutAction
    data object StartWorkout : WorkoutAction
    data class AddExercise(val exerciseId: String) : WorkoutAction
    data class ReplaceExercise(val workoutExerciseId: String, val exerciseId: String) : WorkoutAction
    data class ChangeWeight(val exerciseId: String, val value: String) : WorkoutAction
    data class ChangeRepetitions(val exerciseId: String, val value: String) : WorkoutAction
    data class ChangeRpe(val exerciseId: String, val value: String) : WorkoutAction
    data class ChangeSetType(val exerciseId: String, val type: WorkoutSetType) : WorkoutAction
    data class LoadPreviousSet(val exerciseId: String, val setId: String) : WorkoutAction
    data class ClearDraft(val exerciseId: String) : WorkoutAction
    data class LogSet(val exerciseId: String) : WorkoutAction
    data class RemoveSet(val exerciseId: String, val setId: String) : WorkoutAction
    data class ChangeNotes(val value: String) : WorkoutAction
    data object FinishWorkout : WorkoutAction
    data object FinishAndUpdateRoutine : WorkoutAction
    data object RetryRoutineUpdate : WorkoutAction
    data object StartNewWorkout : WorkoutAction
    data class ViewHistory(val id: String) : WorkoutAction
    data class DeleteHistory(val id: String) : WorkoutAction
}

class WorkoutController(
    exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
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
                        selectedRoutineId = mutableState.value.selectedRoutineId?.takeIf { selected ->
                            routines.any { it.id == selected }
                        },
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
                        if (active != null) current = current.copy(
                            drafts = initialDrafts(current, active),
                        )
                    } else if (!current.isSaving && current.isActive) {
                        val updated = workouts.find { it.id == current.workout?.id }
                        if (updated != null && updated != current.workout) {
                            current = current.copy(workout = updated, summary = summarize(updated, clock()))
                        }
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
            is WorkoutAction.AddExercise -> addExercise(action.exerciseId)
            is WorkoutAction.ReplaceExercise -> replaceExercise(action.workoutExerciseId, action.exerciseId)
            is WorkoutAction.ChangeWeight -> editDraft(action.exerciseId) {
                it.copy(weight = action.value, isSuggested = false)
            }
            is WorkoutAction.ChangeRepetitions -> editDraft(action.exerciseId) {
                it.copy(repetitions = action.value, isSuggested = false)
            }
            is WorkoutAction.ChangeRpe -> editDraft(action.exerciseId) {
                it.copy(rpe = action.value, isSuggested = false)
            }
            is WorkoutAction.ChangeSetType -> editDraft(action.exerciseId) {
                it.copy(type = action.type, isSuggested = false)
            }
            is WorkoutAction.LoadPreviousSet -> {
                val set = current.previousSetsFor(action.exerciseId).find { it.id == action.setId } ?: return
                editDraft(action.exerciseId) { set.toDraft() }
            }
            is WorkoutAction.ClearDraft -> if (current.isActive) {
                mutableState.value = current.copy(
                    drafts = current.drafts - action.exerciseId, errorMessage = null,
                )
            }
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
            WorkoutAction.FinishWorkout -> finishWorkout(updateRoutine = false)
            WorkoutAction.FinishAndUpdateRoutine -> finishWorkout(updateRoutine = true)
            WorkoutAction.RetryRoutineUpdate -> retryRoutineUpdate()
            WorkoutAction.StartNewWorkout -> if (!current.isActive) {
                mutableState.value = current.copy(
                    workout = null, summary = null, drafts = emptyMap(), notes = "", errorMessage = null,
                    routineUpdatePendingRetry = false,
                )
            }
            is WorkoutAction.ViewHistory -> if (!current.isActive) {
                val workout = current.history.find { it.id == action.id } ?: return
                mutableState.value = current.copy(
                    workout = workout, notes = workout.notes, summary = summarize(workout, clock()),
                    drafts = emptyMap(), errorMessage = null, routineUpdatePendingRetry = false,
                )
            }
            is WorkoutAction.DeleteHistory -> {
                if (current.history.none { it.id == action.id }) return
                mutableState.value = current.copy(isSaving = true, errorMessage = null)
                viewModelScope.launch {
                    try {
                        workoutRepository.deleteWorkout(action.id)
                        val latest = mutableState.value
                        mutableState.value = latest.copy(
                            history = latest.history.filterNot { it.id == action.id },
                            workout = latest.workout?.takeUnless { it.id == action.id },
                            summary = if (latest.workout?.id == action.id) null else latest.summary,
                            routineUpdatePendingRetry = latest.routineUpdatePendingRetry &&
                                latest.workout?.id != action.id,
                            isSaving = false,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.value = mutableState.value.copy(
                            isSaving = false, errorMessage = "Suppression de la séance impossible. Réessayez.",
                        )
                    }
                }
            }
        }
    }

    private fun finishWorkout(updateRoutine: Boolean) {
        val current = mutableState.value
        val workout = current.workout ?: return
        if (!current.isActive) return
        if (current.drafts.values.any { draft ->
            !draft.isSuggested && (draft.weight.isNotBlank() ||
                draft.repetitions.isNotBlank() || draft.rpe.isNotBlank())
        }) {
            mutableState.value = current.copy(
                errorMessage = "Validez les séries saisies ou videz leurs champs avant de terminer.",
            )
            return
        }
        val shouldUpdateRoutine = updateRoutine && current.hasRoutinePlanChanges
        val finished = workout.copy(
            notes = current.notes.trim(),
            endedAtEpochMillis = clock().coerceAtLeast(workout.startedAtEpochMillis),
        )
        mutableState.value = current.copy(
            isSaving = true, errorMessage = null, routineUpdatePendingRetry = false,
        )
        viewModelScope.launch {
            try {
                workoutRepository.saveWorkout(finished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    errorMessage = "Enregistrement impossible. Vos saisies sont conservées ; réessayez.",
                )
                return@launch
            }
            val latest = mutableState.value
            mutableState.value = latest.copy(
                workout = finished, summary = summarize(finished, clock()), notes = finished.notes,
                drafts = emptyMap(), isSaving = shouldUpdateRoutine,
            )
            if (shouldUpdateRoutine) saveRoutinePlan(finished)
        }
    }

    private fun retryRoutineUpdate() {
        val current = mutableState.value
        val finished = current.workout ?: return
        if (finished.endedAtEpochMillis == null || !current.routineUpdatePendingRetry) return
        if (current.routines.any { it.id == finished.routineId } && !current.hasRoutinePlanChanges) {
            mutableState.value = current.copy(
                routineUpdatePendingRetry = false, errorMessage = null,
            )
            return
        }
        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch { saveRoutinePlan(finished) }
    }

    private suspend fun saveRoutinePlan(finished: WorkoutSession) {
        val current = mutableState.value
        val routine = current.routines.find { it.id == finished.routineId }
        if (routine == null) {
            mutableState.value = current.copy(
                isSaving = false, routineUpdatePendingRetry = false,
                errorMessage = "Séance enregistrée. La routine a été supprimée ; son plan n’a pas été mis à jour.",
            )
            return
        }
        if (!current.hasRoutinePlanChanges) {
            mutableState.value = current.copy(
                isSaving = false, routineUpdatePendingRetry = false, errorMessage = null,
            )
            return
        }
        try {
            val oldExercises = routine.exercises.associateBy { it.exerciseId }
            val usedIds = mutableSetOf<String>()
            val updated = routine.copy(
                updatedAtEpochMillis = clock().coerceAtLeast(routine.updatedAtEpochMillis),
                exercises = finished.exercises.sortedBy { it.orderIndex }.mapIndexed { index, exercise ->
                    val retained = oldExercises[exercise.exerciseId]
                    val retainedId = retained?.id?.takeIf { usedIds.add(it) }
                    RoutineExercise(
                        id = retainedId ?: newId(), exerciseId = exercise.exerciseId,
                        orderIndex = index, supersetGroupId = retained?.supersetGroupId,
                    )
                },
            )
            routineRepository.saveRoutine(updated)
            mutableState.value = mutableState.value.copy(
                isSaving = false, routineUpdatePendingRetry = false, errorMessage = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                isSaving = false, routineUpdatePendingRetry = true,
                errorMessage = "Séance enregistrée, mais la routine n’a pas été mise à jour. Réessayez depuis ce bilan.",
            )
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

    private fun addExercise(exerciseId: String) {
        val current = mutableState.value
        val workout = current.workout ?: return
        if (!current.isActive || !canSelectExercise(current, workout, exerciseId)) return
        val ordered = workout.exercises.sortedBy { it.orderIndex }
            .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }
        val added = WorkoutExercise(
            id = newId(), exerciseId = exerciseId,
            orderIndex = ordered.size,
        )
        persist(
            workout.copy(notes = current.notes, exercises = ordered + added),
            clearDraftId = added.id,
        )
    }

    private fun replaceExercise(workoutExerciseId: String, exerciseId: String) {
        val current = mutableState.value
        val workout = current.workout ?: return
        if (!current.isActive) return
        val original = workout.exercises.find { it.id == workoutExerciseId } ?: return
        if (original.sets.isNotEmpty()) {
            mutableState.value = current.copy(errorMessage =
                "Cet exercice contient déjà des séries validées. Ajoutez un autre exercice pour conserver leur historique.")
            return
        }
        val draft = current.drafts[workoutExerciseId]
        if (draft != null && !draft.isSuggested &&
            (draft.weight.isNotBlank() || draft.repetitions.isNotBlank() || draft.rpe.isNotBlank())) {
            mutableState.value = current.copy(errorMessage =
                "Validez ou effacez la saisie en cours avant de remplacer cet exercice.")
            return
        }
        if (!canSelectExercise(current, workout, exerciseId)) return
        val ordered = workout.exercises.sortedBy { it.orderIndex }
            .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }
        persist(
            workout.copy(notes = current.notes, exercises = ordered.map { exercise ->
                if (exercise.id == workoutExerciseId) exercise.copy(exerciseId = exerciseId) else exercise
            }),
            clearDraftId = workoutExerciseId,
        )
    }

    private fun canSelectExercise(
        state: WorkoutUiState,
        workout: WorkoutSession,
        exerciseId: String,
    ): Boolean {
        val message = when {
            state.exercises.none { it.id == exerciseId } -> "Cet exercice n’est plus disponible dans le catalogue."
            workout.exercises.any { it.exerciseId == exerciseId } -> "Cet exercice est déjà présent dans la séance."
            else -> return true
        }
        mutableState.value = state.copy(errorMessage = message)
        return false
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
            repetitions = input.repetitions, rpe = input.rpe, type = draft.type, completed = true,
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
                val suggestions = current.copy(workout = workout)
                val nextDrafts = if (resetDrafts) initialDrafts(suggestions, workout) else
                    if (clearDraftId != null) {
                        val nextIndex = workout.exercises.find { it.id == clearDraftId }?.sets?.size ?: 0
                        val suggested = suggestions.previousSetsFor(clearDraftId).getOrNull(nextIndex)?.toDraft()
                        if (suggested == null) current.drafts - clearDraftId
                        else current.drafts + (clearDraftId to suggested)
                    } else current.drafts
                mutableState.value = current.copy(
                    workout = workout, summary = summarize(workout, clock()), notes = workout.notes,
                    isSaving = false,
                    drafts = nextDrafts,
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

    private fun initialDrafts(state: WorkoutUiState, workout: WorkoutSession): Map<String, SetDraft> =
        workout.exercises.mapNotNull { exercise ->
            state.previousSetsFor(exercise.id).getOrNull(exercise.sets.size)?.toDraft()
                ?.let { exercise.id to it }
        }.toMap()

    private fun WorkoutSet.toDraft() = SetDraft(
        weight = java.math.BigDecimal.valueOf(weightGrams, 3).stripTrailingZeros().toPlainString(),
        repetitions = repetitions.toString(), rpe = rpe?.toString().orEmpty(), type = type,
        isSuggested = true,
    )
}
