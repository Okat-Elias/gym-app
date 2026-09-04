package com.gymapp.model.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.gymapp.model.data.local.entity.ExerciseEntity
import com.gymapp.model.data.local.entity.MuscleTargetEntity

data class ExerciseWithTargets(
    @Embedded
    val exercise: ExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "exercise_id",
    )
    val muscleTargets: List<MuscleTargetEntity>,
)
