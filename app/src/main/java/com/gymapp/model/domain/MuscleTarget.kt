package com.gymapp.model.domain

/**
 * Contribution of one completed working set to a muscle.
 *
 * A value of 10,000 represents one full effective set and 5,000 represents half a set. A
 * muscle with no contribution is omitted instead of being represented by a zero-valued target.
 */
data class MuscleTarget(
    val muscleGroup: MuscleGroup,
    val contributionBasisPoints: Int,
) {
    init {
        require(contributionBasisPoints in MIN_CONTRIBUTION..MAX_CONTRIBUTION) {
            "contributionBasisPoints must be between $MIN_CONTRIBUTION and $MAX_CONTRIBUTION"
        }
    }

    companion object {
        const val MIN_CONTRIBUTION = 1
        const val MAX_CONTRIBUTION = 10_000
    }
}
