package com.gymapp.controller.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.controller.ScreenController
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.repository.ExerciseRepository
import com.gymapp.model.repository.WorkoutRepository
import com.gymapp.model.usecase.CalculateWeeklyMuscleVolume
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MuscleVolumeUi(
    val muscleGroup: MuscleGroup,
    val effectiveSetBasisPoints: Int,
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val volumes: List<MuscleVolumeUi> = emptyList(),
    val errorMessage: String? = null,
)

sealed interface StatisticsAction {
    data object DismissError : StatisticsAction
}

class StatisticsController(
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    private val calculateWeeklyMuscleVolume: CalculateWeeklyMuscleVolume,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel(), ScreenController<StatisticsUiState, StatisticsAction> {
    private val mutableState = MutableStateFlow(StatisticsUiState())
    override val state: StateFlow<StatisticsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                exerciseRepository.observeExercises(),
                workoutRepository.observeWorkouts(),
            ) { exercises, workouts ->
                val (weekStart, weekEnd) = currentWeekBounds()
                val volumes = calculateWeeklyMuscleVolume(
                    sessions = workouts,
                    exercisesById = exercises.associateBy { it.id },
                    weekStartEpochMillis = weekStart,
                    weekEndExclusiveEpochMillis = weekEnd,
                )
                StatisticsUiState(
                    isLoading = false,
                    volumes = volumes
                        .map { (muscle, value) -> MuscleVolumeUi(muscle, value) }
                        .sortedByDescending(MuscleVolumeUi::effectiveSetBasisPoints),
                )
            }.catch {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = "Les statistiques locales sont indisponibles.",
                )
            }.collect { statistics ->
                mutableState.value = statistics
            }
        }
    }

    override fun onAction(action: StatisticsAction) {
        when (action) {
            StatisticsAction.DismissError -> {
                mutableState.value = mutableState.value.copy(errorMessage = null)
            }
        }
    }

    private fun currentWeekBounds(): Pair<Long, Long> {
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock()), zoneId)
        val monday = today.with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }
}

