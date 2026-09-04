package com.gymapp.controller.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.domain.WorkoutSetType
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.WorkoutRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val exercises: List<Exercise> = emptyList(),
    val selectedExerciseId: String? = null,
    val workoutId: String? = null,
    val workoutExerciseId: String? = null,
    val startedAtEpochMillis: Long? = null,
    val weightGrams: Long = 20_000,
    val repetitions: Int = 8,
    val rpe: Double = 8.0,
    val loggedSets: List<WorkoutSet> = emptyList(),
    val isLoadingExercises: Boolean = true,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface WorkoutAction {
    data class SelectExercise(val exerciseId: String) : WorkoutAction
    data class ChangeWeight(val deltaGrams: Long) : WorkoutAction
    data class ChangeRepetitions(val delta: Int) : WorkoutAction
    data class ChangeRpe(val delta: Double) : WorkoutAction
    data object LogSet : WorkoutAction
    data object FinishWorkout : WorkoutAction
    data object StartNewWorkout : WorkoutAction
    data object DismissError : WorkoutAction
}

class WorkoutController(
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel(), ScreenController<WorkoutUiState, WorkoutAction> {
    private val mutableState = MutableStateFlow(WorkoutUiState())
    override val state: StateFlow<WorkoutUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            exerciseRepository.observeExercises()
                .catch {
                    mutableState.value = mutableState.value.copy(
                        isLoadingExercises = false,
                        errorMessage = "Le catalogue d'exercices est indisponible.",
                    )
                }
                .collect { exercises ->
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        exercises = exercises,
                        selectedExerciseId = current.selectedExerciseId
                            ?.takeIf { selected -> exercises.any { it.id == selected } }
                            ?: exercises.firstOrNull()?.id,
                        isLoadingExercises = false,
                    )
                }
        }

        viewModelScope.launch {
            workoutRepository.observeWorkouts()
                .catch {
                    mutableState.value = mutableState.value.copy(
                        errorMessage = "La séance en cours n'a pas pu être restaurée.",
                    )
                }
                .collect { workouts ->
                    val current = mutableState.value
                    val activeWorkout = workouts.firstOrNull { it.endedAtEpochMillis == null }
                    val activeExercise = activeWorkout?.exercises?.minByOrNull { it.orderIndex }
                    if (
                        activeWorkout != null &&
                        activeExercise != null &&
                        current.workoutId == null &&
                        !current.isFinished
                    ) {
                        mutableState.value = current.copy(
                            selectedExerciseId = activeExercise.exerciseId,
                            workoutId = activeWorkout.id,
                            workoutExerciseId = activeExercise.id,
                            startedAtEpochMillis = activeWorkout.startedAtEpochMillis,
                            loggedSets = activeExercise.sets,
                        )
                    }
                }
        }
    }

    override fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.SelectExercise -> selectExercise(action.exerciseId)
            is WorkoutAction.ChangeWeight -> changeWeight(action.deltaGrams)
            is WorkoutAction.ChangeRepetitions -> changeRepetitions(action.delta)
            is WorkoutAction.ChangeRpe -> changeRpe(action.delta)
            WorkoutAction.LogSet -> logSet()
            WorkoutAction.FinishWorkout -> finishWorkout()
            WorkoutAction.StartNewWorkout -> resetWorkout()
            WorkoutAction.DismissError -> {
                mutableState.value = mutableState.value.copy(errorMessage = null)
            }
        }
    }

    private fun selectExercise(exerciseId: String) {
        val current = mutableState.value
        if (
            !current.isSaving &&
            !current.isFinished &&
            current.loggedSets.isEmpty() &&
            current.exercises.any { it.id == exerciseId }
        ) {
            mutableState.value = current.copy(selectedExerciseId = exerciseId)
        }
    }

    private fun changeWeight(deltaGrams: Long) {
        val current = mutableState.value
        if (current.isSaving || current.isFinished) return
        mutableState.value = current.copy(
            weightGrams = (current.weightGrams + deltaGrams).coerceIn(0, 1_000_000),
        )
    }

    private fun changeRepetitions(delta: Int) {
        val current = mutableState.value
        if (current.isSaving || current.isFinished) return
        mutableState.value = current.copy(
            repetitions = (current.repetitions + delta).coerceIn(0, 100),
        )
    }

    private fun changeRpe(delta: Double) {
        val current = mutableState.value
        if (current.isSaving || current.isFinished) return
        mutableState.value = current.copy(
            rpe = (current.rpe + delta).coerceIn(1.0, 10.0),
        )
    }

    private fun logSet() {
        val current = mutableState.value
        val exerciseId = current.selectedExerciseId ?: return
        if (current.isSaving || current.isFinished || current.repetitions <= 0) return

        val workoutId = current.workoutId ?: newId()
        val workoutExerciseId = current.workoutExerciseId ?: newId()
        val startedAt = current.startedAtEpochMillis ?: clock()
        val newSet = WorkoutSet(
            id = newId(),
            orderIndex = current.loggedSets.size,
            type = WorkoutSetType.WORKING,
            weightGrams = current.weightGrams,
            repetitions = current.repetitions,
            rpe = current.rpe,
            completed = true,
        )
        val updatedSets = current.loggedSets + newSet
        val workout = buildWorkout(
            workoutId = workoutId,
            workoutExerciseId = workoutExerciseId,
            exerciseId = exerciseId,
            startedAt = startedAt,
            sets = updatedSets,
        )

        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { workoutRepository.saveWorkout(workout) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        workoutId = workoutId,
                        workoutExerciseId = workoutExerciseId,
                        startedAtEpochMillis = startedAt,
                        loggedSets = updatedSets,
                        isSaving = false,
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        errorMessage = "La série n'a pas pu être enregistrée.",
                    )
                }
        }
    }

    private fun finishWorkout() {
        val current = mutableState.value
        val workoutId = current.workoutId ?: return
        val workoutExerciseId = current.workoutExerciseId ?: return
        val exerciseId = current.selectedExerciseId ?: return
        val startedAt = current.startedAtEpochMillis ?: return
        if (current.isSaving || current.loggedSets.isEmpty() || current.isFinished) return

        val workout = buildWorkout(
            workoutId = workoutId,
            workoutExerciseId = workoutExerciseId,
            exerciseId = exerciseId,
            startedAt = startedAt,
            sets = current.loggedSets,
            endedAt = clock(),
        )

        mutableState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { workoutRepository.saveWorkout(workout) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        isFinished = true,
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        errorMessage = "La séance n'a pas pu être terminée.",
                    )
                }
        }
    }

    private fun resetWorkout() {
        val current = mutableState.value
        if (current.isSaving) return
        mutableState.value = WorkoutUiState(
            exercises = current.exercises,
            selectedExerciseId = current.exercises.firstOrNull()?.id,
            isLoadingExercises = false,
        )
    }

    private fun buildWorkout(
        workoutId: String,
        workoutExerciseId: String,
        exerciseId: String,
        startedAt: Long,
        sets: List<WorkoutSet>,
        endedAt: Long? = null,
    ): WorkoutSession = WorkoutSession(
        id = workoutId,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = endedAt,
        exercises = listOf(
            WorkoutExercise(
                id = workoutExerciseId,
                exerciseId = exerciseId,
                orderIndex = 0,
                sets = sets,
            ),
        ),
    )
}
