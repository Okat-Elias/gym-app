package com.gymapp.view.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.workout.WorkoutAction
import com.gymapp.controller.workout.WorkoutController
import com.gymapp.controller.workout.WorkoutUiState
import com.gymapp.view.component.ValueStepper
import java.util.Locale

@Composable
fun WorkoutRoute(controller: WorkoutController) {
    val state by controller.state.collectAsStateWithLifecycle()
    WorkoutScreen(state = state, onAction = controller::onAction)
}

@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Séance rapide", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Chaque série est écrite immédiatement dans Room.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isLoadingExercises) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        if (state.exercises.isEmpty()) {
            Text("Le catalogue local est vide.")
            return@Column
        }

        Text(text = "Exercice", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.exercises.forEach { exercise ->
                FilterChip(
                    selected = state.selectedExerciseId == exercise.id,
                    onClick = { onAction(WorkoutAction.SelectExercise(exercise.id)) },
                    enabled = state.loggedSets.isEmpty() && !state.isSaving,
                    label = { Text(exercise.name) },
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ValueStepper(
                    label = "Charge",
                    value = "${formatKilograms(state.weightGrams)} kg",
                    onDecrease = { onAction(WorkoutAction.ChangeWeight(-2_500)) },
                    onIncrease = { onAction(WorkoutAction.ChangeWeight(2_500)) },
                    decreaseEnabled =
                        !state.isSaving && !state.isFinished && state.weightGrams >= 2_500,
                    increaseEnabled = !state.isSaving && !state.isFinished,
                )
                HorizontalDivider()
                ValueStepper(
                    label = "Répétitions",
                    value = state.repetitions.toString(),
                    onDecrease = { onAction(WorkoutAction.ChangeRepetitions(-1)) },
                    onIncrease = { onAction(WorkoutAction.ChangeRepetitions(1)) },
                    decreaseEnabled =
                        !state.isSaving && !state.isFinished && state.repetitions > 0,
                    increaseEnabled = !state.isSaving && !state.isFinished,
                )
                HorizontalDivider()
                ValueStepper(
                    label = "RPE",
                    value = String.format(Locale.FRANCE, "%.1f", state.rpe),
                    onDecrease = { onAction(WorkoutAction.ChangeRpe(-0.5)) },
                    onIncrease = { onAction(WorkoutAction.ChangeRpe(0.5)) },
                    decreaseEnabled = !state.isSaving && !state.isFinished && state.rpe > 1.0,
                    increaseEnabled = !state.isSaving && !state.isFinished && state.rpe < 10.0,
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        if (state.isFinished) {
            Text(
                text = "Séance terminée et disponible dans l'historique local.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = { onAction(WorkoutAction.StartNewWorkout) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Nouvelle séance")
            }
        } else {
            Button(
                onClick = { onAction(WorkoutAction.LogSet) },
                enabled = !state.isSaving && state.repetitions > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Enregistrement…" else "Valider la série")
            }
        }

        if (state.loggedSets.isNotEmpty()) {
            Text(text = "Séries validées", style = MaterialTheme.typography.titleMedium)
            state.loggedSets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Série ${index + 1}")
                    Text(
                        "${formatKilograms(set.weightGrams)} kg × ${set.repetitions} · RPE ${set.rpe}",
                    )
                }
            }
            if (!state.isFinished) {
                OutlinedButton(
                    onClick = { onAction(WorkoutAction.FinishWorkout) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Terminer la séance")
                }
            }
        }
    }
}

private fun formatKilograms(weightGrams: Long): String {
    val kilograms = weightGrams / 1_000.0
    return if (weightGrams % 1_000L == 0L) {
        kilograms.toInt().toString()
    } else {
        String.format(Locale.FRANCE, "%.2f", kilograms).trimEnd('0').trimEnd(',')
    }
}
