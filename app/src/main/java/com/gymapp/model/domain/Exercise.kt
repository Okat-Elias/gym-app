package com.gymapp.model.domain

data class Exercise(
    val id: String,
    val name: String,
    val notes: String = "",
    val isCustom: Boolean = false,
    val muscleTargets: List<MuscleTarget> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Exercise id must not be blank" }
        require(name.isNotBlank()) { "Exercise name must not be blank" }
        require(muscleTargets.map(MuscleTarget::muscleGroup).distinct().size == muscleTargets.size) {
            "An exercise can contain at most one target per muscle group"
        }
    }
}
