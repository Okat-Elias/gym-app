package com.gymapp.model.usecase

/** One plate denomination and the number of complete left/right pairs available. */
data class PlateInventoryItem(
    val weightGrams: Long,
    val availablePairs: Int,
) {
    init {
        require(weightGrams > 0) { "Plate weight must be positive" }
        require(availablePairs >= 0) { "availablePairs must be non-negative" }
    }
}

/** Number of pairs selected for a denomination (one plate from each pair goes on each side). */
data class PlateAllocation(
    val weightGrams: Long,
    val pairCount: Int,
) {
    init {
        require(weightGrams > 0) { "Plate weight must be positive" }
        require(pairCount > 0) { "pairCount must be positive" }
    }
}

sealed interface PlateLoadingResult {
    val targetWeightGrams: Long
    val barWeightGrams: Long

    data class Exact(
        override val targetWeightGrams: Long,
        override val barWeightGrams: Long,
        val totalWeightGrams: Long,
        val allocations: List<PlateAllocation>,
    ) : PlateLoadingResult

    /**
     * The nearest attainable symmetric load. [differenceFromTargetGrams] is signed: a positive
     * value means the proposed load is heavier than requested.
     */
    data class Closest(
        override val targetWeightGrams: Long,
        override val barWeightGrams: Long,
        val totalWeightGrams: Long,
        val differenceFromTargetGrams: Long,
        val allocations: List<PlateAllocation>,
    ) : PlateLoadingResult

    data class Impossible(
        override val targetWeightGrams: Long,
        override val barWeightGrams: Long,
        val reason: Reason,
    ) : PlateLoadingResult {
        enum class Reason {
            TARGET_BELOW_BAR,
        }
    }
}

/**
 * Finds the closest symmetric barbell loading while respecting every denomination's bounded
 * inventory. This is a bounded-knapsack search, not a greedy selection: e.g. two 3 kg pairs can
 * be selected instead of one 4 kg pair when 6 kg per side is required.
 */
class CalculatePlateLoading {
    operator fun invoke(
        targetWeightGrams: Long,
        barWeightGrams: Long,
        inventory: List<PlateInventoryItem>,
    ): PlateLoadingResult {
        require(targetWeightGrams >= 0) { "Target weight must be non-negative" }
        require(barWeightGrams >= 0) { "Bar weight must be non-negative" }

        if (targetWeightGrams < barWeightGrams) {
            return PlateLoadingResult.Impossible(
                targetWeightGrams = targetWeightGrams,
                barWeightGrams = barWeightGrams,
                reason = PlateLoadingResult.Impossible.Reason.TARGET_BELOW_BAR,
            )
        }

        val denominations = normalize(inventory)
        val maximumUsefulPerSideWeight = targetWeightGrams - barWeightGrams
        val initial = Selection(counts = IntArray(denominations.size), usedPairCount = 0)
        var reachable = linkedMapOf(0L to initial)

        denominations.forEachIndexed { index, denomination ->
            val next = linkedMapOf<Long, Selection>()

            reachable.forEach { (baseWeight, selection) ->
                val capacityForThisPlate =
                    (maximumUsefulPerSideWeight - baseWeight) / denomination.weightGrams
                val maximumCount = minOf(
                    denomination.availablePairs.toLong(),
                    capacityForThisPlate,
                ).toInt()

                for (count in 0..maximumCount) {
                    val weight = baseWeight + denomination.weightGrams * count
                    val counts = selection.counts.copyOf().also { it[index] = count }
                    val candidate = Selection(
                        counts = counts,
                        usedPairCount = selection.usedPairCount + count,
                    )
                    val existing = next[weight]
                    if (existing == null || candidate.isPreferredTo(existing)) {
                        next[weight] = candidate
                    }
                }
            }

            reachable = next
        }

        val (bestPerSideWeight, bestSelection) = reachable.entries.reduce { best, candidate ->
            if (
                candidate.isBetterFinalLoadThan(
                    other = best,
                    barWeightGrams = barWeightGrams,
                    targetWeightGrams = targetWeightGrams,
                )
            ) {
                candidate
            } else {
                best
            }
        }.let { it.key to it.value }

        val totalWeight = totalWeight(barWeightGrams, bestPerSideWeight)
        val allocations = denominations.mapIndexedNotNull { index, denomination ->
            bestSelection.counts[index]
                .takeIf { it > 0 }
                ?.let { count -> PlateAllocation(denomination.weightGrams, count) }
        }

        return if (totalWeight == targetWeightGrams) {
            PlateLoadingResult.Exact(
                targetWeightGrams = targetWeightGrams,
                barWeightGrams = barWeightGrams,
                totalWeightGrams = totalWeight,
                allocations = allocations,
            )
        } else {
            PlateLoadingResult.Closest(
                targetWeightGrams = targetWeightGrams,
                barWeightGrams = barWeightGrams,
                totalWeightGrams = totalWeight,
                differenceFromTargetGrams = totalWeight - targetWeightGrams,
                allocations = allocations,
            )
        }
    }

    private fun normalize(inventory: List<PlateInventoryItem>): List<PlateInventoryItem> {
        val pairsByWeight = linkedMapOf<Long, Int>()
        inventory.forEach { item ->
            pairsByWeight[item.weightGrams] = Math.addExact(
                pairsByWeight[item.weightGrams] ?: 0,
                item.availablePairs,
            )
        }
        return pairsByWeight
            .map { (weight, pairs) -> PlateInventoryItem(weight, pairs) }
            .filter { it.availablePairs > 0 }
            .sortedByDescending(PlateInventoryItem::weightGrams)
    }

    private fun Map.Entry<Long, Selection>.isBetterFinalLoadThan(
        other: Map.Entry<Long, Selection>,
        barWeightGrams: Long,
        targetWeightGrams: Long,
    ): Boolean {
        val total = totalWeight(barWeightGrams, key)
        val otherTotal = totalWeight(barWeightGrams, other.key)
        val distance = absoluteDistance(total, targetWeightGrams)
        val otherDistance = absoluteDistance(otherTotal, targetWeightGrams)

        if (distance != otherDistance) return distance < otherDistance

        // A lighter load is the safer deterministic choice when two loads are equally close.
        val exceedsTarget = total > targetWeightGrams
        val otherExceedsTarget = otherTotal > targetWeightGrams
        if (exceedsTarget != otherExceedsTarget) return !exceedsTarget

        return value.isPreferredTo(other.value)
    }

    private fun totalWeight(barWeightGrams: Long, perSideWeightGrams: Long): Long {
        if (perSideWeightGrams > (Long.MAX_VALUE - barWeightGrams) / SIDES) {
            return Long.MAX_VALUE
        }
        return barWeightGrams + SIDES * perSideWeightGrams
    }

    private fun absoluteDistance(first: Long, second: Long): Long =
        if (first >= second) first - second else second - first

    private data class Selection(
        val counts: IntArray,
        val usedPairCount: Int,
    ) {
        fun isPreferredTo(other: Selection): Boolean {
            if (usedPairCount != other.usedPairCount) return usedPairCount < other.usedPairCount

            // Denominations are descending, so prefer more heavy pairs as a stable final tie-break.
            counts.indices.forEach { index ->
                if (counts[index] != other.counts[index]) return counts[index] > other.counts[index]
            }
            return false
        }
    }

    private companion object {
        const val SIDES = 2L
    }
}
