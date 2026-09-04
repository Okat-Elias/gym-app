package com.gymapp.controller.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val exerciseCount: Int = 0,
    val completedWorkoutCount: Int = 0,
    val activeWorkoutCount: Int = 0,
    val completedSetCount: Int = 0,
    val errorMessage: String? = null,
)

sealed interface DashboardAction {
    data object DismissError : DashboardAction
}

class DashboardController(
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
) : ViewModel(), ScreenController<DashboardUiState, DashboardAction> {
    private val mutableState = MutableStateFlow(DashboardUiState())
    override val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                exerciseRepository.observeExercises(),
                workoutRepository.observeWorkouts(),
            ) { exercises, workouts ->
                DashboardUiState(
                    isLoading = false,
                    exerciseCount = exercises.size,
                    completedWorkoutCount = workouts.count { it.endedAtEpochMillis != null },
                    activeWorkoutCount = workouts.count { it.endedAtEpochMillis == null },
                    completedSetCount = workouts.sumOf { workout ->
                        workout.exercises.sumOf { exercise ->
                            exercise.sets.count { it.completed }
                        }
                    },
                )
            }.catch {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = "Impossible de lire les données locales.",
                )
            }.collect { dashboardState ->
                mutableState.value = dashboardState
            }
        }
    }

    override fun onAction(action: DashboardAction) {
        when (action) {
            DashboardAction.DismissError -> {
                mutableState.value = mutableState.value.copy(errorMessage = null)
            }
        }
    }
}

