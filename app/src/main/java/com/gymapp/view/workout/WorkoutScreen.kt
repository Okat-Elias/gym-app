package com.gymapp.view.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.workout.SetDraft
import com.gymapp.controller.workout.WorkoutAction
import com.gymapp.controller.workout.WorkoutController
import com.gymapp.controller.workout.WorkoutUiState
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.usecase.WorkoutSummary
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutRoute(controller: WorkoutController, onCreateRoutine: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    WorkoutScreen(state, controller::onAction, onCreateRoutine)
}

@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onAction: (WorkoutAction) -> Unit,
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val workout = state.workout
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                if (workout == null) "Lancer une séance" else if (state.isActive) "Séance en cours" else "Bilan de séance",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (state.isLoading) {
            item { CircularProgressIndicator() }
        } else if (workout == null) {
            item { Text("Choisissez une routine. Le chronomètre démarre lorsque vous lancez la séance.") }
            if (state.routines.isEmpty()) {
                item {
                    Text("Créez votre première routine pour préparer vos exercices.")
                    Button(onClick = onCreateRoutine) { Text("Créer une routine") }
                }
            }
            items(state.routines, key = { "routine-${it.id}" }) { routine ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        FilterChip(
                            selected = state.selectedRoutineId == routine.id,
                            onClick = { onAction(WorkoutAction.SelectRoutine(routine.id)) },
                            label = { Text(routine.name) }, enabled = state.isReady && !state.isSaving,
                        )
                        routine.exercises.sortedBy { it.orderIndex }.forEachIndexed { index, exercise ->
                            Text("${index + 1}. ${state.exercises.find { it.id == exercise.exerciseId }?.name ?: "Exercice"}")
                        }
                        if (routine.notes.isNotBlank()) Text(routine.notes)
                    }
                }
            }
            item {
                Button(
                    onClick = { onAction(WorkoutAction.StartWorkout) },
                    enabled = state.isReady && !state.isSaving && state.selectedRoutineId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isSaving) "Démarrage…" else "Lancer la séance") }
            }
            if (state.history.isNotEmpty()) {
                item { Text("Séances enregistrées", style = MaterialTheme.typography.titleLarge) }
                items(state.history, key = { "history-${it.id}" }) { session ->
                    TextButton(
                        onClick = { onAction(WorkoutAction.ViewHistory(session.id)) },
                        enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text("${state.routines.find { it.id == session.routineId }?.name ?: "Séance libre"} · ${formatDate(session.startedAtEpochMillis)}")
                    }
                }
            }
        } else {
            item {
                Text(state.routines.find { it.id == workout.routineId }?.name ?: "Séance libre",
                    style = MaterialTheme.typography.titleLarge)
                Text(formatDate(workout.startedAtEpochMillis))
                state.summary?.let { SummaryCard(it, state.isActive) }
            }
            if (!state.isActive) {
                item {
                    Text("Séance enregistrée", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { onAction(WorkoutAction.StartNewWorkout) }) {
                        Text("Retour aux séances")
                    }
                }
            }
            items(workout.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    name = state.exercises.find { it.id == exercise.exerciseId }?.name ?: "Exercice",
                    exercise = exercise, draft = state.drafts[exercise.id] ?: SetDraft(),
                    active = state.isActive, enabled = state.isReady && !state.isSaving,
                    onAction = onAction, errorMessage = state.errorMessage,
                )
            }
            item {
                if (state.isActive) {
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = state.notes, onValueChange = { onAction(WorkoutAction.ChangeNotes(it)) },
                        label = { Text("Notes de séance (facultatif)") },
                        enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { confirmFinish = true }, enabled = state.isReady && !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    ) { Text(if (state.isSaving) "Enregistrement…" else "Fin de l’entraînement") }
                } else if (workout.notes.isNotBlank()) {
                    Text("Notes : ${workout.notes}", modifier = Modifier.padding(bottom = 20.dp))
                }
            }
        }
    }
    if (confirmFinish && state.isActive) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Terminer l’entraînement ?") },
            text = { Text("Le chronomètre s’arrêtera et le bilan conservera toutes vos séries validées. " +
                "Les exercices sans série sont autorisés. Validez ou videz les saisies en cours avant de terminer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    onAction(WorkoutAction.FinishWorkout)
                }) { Text("Terminer et enregistrer") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Continuer") } },
        )
    }
}

@Composable
private fun SummaryCard(summary: WorkoutSummary, active: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (active) "Chronomètre" else "Durée totale")
            Text(formatDuration(summary.durationMillis), style = MaterialTheme.typography.headlineLarge)
            Text("${summary.completedSets} séries · ${summary.repetitions} répétitions · ${summary.performedExercises} exercices")
            Text("Tonnage : ${formatKg(summary.tonnageGrams)} kg", style = MaterialTheme.typography.titleLarge)
            Text("Somme des charges saisies × répétitions des séries validées.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExerciseCard(
    name: String,
    exercise: WorkoutExercise,
    draft: SetDraft,
    active: Boolean,
    enabled: Boolean,
    onAction: (WorkoutAction) -> Unit,
    errorMessage: String?,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${exercise.orderIndex + 1}. $name", style = MaterialTheme.typography.titleLarge)
            if (exercise.sets.isEmpty()) Text("Aucune série validée")
            exercise.sets.forEachIndexed { index, set ->
                Row {
                    Text(
                        "${index + 1}. ${formatKg(set.weightGrams)} kg × ${set.repetitions}" +
                            (set.rpe?.let { " · RPE $it" } ?: ""),
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        TextButton(
                            onClick = { onAction(WorkoutAction.RemoveSet(exercise.id, set.id)) },
                            enabled = enabled,
                        ) { Text("Retirer") }
                    }
                }
            }
            if (active) {
                Text("Nouvelle série", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.weight,
                        onValueChange = { onAction(WorkoutAction.ChangeWeight(exercise.id, it)) },
                        label = { Text("Poids (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, enabled = enabled, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.repetitions,
                        onValueChange = { onAction(WorkoutAction.ChangeRepetitions(exercise.id, it)) },
                        label = { Text("Répétitions") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, enabled = enabled, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = draft.rpe,
                    onValueChange = { onAction(WorkoutAction.ChangeRpe(exercise.id, it)) },
                    label = { Text("RPE (facultatif, de 1 à 10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null && (draft.weight.isNotBlank() || draft.repetitions.isNotBlank() || draft.rpe.isNotBlank())) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { onAction(WorkoutAction.LogSet(exercise.id)) },
                    enabled = enabled, modifier = Modifier.fillMaxWidth(),
                ) { Text("Valider la série") }
            }
        }
    }
}

private fun formatKg(grams: Long): String = BigDecimal.valueOf(grams, 3)
    .stripTrailingZeros().toPlainString().replace('.', ',')

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1_000
    return String.format(Locale.FRANCE, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
}

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRANCE))
