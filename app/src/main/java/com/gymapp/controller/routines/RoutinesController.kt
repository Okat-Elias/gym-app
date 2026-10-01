package com.gymapp.controller.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.Routine
import com.gymapp.model.domain.RoutineExercise
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.RoutineRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val name: String = "",
    val notes: String = "",
    val selectedExerciseIds: List<String> = emptyList(),
    val editingRoutineId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

sealed interface RoutinesAction {
    data class ChangeName(val value: String) : RoutinesAction
    data class ChangeNotes(val value: String) : RoutinesAction
    data class AddExercise(val id: String) : RoutinesAction
    data class ReplaceExercise(val oldId: String, val newId: String) : RoutinesAction
    data class RemoveExercise(val id: String) : RoutinesAction
    // Also used by existing callers that toggle exercises in an editor.
    data class ToggleExercise(val id: String) : RoutinesAction
    data class MoveExercise(val id: String, val offset: Int) : RoutinesAction
    data class Edit(val id: String) : RoutinesAction
    data object CancelEdit : RoutinesAction
    data class Delete(val id: String) : RoutinesAction
    data object Save : RoutinesAction
}

class RoutinesController(
    exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel(), ScreenController<RoutinesUiState, RoutinesAction> {
    private val mutableState = MutableStateFlow(RoutinesUiState())
    override val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(exerciseRepository.observeExercises(), routineRepository.observeRoutines()) {
                exercises, routines -> exercises to routines
            }.catch {
                mutableState.value = mutableState.value.copy(
                    isLoading = false, errorMessage = "Impossible de charger les routines.",
                )
            }.collect { (exercises, routines) ->
                mutableState.value = mutableState.value.copy(
                    exercises = exercises, routines = routines, isLoading = false,
                )
            }
        }
    }

    override fun onAction(action: RoutinesAction) {
        val current = mutableState.value
        if (current.isSaving || current.isLoading) return
        mutableState.value = current.copy(savedMessage = null, errorMessage = null)
        when (action) {
            is RoutinesAction.ChangeName -> mutableState.value = mutableState.value.copy(name = action.value)
            is RoutinesAction.ChangeNotes -> mutableState.value = mutableState.value.copy(notes = action.value)
            is RoutinesAction.AddExercise -> {
                if (current.exercises.any { it.id == action.id } && action.id !in current.selectedExerciseIds) {
                    mutableState.value = mutableState.value.copy(
                        selectedExerciseIds = current.selectedExerciseIds + action.id,
                    )
                }
            }
            is RoutinesAction.ReplaceExercise -> {
                val index = current.selectedExerciseIds.indexOf(action.oldId)
                if (index >= 0 && current.exercises.any { it.id == action.newId } &&
                    (action.newId == action.oldId || action.newId !in current.selectedExerciseIds)
                ) {
                    mutableState.value = mutableState.value.copy(
                        selectedExerciseIds = current.selectedExerciseIds.toMutableList().apply {
                            this[index] = action.newId
                        },
                    )
                }
            }
            is RoutinesAction.RemoveExercise -> {
                mutableState.value = mutableState.value.copy(
                    selectedExerciseIds = current.selectedExerciseIds - action.id,
                )
            }
            is RoutinesAction.ToggleExercise -> {
                if (current.exercises.none { it.id == action.id }) return
                val ids = current.selectedExerciseIds
                mutableState.value = mutableState.value.copy(
                    selectedExerciseIds = if (action.id in ids) ids - action.id else ids + action.id,
                )
            }
            is RoutinesAction.MoveExercise -> {
                val ids = current.selectedExerciseIds.toMutableList()
                val from = ids.indexOf(action.id)
                val to = from + action.offset
                if (from in ids.indices && to in ids.indices) {
                    ids.add(to, ids.removeAt(from))
                    mutableState.value = mutableState.value.copy(selectedExerciseIds = ids)
                }
            }
            is RoutinesAction.Edit -> {
                val routine = current.routines.find { it.id == action.id } ?: return
                mutableState.value = current.copy(
                    editingRoutineId = routine.id, name = routine.name, notes = routine.notes,
                    selectedExerciseIds = routine.exercises.sortedBy { it.orderIndex }.map { it.exerciseId },
                    savedMessage = null, errorMessage = null,
                )
            }
            RoutinesAction.CancelEdit -> mutableState.value = current.copy(
                editingRoutineId = null, name = "", notes = "", selectedExerciseIds = emptyList(),
            )
            is RoutinesAction.Delete -> delete(action.id)
            RoutinesAction.Save -> save()
        }
    }

    private fun save() {
        val current = mutableState.value
        if (current.name.isBlank() || current.selectedExerciseIds.isEmpty()) {
            mutableState.value = current.copy(errorMessage = "Donnez un nom et choisissez au moins un exercice.")
            return
        }
        if (current.selectedExerciseIds.distinct().size != current.selectedExerciseIds.size ||
            current.selectedExerciseIds.any { id -> current.exercises.none { it.id == id } }
        ) {
            mutableState.value = current.copy(errorMessage = "La routine contient un exercice indisponible ou en double.")
            return
        }
        val now = clock()
        val original = current.routines.find { it.id == current.editingRoutineId }
        if (current.editingRoutineId != null && original == null) {
            mutableState.value = current.copy(errorMessage = "Cette routine n’existe plus.")
            return
        }
        val routine = Routine(
            id = original?.id ?: newId(), name = current.name.trim(), notes = current.notes.trim(),
            createdAtEpochMillis = original?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now.coerceAtLeast(original?.createdAtEpochMillis ?: now),
            exercises = current.selectedExerciseIds.mapIndexed { index, id ->
                RoutineExercise(original?.exercises?.find { it.exerciseId == id }?.id ?: newId(), id, index)
            },
        )
        mutableState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                routineRepository.saveRoutine(routine)
                mutableState.value = mutableState.value.copy(
                    name = "", notes = "", selectedExerciseIds = emptyList(), editingRoutineId = null,
                    isSaving = false, savedMessage = "« ${routine.name} » enregistrée. Lancez-la depuis l’onglet Séance.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false, errorMessage = "Enregistrement impossible. Votre routine reste dans le formulaire.",
                )
            }
        }
    }

    private fun delete(id: String) {
        val current = mutableState.value
        val routine = current.routines.find { it.id == id } ?: return
        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                routineRepository.deleteRoutine(id)
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    isSaving = false,
                    editingRoutineId = if (latest.editingRoutineId == id) null else latest.editingRoutineId,
                    name = if (latest.editingRoutineId == id) "" else latest.name,
                    notes = if (latest.editingRoutineId == id) "" else latest.notes,
                    selectedExerciseIds = if (latest.editingRoutineId == id) emptyList() else latest.selectedExerciseIds,
                    savedMessage = "« ${routine.name} » supprimée. Les séances passées sont conservées.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false, errorMessage = "Suppression impossible. Réessayez.",
                )
            }
        }
    }
}
