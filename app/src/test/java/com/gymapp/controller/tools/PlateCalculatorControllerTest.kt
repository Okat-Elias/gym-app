package com.gymapp.controller.tools

import com.gymapp.model.usecase.CalculatePlateLoading
import com.gymapp.model.usecase.PlateInventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorControllerTest {
    private val controller = PlateCalculatorController(
        calculatePlateLoading = CalculatePlateLoading(),
        inventory = listOf(
            PlateInventoryItem(weightGrams = 20_000L, availablePairs = 4),
            PlateInventoryItem(weightGrams = 15_000L, availablePairs = 4),
            PlateInventoryItem(weightGrams = 10_000L, availablePairs = 4),
            PlateInventoryItem(weightGrams = 5_000L, availablePairs = 4),
            PlateInventoryItem(weightGrams = 2_500L, availablePairs = 4),
            PlateInventoryItem(weightGrams = 1_250L, availablePairs = 4),
        ),
    )

    @Test
    fun `starts at sixty kilograms with the twenty kilogram bar`() {
        val state = controller.state.value

        assertEquals(60_000L, state.targetWeightGrams)
        assertEquals(20_000L, state.barWeightGrams)
        assertEquals(60_000L, state.loadedWeightGrams)
        assertEquals(
            listOf(PlateAllocationUi(weightGrams = 20_000L, countPerSide = 1)),
            state.allocations,
        )
        assertTrue(state.status is PlateCalculationStatus.Exact)
    }

    @Test
    fun `changes the total target by two point five kilograms`() {
        controller.onAction(PlateCalculatorAction.IncreaseTarget)

        assertEquals(62_500L, controller.state.value.targetWeightGrams)
        assertEquals(62_500L, controller.state.value.loadedWeightGrams)

        controller.onAction(PlateCalculatorAction.DecreaseTarget)

        assertEquals(60_000L, controller.state.value.targetWeightGrams)
    }

    @Test
    fun `selects a fifteen kilogram bar without changing a valid target`() {
        controller.onAction(PlateCalculatorAction.SelectFifteenKilogramBar)

        val state = controller.state.value
        assertEquals(60_000L, state.targetWeightGrams)
        assertEquals(15_000L, state.barWeightGrams)
        assertEquals(60_000L, state.loadedWeightGrams)
        assertTrue(state.status is PlateCalculationStatus.Exact)
    }

    @Test
    fun `does not decrement the target below the selected bar`() {
        repeat(30) {
            controller.onAction(PlateCalculatorAction.DecreaseTarget)
        }

        assertEquals(20_000L, controller.state.value.targetWeightGrams)
        assertEquals(20_000L, controller.state.value.loadedWeightGrams)
        assertTrue(controller.state.value.allocations.isEmpty())
    }

    @Test
    fun `reset restores the default target and bar`() {
        controller.onAction(PlateCalculatorAction.SelectFifteenKilogramBar)
        controller.onAction(PlateCalculatorAction.IncreaseTarget)

        controller.onAction(PlateCalculatorAction.Reset)

        assertEquals(60_000L, controller.state.value.targetWeightGrams)
        assertEquals(20_000L, controller.state.value.barWeightGrams)
    }
}
