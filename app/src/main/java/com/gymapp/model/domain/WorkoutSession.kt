package com.gymapp.model.domain

data class WorkoutSession(
    val id: String,
    val routineId: String? = null,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val notes: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Workout session id must not be blank" }
        require(routineId == null || routineId.isNotBlank()) {
            "Routine id must be null or non-blank"
        }
        require(startedAtEpochMillis >= 0) { "Session start time must be non-negative" }
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis) {
            "Session end time must not precede its start time"
        }
        require(exercises.map(WorkoutExercise::id).distinct().size == exercises.size) {
            "Workout exercise ids must be unique within a session"
        }
        require(exercises.map(WorkoutExercise::orderIndex).distinct().size == exercises.size) {
            "Workout exercise order indexes must be unique within a session"
        }
    }
}
