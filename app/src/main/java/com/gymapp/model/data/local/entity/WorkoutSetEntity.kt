package com.gymapp.model.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workout_exercise_id", "order_index"], unique = true),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "workout_exercise_id")
    val workoutExerciseId: String,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    val type: String,
    @ColumnInfo(name = "weight_grams")
    val weightGrams: Long,
    val repetitions: Int,
    @ColumnInfo(name = "rpe_tenths")
    val rpeTenths: Int? = null,
    val rir: Int? = null,
    val completed: Boolean = false,
)
