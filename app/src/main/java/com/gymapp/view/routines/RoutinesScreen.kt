package com.gymapp.view.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.routines.RoutinesAction
import com.gymapp.controller.routines.RoutinesController
import com.gymapp.controller.routines.RoutinesUiState
import kotlinx.coroutines.launch

@Composable
fun RoutinesRoute(controller: RoutinesController, onOpenWorkout: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    RoutinesScreen(state, controller::onAction, onOpenWorkout)
}

@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    onAction: (RoutinesAction) -> Unit,
    onOpenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var routineToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var replacingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseSearch by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun openExercisePicker(replaceId: String? = null) {
        replacingExerciseId = replaceId
        exerciseSearch = ""
        showExercisePicker = true
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(if (state.editingRoutineId == null) "Créer une routine" else "Modifier la routine",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 20.dp))
            Text("Choisissez les exercices de votre programme réutilisable.")
        }
        if (state.isLoading) {
            item { CircularProgressIndicator() }
        } else {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onAction(RoutinesAction.ChangeName(it)) },
                    label = { Text("Nom de la routine") },
                    placeholder = { Text("Ex. : Haut du corps") },
                    enabled = !state.isSaving, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onAction(RoutinesAction.ChangeNotes(it)) },
                    label = { Text("Consignes (facultatif)") },
                    enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Exercices de la routine", style = MaterialTheme.typography.titleMedium)
                if (state.selectedExerciseIds.isEmpty()) {
                    Text("Aucun exercice sélectionné.")
                }
                OutlinedButton(
                    onClick = { openExercisePicker() },
                    enabled = !state.isSaving,
                ) { Text("Ajouter un exercice") }
            }
            if (state.selectedExerciseIds.isNotEmpty()) {
                items(state.selectedExerciseIds, key = { "selected-$it" }) { id ->
                    val index = state.selectedExerciseIds.indexOf(id)
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${index + 1}. ${state.exercises.find { it.id == id }?.name ?: "Exercice indisponible"}",
                                style = MaterialTheme.typography.titleSmall)
                            Row {
                                TextButton(
                                    onClick = { openExercisePicker(id) },
                                    enabled = !state.isSaving,
                                ) { Text("Remplacer") }
                                TextButton(
                                    onClick = { onAction(RoutinesAction.RemoveExercise(id)) },
                                    enabled = !state.isSaving,
                                ) { Text("Retirer") }
                            }
                            Row {
                                TextButton(
                                    onClick = { onAction(RoutinesAction.MoveExercise(id, -1)) },
                                    enabled = !state.isSaving && index > 0,
                                ) { Text("Monter") }
                                TextButton(
                                    onClick = { onAction(RoutinesAction.MoveExercise(id, 1)) },
                                    enabled = !state.isSaving && index < state.selectedExerciseIds.lastIndex,
                                ) { Text("Descendre") }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onAction(RoutinesAction.Save) },
                    enabled = !state.isSaving && state.name.isNotBlank() && state.selectedExerciseIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isSaving) "Enregistrement…" else if (state.editingRoutineId == null)
                    "Créer la routine" else "Enregistrer les modifications") }
                if (state.editingRoutineId != null) {
                    TextButton(onClick = { onAction(RoutinesAction.CancelEdit) }, enabled = !state.isSaving) {
                        Text("Annuler la modification")
                    }
                }
            }
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.savedMessage?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onOpenWorkout) { Text("Aller aux séances") }
            }
        }
        if (state.routines.isNotEmpty()) {
            item { Text("Mes routines", style = MaterialTheme.typography.titleLarge) }
            items(state.routines, key = { "routine-${it.id}" }) { routine ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        routine.exercises.sortedBy { it.orderIndex }.forEachIndexed { index, entry ->
                            Text("${index + 1}. ${state.exercises.find { it.id == entry.exerciseId }?.name ?: "Exercice"}")
                        }
                        if (routine.notes.isNotBlank()) Text(routine.notes)
                        Row {
                            TextButton(
                                onClick = {
                                    onAction(RoutinesAction.Edit(routine.id))
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                                enabled = !state.isSaving,
                            ) { Text("Modifier") }
                            TextButton(
                                onClick = { routineToDelete = routine.id },
                                enabled = !state.isSaving,
                            ) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
    if (showExercisePicker) {
        val availableExercises = state.exercises.filter { exercise ->
            exercise.id !in state.selectedExerciseIds &&
                exercise.name.contains(exerciseSearch.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showExercisePicker = false },
            title = { Text(if (replacingExerciseId == null) "Ajouter un exercice" else "Remplacer l’exercice") },
            text = {
                Column {
                    OutlinedTextField(
                        value = exerciseSearch,
                        onValueChange = { exerciseSearch = it },
                        label = { Text("Rechercher un exercice") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (availableExercises.isEmpty()) {
                        Text(
                            if (exerciseSearch.isBlank()) "Aucun autre exercice disponible."
                            else "Aucun exercice trouvé.",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(availableExercises, key = { it.id }) { exercise ->
                                TextButton(
                                    onClick = {
                                        val replaced = replacingExerciseId
                                        onAction(if (replaced == null) RoutinesAction.AddExercise(exercise.id)
                                            else RoutinesAction.ReplaceExercise(replaced, exercise.id))
                                        showExercisePicker = false
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(exercise.name, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExercisePicker = false }) { Text("Annuler") }
            },
        )
    }
    routineToDelete?.let { id ->
        val name = state.routines.find { it.id == id }?.name ?: "cette routine"
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Supprimer « $name » ?") },
            text = { Text("La routine sera supprimée. Ses séances passées resteront dans l’historique.") },
            confirmButton = {
                TextButton(onClick = {
                    routineToDelete = null
                    onAction(RoutinesAction.Delete(id))
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { routineToDelete = null }) { Text("Annuler") } },
        )
    }
}
