package com.gymapp.model.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"])],
)
data class ExerciseEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val notes: String = "",
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,
)
