package com.gymapp.model.domain

data class WorkoutSet(
    val id: String,
    val orderIndex: Int,
    val type: WorkoutSetType = WorkoutSetType.WORKING,
    val weightGrams: Long,
    val repetitions: Int,
    val rpe: Double? = null,
    val rir: Int? = null,
    val completed: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Workout set id must not be blank" }
        require(orderIndex >= 0) { "Workout set orderIndex must be non-negative" }
        require(weightGrams >= 0) { "Workout set weightGrams must be non-negative" }
        require(repetitions >= 0) { "Workout set repetitions must be non-negative" }
        require(rpe == null || (rpe.isFinite() && rpe in MIN_RPE..MAX_RPE)) {
            "RPE must be finite and between $MIN_RPE and $MAX_RPE"
        }
        require(rir == null || rir in MIN_RIR..MAX_RIR) {
            "RIR must be between $MIN_RIR and $MAX_RIR"
        }
    }

    companion object {
        const val MIN_RPE = 1.0
        const val MAX_RPE = 10.0
        const val MIN_RIR = 0
        const val MAX_RIR = 10
    }
}
