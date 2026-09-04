package com.gymapp.controller.tools

import androidx.lifecycle.ViewModel
import com.gymapp.controller.ScreenController
import com.gymapp.model.usecase.CalculatePlateLoading
import com.gymapp.model.usecase.PlateInventoryItem
import com.gymapp.model.usecase.PlateLoadingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlateAllocationUi(
    val weightGrams: Long,
    val countPerSide: Int,
)

sealed interface PlateCalculationStatus {
    data object Exact : PlateCalculationStatus

    data class Closest(
        val differenceFromTargetGrams: Long,
    ) : PlateCalculationStatus

    data object TargetBelowBar : PlateCalculationStatus
}

data class PlateCalculatorUiState(
    val targetWeightGrams: Long,
    val barWeightGrams: Long,
    val loadedWeightGrams: Long?,
    val allocations: List<PlateAllocationUi>,
    val status: PlateCalculationStatus,
)

sealed interface PlateCalculatorAction {
    data object IncreaseTarget : PlateCalculatorAction

    data object DecreaseTarget : PlateCalculatorAction

    data object SelectTwentyKilogramBar : PlateCalculatorAction

    data object SelectFifteenKilogramBar : PlateCalculatorAction

    data object Reset : PlateCalculatorAction
}

class PlateCalculatorController(
    private val calculatePlateLoading: CalculatePlateLoading = CalculatePlateLoading(),
    private val inventory: List<PlateInventoryItem> = defaultInventory(),
) : ViewModel(), ScreenController<PlateCalculatorUiState, PlateCalculatorAction> {
    private val mutableState = MutableStateFlow(
        calculateState(
            targetWeightGrams = DEFAULT_TARGET_WEIGHT_GRAMS,
            barWeightGrams = DEFAULT_BAR_WEIGHT_GRAMS,
        ),
    )
    override val state: StateFlow<PlateCalculatorUiState> = mutableState.asStateFlow()

    override fun onAction(action: PlateCalculatorAction) {
        when (action) {
            PlateCalculatorAction.IncreaseTarget -> updateWeights(
                targetWeightGrams = incrementTarget(mutableState.value.targetWeightGrams),
                barWeightGrams = mutableState.value.barWeightGrams,
            )

            PlateCalculatorAction.DecreaseTarget -> updateWeights(
                targetWeightGrams = (mutableState.value.targetWeightGrams - TARGET_STEP_GRAMS)
                    .coerceAtLeast(mutableState.value.barWeightGrams),
                barWeightGrams = mutableState.value.barWeightGrams,
            )

            PlateCalculatorAction.SelectTwentyKilogramBar -> selectBar(
                barWeightGrams = TWENTY_KILOGRAM_BAR_GRAMS,
            )

            PlateCalculatorAction.SelectFifteenKilogramBar -> selectBar(
                barWeightGrams = FIFTEEN_KILOGRAM_BAR_GRAMS,
            )

            PlateCalculatorAction.Reset -> updateWeights(
                targetWeightGrams = DEFAULT_TARGET_WEIGHT_GRAMS,
                barWeightGrams = DEFAULT_BAR_WEIGHT_GRAMS,
            )
        }
    }

    private fun selectBar(barWeightGrams: Long) {
        updateWeights(
            targetWeightGrams = mutableState.value.targetWeightGrams.coerceAtLeast(barWeightGrams),
            barWeightGrams = barWeightGrams,
        )
    }

    private fun updateWeights(
        targetWeightGrams: Long,
        barWeightGrams: Long,
    ) {
        mutableState.value = calculateState(
            targetWeightGrams = targetWeightGrams,
            barWeightGrams = barWeightGrams,
        )
    }

    private fun calculateState(
        targetWeightGrams: Long,
        barWeightGrams: Long,
    ): PlateCalculatorUiState = when (
        val result = calculatePlateLoading(
            targetWeightGrams = targetWeightGrams,
            barWeightGrams = barWeightGrams,
            inventory = inventory,
        )
    ) {
        is PlateLoadingResult.Exact -> result.toUiState(PlateCalculationStatus.Exact)
        is PlateLoadingResult.Closest -> result.toUiState(
            PlateCalculationStatus.Closest(result.differenceFromTargetGrams),
        )

        is PlateLoadingResult.Impossible -> PlateCalculatorUiState(
            targetWeightGrams = result.targetWeightGrams,
            barWeightGrams = result.barWeightGrams,
            loadedWeightGrams = null,
            allocations = emptyList(),
            status = PlateCalculationStatus.TargetBelowBar,
        )
    }

    private fun PlateLoadingResult.Exact.toUiState(
        status: PlateCalculationStatus,
    ) = PlateCalculatorUiState(
        targetWeightGrams = targetWeightGrams,
        barWeightGrams = barWeightGrams,
        loadedWeightGrams = totalWeightGrams,
        allocations = allocations.map { allocation ->
            PlateAllocationUi(
                weightGrams = allocation.weightGrams,
                countPerSide = allocation.pairCount,
            )
        },
        status = status,
    )

    private fun PlateLoadingResult.Closest.toUiState(
        status: PlateCalculationStatus,
    ) = PlateCalculatorUiState(
        targetWeightGrams = targetWeightGrams,
        barWeightGrams = barWeightGrams,
        loadedWeightGrams = totalWeightGrams,
        allocations = allocations.map { allocation ->
            PlateAllocationUi(
                weightGrams = allocation.weightGrams,
                countPerSide = allocation.pairCount,
            )
        },
        status = status,
    )

    private fun incrementTarget(currentTargetWeightGrams: Long): Long =
        if (currentTargetWeightGrams > Long.MAX_VALUE - TARGET_STEP_GRAMS) {
            currentTargetWeightGrams
        } else {
            currentTargetWeightGrams + TARGET_STEP_GRAMS
        }

    companion object {
        const val TARGET_STEP_GRAMS = 2_500L
        const val TWENTY_KILOGRAM_BAR_GRAMS = 20_000L
        const val FIFTEEN_KILOGRAM_BAR_GRAMS = 15_000L
        const val DEFAULT_TARGET_WEIGHT_GRAMS = 60_000L
        const val DEFAULT_BAR_WEIGHT_GRAMS = TWENTY_KILOGRAM_BAR_GRAMS

        private const val DEFAULT_AVAILABLE_PAIRS = 4

        private fun defaultInventory(): List<PlateInventoryItem> = listOf(
            PlateInventoryItem(weightGrams = 20_000L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
            PlateInventoryItem(weightGrams = 15_000L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
            PlateInventoryItem(weightGrams = 10_000L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
            PlateInventoryItem(weightGrams = 5_000L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
            PlateInventoryItem(weightGrams = 2_500L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
            PlateInventoryItem(weightGrams = 1_250L, availablePairs = DEFAULT_AVAILABLE_PAIRS),
        )
    }
}
