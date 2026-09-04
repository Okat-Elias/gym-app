package com.gymapp.model.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.gymapp.model.data.local.entity.RoutineEntity
import com.gymapp.model.data.local.entity.RoutineExerciseEntity

data class RoutineWithExercises(
    @Embedded
    val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routine_id",
    )
    val exercises: List<RoutineExerciseEntity>,
)
