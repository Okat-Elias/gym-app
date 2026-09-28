package com.gymapp.model.domain

/** A reusable plan. Completed sets belong to WorkoutSession, never to this template. */
data class Routine(
    val id: String,
    val name: String,
    val notes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val exercises: List<RoutineExercise>,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis)
        require(exercises.isNotEmpty())
        require(exercises.map { it.id }.distinct().size == exercises.size)
        require(exercises.map { it.orderIndex }.distinct().size == exercises.size)
    }
}

data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val supersetGroupId: String? = null,
) {
    init {
        require(id.isNotBlank() && exerciseId.isNotBlank() && orderIndex >= 0)
    }
}
