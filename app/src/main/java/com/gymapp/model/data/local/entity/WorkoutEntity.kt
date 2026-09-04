package com.gymapp.model.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["routine_id"]),
        Index(value = ["started_at_epoch_millis"]),
    ],
)
data class WorkoutEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "routine_id")
    val routineId: String? = null,
    @ColumnInfo(name = "started_at_epoch_millis")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis")
    val endedAtEpochMillis: Long? = null,
    val notes: String = "",
)
