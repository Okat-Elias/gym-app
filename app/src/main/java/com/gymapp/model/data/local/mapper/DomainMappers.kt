package com.gymapp.model.data.local.mapper

import com.gymapp.model.data.local.entity.ExerciseEntity
import com.gymapp.model.data.local.entity.MuscleTargetEntity
import com.gymapp.model.data.local.entity.WorkoutEntity
import com.gymapp.model.data.local.entity.WorkoutExerciseEntity
import com.gymapp.model.data.local.entity.WorkoutSetEntity
import com.gymapp.model.data.local.relation.ExerciseWithTargets
import com.gymapp.model.data.local.relation.WorkoutExerciseWithSets
import com.gymapp.model.data.local.relation.WorkoutWithExercises
import com.gymapp.model.domain.Exercise
import com.gymapp.model.domain.MuscleGroup
import com.gymapp.model.domain.MuscleTarget
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSession
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.domain.WorkoutSetType
import kotlin.math.roundToInt

internal fun ExerciseWithTargets.toDomain(): Exercise = Exercise(
    id = exercise.id,
    name = exercise.name,
    notes = exercise.notes,
    isCustom = exercise.isCustom,
    muscleTargets = muscleTargets
        .sortedBy { it.muscleGroup }
        .map(MuscleTargetEntity::toDomain),
)

internal fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    notes = notes,
    isCustom = isCustom,
)

internal fun MuscleTargetEntity.toDomain(): MuscleTarget = MuscleTarget(
    muscleGroup = MuscleGroup.valueOf(muscleGroup),
    contributionBasisPoints = contributionBasisPoints,
)

internal fun MuscleTarget.toEntity(exerciseId: String): MuscleTargetEntity =
    MuscleTargetEntity(
        exerciseId = exerciseId,
        muscleGroup = muscleGroup.name,
        contributionBasisPoints = contributionBasisPoints,
    )

internal fun WorkoutWithExercises.toDomain(): WorkoutSession = WorkoutSession(
    id = workout.id,
    routineId = workout.routineId,
    startedAtEpochMillis = workout.startedAtEpochMillis,
    endedAtEpochMillis = workout.endedAtEpochMillis,
    notes = workout.notes,
    exercises = exercises
        .sortedBy { it.workoutExercise.orderIndex }
        .map(WorkoutExerciseWithSets::toDomain),
)

internal fun WorkoutSession.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    routineId = routineId,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    notes = notes,
)

internal fun WorkoutExerciseWithSets.toDomain(): WorkoutExercise = WorkoutExercise(
    id = workoutExercise.id,
    exerciseId = workoutExercise.exerciseId,
    orderIndex = workoutExercise.orderIndex,
    supersetGroupId = workoutExercise.supersetGroupId,
    sets = sets.sortedBy { it.orderIndex }.map(WorkoutSetEntity::toDomain),
)

internal fun WorkoutExercise.toEntity(workoutId: String): WorkoutExerciseEntity =
    WorkoutExerciseEntity(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = supersetGroupId,
    )

internal fun WorkoutSetEntity.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    orderIndex = orderIndex,
    type = WorkoutSetType.valueOf(type),
    weightGrams = weightGrams,
    repetitions = repetitions,
    rpe = rpeTenths?.div(10.0),
    rir = rir,
    completed = completed,
)

internal fun WorkoutSet.toEntity(workoutExerciseId: String): WorkoutSetEntity =
    WorkoutSetEntity(
        id = id,
        workoutExerciseId = workoutExerciseId,
        orderIndex = orderIndex,
        type = type.name,
        weightGrams = weightGrams,
        repetitions = repetitions,
        rpeTenths = rpe?.times(10.0)?.roundToInt(),
        rir = rir,
        completed = completed,
    )
