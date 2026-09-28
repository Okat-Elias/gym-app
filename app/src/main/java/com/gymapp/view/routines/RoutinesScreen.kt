package com.gymapp.view.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.routines.RoutinesAction
import com.gymapp.controller.routines.RoutinesController
import com.gymapp.controller.routines.RoutinesUiState

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
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Créer une routine", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 20.dp))
            Text("Composez un programme réutilisable, puis lancez-le dans l’onglet Séance.")
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
            item { Text("Choisir les exercices", style = MaterialTheme.typography.titleMedium) }
            items(state.exercises, key = { "catalog-${it.id}" }) { exercise ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = exercise.id in state.selectedExerciseIds,
                        onCheckedChange = { onAction(RoutinesAction.ToggleExercise(exercise.id)) },
                        enabled = !state.isSaving,
                    )
                    Text(exercise.name, modifier = Modifier.weight(1f))
                }
            }
            if (state.selectedExerciseIds.isNotEmpty()) {
                item { Text("Ordre de passage", style = MaterialTheme.typography.titleMedium) }
                items(state.selectedExerciseIds, key = { "selected-$it" }) { id ->
                    val index = state.selectedExerciseIds.indexOf(id)
                    Column {
                        Text("${index + 1}. ${state.exercises.find { it.id == id }?.name.orEmpty()}")
                        Row {
                            TextButton(
                                onClick = { onAction(RoutinesAction.MoveExercise(id, -1)) },
                                enabled = !state.isSaving && index > 0,
                            ) { Text("Monter") }
                            TextButton(
                                onClick = { onAction(RoutinesAction.MoveExercise(id, 1)) },
                                enabled = !state.isSaving && index < state.selectedExerciseIds.lastIndex,
                            ) { Text("Descendre") }
                            TextButton(
                                onClick = { onAction(RoutinesAction.ToggleExercise(id)) },
                                enabled = !state.isSaving,
                            ) { Text("Retirer") }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onAction(RoutinesAction.Save) },
                    enabled = !state.isSaving && state.name.isNotBlank() && state.selectedExerciseIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isSaving) "Enregistrement…" else "Enregistrer la routine") }
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
                    }
                }
            }
        }
    }
}
