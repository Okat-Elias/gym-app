package com.gymapp.view.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.workout.SetDraft
import com.gymapp.controller.workout.WorkoutAction
import com.gymapp.controller.workout.WorkoutController
import com.gymapp.controller.workout.WorkoutUiState
import com.gymapp.model.domain.WorkoutExercise
import com.gymapp.model.domain.WorkoutSet
import com.gymapp.model.domain.WorkoutSetType
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
    var historyToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val workout = state.workout
    var exercisePickerOpen by rememberSaveable(workout?.id) { mutableStateOf(false) }
    var exerciseToReplace by rememberSaveable(workout?.id) { mutableStateOf<String?>(null) }
    var exerciseSearch by rememberSaveable(workout?.id) { mutableStateOf("") }
    var pendingAddCount by rememberSaveable(workout?.id) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(workout?.id, workout?.exercises?.size, state.isSaving, state.errorMessage, pendingAddCount) {
        val countBeforeAdd = pendingAddCount ?: return@LaunchedEffect
        if (workout != null && !state.isSaving && workout.exercises.size > countBeforeAdd) {
            // The active layout starts with its title and session summary before the exercise cards.
            val cardIndex = 2 + (if (state.errorMessage != null) 1 else 0) + workout.exercises.lastIndex
            listState.animateScrollToItem(cardIndex)
            pendingAddCount = null
        } else if (!state.isSaving && state.errorMessage != null) {
            pendingAddCount = null
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        state = listState,
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
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        TextButton(
                            onClick = { onAction(WorkoutAction.ViewHistory(session.id)) },
                            enabled = !state.isSaving, modifier = Modifier.weight(1f),
                        ) {
                            Text("${state.routines.find { it.id == session.routineId }?.name ?: "Routine supprimée"} · ${formatDate(session.startedAtEpochMillis)}")
                        }
                        TextButton(onClick = { historyToDelete = session.id }, enabled = !state.isSaving) {
                            Text("Supprimer")
                        }
                    }
                }
            }
        } else {
            item {
                Text(state.routines.find { it.id == workout.routineId }?.name ?: "Routine supprimée",
                    style = MaterialTheme.typography.titleLarge)
                Text(formatDate(workout.startedAtEpochMillis))
                state.summary?.let { SummaryCard(it, state.isActive) }
            }
            if (!state.isActive) {
                item {
                    Text("Séance enregistrée", color = MaterialTheme.colorScheme.primary)
                    if (state.routineUpdatePendingRetry) {
                        Text("La séance est enregistrée, mais la routine n’a pas pu être mise à jour. " +
                            "Les séances précédentes sont conservées.")
                        Button(
                            onClick = { onAction(WorkoutAction.RetryRoutineUpdate) },
                            enabled = state.isReady && !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.isSaving) "Mise à jour…" else "Réessayer la mise à jour") }
                    }
                    Button(
                        onClick = { onAction(WorkoutAction.StartNewWorkout) },
                        enabled = !state.isSaving,
                    ) {
                        Text("Retour aux séances")
                    }
                    TextButton(onClick = { historyToDelete = workout.id }, enabled = !state.isSaving) {
                        Text("Supprimer cette séance")
                    }
                }
            }
            items(workout.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    name = state.exercises.find { it.id == exercise.exerciseId }?.name ?: "Exercice",
                    exercise = exercise, draft = state.drafts[exercise.id] ?: SetDraft(),
                    previousSets = state.previousSetsFor(exercise.id),
                    active = state.isActive, enabled = state.isReady && !state.isSaving,
                    onAction = onAction, errorMessage = state.errorMessage,
                    onReplace = {
                        exerciseToReplace = it
                        exerciseSearch = ""
                        exercisePickerOpen = true
                    },
                )
            }
            if (state.isActive) {
                item {
                    Button(
                        onClick = {
                            exerciseToReplace = null
                            exerciseSearch = ""
                            exercisePickerOpen = true
                        },
                        enabled = state.isReady && !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ajouter un exercice") }
                }
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
        val routineName = state.routines.find { it.id == workout?.routineId }?.name
        val planChanged = state.hasRoutinePlanChanges && routineName != null
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(if (planChanged) "Mettre à jour la routine ?" else "Terminer l’entraînement ?") },
            text = {
                Text(if (planChanged)
                    "Les exercices ont changé pendant cette séance. Vous pouvez utiliser cette liste pour les prochaines séances de « $routineName ». " +
                        "Cette séance et les séances précédentes resteront dans l’historique. Validez ou videz les saisies en cours avant de terminer."
                else
                    "Le chronomètre s’arrêtera et le bilan conservera toutes vos séries validées. " +
                        "Les exercices sans série sont autorisés. Validez ou videz les saisies en cours avant de terminer.")
            },
            confirmButton = {
                if (planChanged) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = {
                                confirmFinish = false
                                onAction(WorkoutAction.FinishAndUpdateRoutine)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Terminer et mettre à jour la routine") }
                        OutlinedButton(
                            onClick = {
                                confirmFinish = false
                                onAction(WorkoutAction.FinishWorkout)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Terminer sans modifier la routine") }
                        TextButton(
                            onClick = { confirmFinish = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Continuer la séance") }
                    }
                } else {
                    TextButton(onClick = {
                        confirmFinish = false
                        onAction(WorkoutAction.FinishWorkout)
                    }) { Text("Terminer et enregistrer") }
                }
            },
            dismissButton = {
                if (!planChanged) TextButton(onClick = { confirmFinish = false }) { Text("Continuer") }
            },
        )
    }
    historyToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { historyToDelete = null },
            title = { Text("Supprimer cette séance ?") },
            text = { Text("Ses séries, son tonnage et ses notes seront supprimés de l’historique.") },
            confirmButton = {
                TextButton(onClick = {
                    historyToDelete = null
                    onAction(WorkoutAction.DeleteHistory(id))
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { historyToDelete = null }) { Text("Annuler") } },
        )
    }
    if (exercisePickerOpen && state.isActive && workout != null) {
        val includedIds = workout.exercises.map { it.exerciseId }.toSet()
        val availableExercises = state.exercises.filterNot { it.id in includedIds }
        val filteredExercises = availableExercises.filter {
            it.name.contains(exerciseSearch.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { exercisePickerOpen = false },
            title = { Text(if (exerciseToReplace == null) "Ajouter un exercice" else "Remplacer l’exercice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = exerciseSearch,
                        onValueChange = { exerciseSearch = it },
                        label = { Text("Rechercher un exercice") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (filteredExercises.isEmpty()) {
                        Text(if (availableExercises.isEmpty())
                            "Tous les exercices du catalogue sont déjà dans cette séance."
                        else "Aucun exercice ne correspond à cette recherche.")
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(filteredExercises, key = { it.id }) { option ->
                                TextButton(
                                    onClick = {
                                        val replacing = exerciseToReplace
                                        if (replacing == null) pendingAddCount = workout.exercises.size
                                        onAction(if (replacing == null) WorkoutAction.AddExercise(option.id)
                                            else WorkoutAction.ReplaceExercise(replacing, option.id))
                                        exercisePickerOpen = false
                                    },
                                    enabled = state.isReady && !state.isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(option.name, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exercisePickerOpen = false }) { Text("Fermer") } },
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
    previousSets: List<WorkoutSet>,
    active: Boolean,
    enabled: Boolean,
    onAction: (WorkoutAction) -> Unit,
    errorMessage: String?,
    onReplace: (String) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${exercise.orderIndex + 1}. $name", style = MaterialTheme.typography.titleLarge)
            if (active) {
                val hasUnsavedInput = !draft.isSuggested &&
                    (draft.weight.isNotBlank() || draft.repetitions.isNotBlank() || draft.rpe.isNotBlank())
                TextButton(
                    onClick = { onReplace(exercise.id) },
                    enabled = enabled && exercise.sets.isEmpty() && !hasUnsavedInput,
                ) { Text("Remplacer cet exercice") }
                if (exercise.sets.isNotEmpty()) {
                    Text("Des séries sont déjà validées. Ajoutez un nouvel exercice pour les conserver.",
                        style = MaterialTheme.typography.bodySmall)
                } else if (hasUnsavedInput) {
                    Text("Validez ou effacez la saisie en cours pour remplacer cet exercice.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (active && previousSets.isNotEmpty()) {
                Text("Dernière séance · séries proposées", style = MaterialTheme.typography.titleMedium)
                Text("Ces valeurs sont préremplies pour vous guider. Elles ne sont comptées qu’après validation.",
                    style = MaterialTheme.typography.bodySmall)
                previousSets.forEachIndexed { index, set ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${index + 1}. ${set.type.label()} · ${formatKg(set.weightGrams)} kg × ${set.repetitions}",
                            modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { onAction(WorkoutAction.LoadPreviousSet(exercise.id, set.id)) },
                            enabled = enabled,
                        ) { Text("Reprendre") }
                    }
                }
            }
            if (exercise.sets.isEmpty()) Text("Aucune série validée")
            exercise.sets.forEachIndexed { index, set ->
                Row {
                    Text(
                        "${index + 1}. ${set.type.label()} · ${formatKg(set.weightGrams)} kg × ${set.repetitions}" +
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
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutSetType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { onAction(WorkoutAction.ChangeSetType(exercise.id, type)) },
                            label = { Text(type.label()) }, enabled = enabled,
                        )
                    }
                }
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
                if (draft.weight.isNotBlank() || draft.repetitions.isNotBlank() || draft.rpe.isNotBlank()) {
                    TextButton(
                        onClick = { onAction(WorkoutAction.ClearDraft(exercise.id)) }, enabled = enabled,
                    ) { Text("Effacer la saisie") }
                }
            }
        }
    }
}

private fun WorkoutSetType.label(): String = when (this) {
    WorkoutSetType.WARM_UP -> "Échauffement"
    WorkoutSetType.WORKING -> "Effective"
    WorkoutSetType.DROP_SET -> "Dropset"
    WorkoutSetType.BACK_OFF -> "Allégée"
}

private fun formatKg(grams: Long): String = BigDecimal.valueOf(grams, 3)
    .stripTrailingZeros().toPlainString().replace('.', ',')

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1_000
    return String.format(Locale.FRANCE, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
}

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRANCE))
