package com.gymapp.model.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymapp.model.data.local.dao.ExerciseDao
import com.gymapp.model.data.local.dao.RoutineDao
import com.gymapp.model.data.local.dao.WorkoutDao
import com.gymapp.model.data.local.entity.ExerciseEntity
import com.gymapp.model.data.local.entity.MuscleTargetEntity
import com.gymapp.model.data.local.entity.RoutineEntity
import com.gymapp.model.data.local.entity.RoutineExerciseEntity
import com.gymapp.model.data.local.entity.WorkoutEntity
import com.gymapp.model.data.local.entity.WorkoutExerciseEntity
import com.gymapp.model.data.local.entity.WorkoutSetEntity

@Database(
    entities = [
        ExerciseEntity::class,
        MuscleTargetEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun workoutDao(): WorkoutDao
}
