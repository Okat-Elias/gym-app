package com.gymapp.model.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatePlateLoadingTest {
    private val calculate = CalculatePlateLoading()

    @Test
    fun `finds an exact bounded solution that a greedy algorithm misses`() {
        val result = calculate(
            targetWeightGrams = 32_000,
            barWeightGrams = 20_000,
            inventory = listOf(
                PlateInventoryItem(weightGrams = 4_000, availablePairs = 1),
                PlateInventoryItem(weightGrams = 3_000, availablePairs = 2),
            ),
        )

        assertTrue(result is PlateLoadingResult.Exact)
        result as PlateLoadingResult.Exact
        assertEquals(32_000, result.totalWeightGrams)
        assertEquals(listOf(PlateAllocation(weightGrams = 3_000, pairCount = 2)), result.allocations)
    }

    @Test
    fun `never uses more pairs than are available`() {
        val result = calculate(
            targetWeightGrams = 40_000,
            barWeightGrams = 20_000,
            inventory = listOf(PlateInventoryItem(weightGrams = 5_000, availablePairs = 1)),
        )

        assertTrue(result is PlateLoadingResult.Closest)
        result as PlateLoadingResult.Closest
        assertEquals(30_000, result.totalWeightGrams)
        assertEquals(-10_000, result.differenceFromTargetGrams)
        assertEquals(listOf(PlateAllocation(weightGrams = 5_000, pairCount = 1)), result.allocations)
    }

    @Test
    fun `can return a closer load above the target`() {
        val result = calculate(
            targetWeightGrams = 28_000,
            barWeightGrams = 20_000,
            inventory = listOf(PlateInventoryItem(weightGrams = 5_000, availablePairs = 1)),
        ) as PlateLoadingResult.Closest

        assertEquals(30_000, result.totalWeightGrams)
        assertEquals(2_000, result.differenceFromTargetGrams)
    }

    @Test
    fun `prefers the lighter load when two possibilities are equally close`() {
        val result = calculate(
            targetWeightGrams = 25_000,
            barWeightGrams = 20_000,
            inventory = listOf(PlateInventoryItem(weightGrams = 5_000, availablePairs = 1)),
        ) as PlateLoadingResult.Closest

        assertEquals(20_000, result.totalWeightGrams)
        assertTrue(result.allocations.isEmpty())
    }

    @Test
    fun `combines duplicate denominations before applying the bound`() {
        val result = calculate(
            targetWeightGrams = 30_000,
            barWeightGrams = 20_000,
            inventory = listOf(
                PlateInventoryItem(weightGrams = 2_500, availablePairs = 1),
                PlateInventoryItem(weightGrams = 2_500, availablePairs = 1),
            ),
        ) as PlateLoadingResult.Exact

        assertEquals(listOf(PlateAllocation(weightGrams = 2_500, pairCount = 2)), result.allocations)
    }

    @Test
    fun `returns an empty exact loading when target equals bar`() {
        val result = calculate(
            targetWeightGrams = 20_000,
            barWeightGrams = 20_000,
            inventory = emptyList(),
        ) as PlateLoadingResult.Exact

        assertEquals(20_000, result.totalWeightGrams)
        assertTrue(result.allocations.isEmpty())
    }

    @Test
    fun `returns impossible when target is below the empty bar`() {
        val result = calculate(
            targetWeightGrams = 15_000,
            barWeightGrams = 20_000,
            inventory = emptyList(),
        )

        assertEquals(
            PlateLoadingResult.Impossible(
                targetWeightGrams = 15_000,
                barWeightGrams = 20_000,
                reason = PlateLoadingResult.Impossible.Reason.TARGET_BELOW_BAR,
            ),
            result,
        )
    }
}
