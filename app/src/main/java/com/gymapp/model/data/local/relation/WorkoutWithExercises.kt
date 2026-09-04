package com.gymapp.model.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.gymapp.model.data.local.entity.WorkoutEntity
import com.gymapp.model.data.local.entity.WorkoutExerciseEntity
import com.gymapp.model.data.local.entity.WorkoutSetEntity

data class WorkoutExerciseWithSets(
    @Embedded
    val workoutExercise: WorkoutExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workout_exercise_id",
    )
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutWithExercises(
    @Embedded
    val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workout_id",
    )
    val exercises: List<WorkoutExerciseWithSets>,
)
