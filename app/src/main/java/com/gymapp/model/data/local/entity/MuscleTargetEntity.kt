package com.gymapp.model.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "muscle_targets",
    primaryKeys = ["exercise_id", "muscle_group"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["muscle_group"])],
)
data class MuscleTargetEntity(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    @ColumnInfo(name = "muscle_group")
    val muscleGroup: String,
    @ColumnInfo(name = "contribution_basis_points")
    val contributionBasisPoints: Int,
)
