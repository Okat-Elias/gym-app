package com.gymapp.model.domain

data class WorkoutExercise(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val supersetGroupId: String? = null,
    val sets: List<WorkoutSet> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Workout exercise id must not be blank" }
        require(exerciseId.isNotBlank()) { "Exercise id must not be blank" }
        require(orderIndex >= 0) { "Workout exercise orderIndex must be non-negative" }
        require(supersetGroupId == null || supersetGroupId.isNotBlank()) {
            "Superset group id must be null or non-blank"
        }
        require(sets.map(WorkoutSet::id).distinct().size == sets.size) {
            "Workout set ids must be unique within an exercise"
        }
        require(sets.map(WorkoutSet::orderIndex).distinct().size == sets.size) {
            "Workout set order indexes must be unique within an exercise"
        }
    }
}
