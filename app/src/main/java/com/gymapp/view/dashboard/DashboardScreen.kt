package com.gymapp.view.dashboard

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.dashboard.DashboardController
import com.gymapp.controller.dashboard.DashboardUiState
import com.gymapp.view.component.MetricCard

@Composable
fun DashboardRoute(
    controller: DashboardController,
    onStartWorkout: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onStartWorkout = onStartWorkout,
        onOpenStatistics = onOpenStatistics,
        onOpenTools = onOpenTools,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onStartWorkout: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Votre entraînement", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Rapide en salle, fiable hors connexion.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "séances terminées",
                    value = state.completedWorkoutCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "séries validées",
                    value = state.completedSetCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            MetricCard(
                label = "exercices disponibles localement",
                value = state.exerciseCount.toString(),
            )
        }

        state.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onStartWorkout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.activeWorkoutCount > 0) "Reprendre la séance" else "Démarrer une séance")
        }
        FilledTonalButton(
            onClick = onOpenStatistics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voir le volume musculaire")
        }
        FilledTonalButton(
            onClick = onOpenTools,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ouvrir le calculateur de disques")
        }
    }
}

