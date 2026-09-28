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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

sealed interface RoutinesAction {
    data class ChangeName(val value: String) : RoutinesAction
    data class ChangeNotes(val value: String) : RoutinesAction
    data class ToggleExercise(val id: String) : RoutinesAction
    data class MoveExercise(val id: String, val offset: Int) : RoutinesAction
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
            RoutinesAction.Save -> save()
        }
    }

    private fun save() {
        val current = mutableState.value
        if (current.name.isBlank() || current.selectedExerciseIds.isEmpty()) {
            mutableState.value = current.copy(errorMessage = "Donnez un nom et choisissez au moins un exercice.")
            return
        }
        val now = clock()
        val routine = Routine(
            id = newId(), name = current.name.trim(), notes = current.notes.trim(),
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
            exercises = current.selectedExerciseIds.mapIndexed { index, id ->
                RoutineExercise(newId(), id, index)
            },
        )
        mutableState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                routineRepository.saveRoutine(routine)
                mutableState.value = mutableState.value.copy(
                    name = "", notes = "", selectedExerciseIds = emptyList(), isSaving = false,
                    savedMessage = "« ${routine.name} » enregistrée. Lancez-la depuis l’onglet Séance.",
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
}
