package com.gymapp.view.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.statistics.StatisticsController
import com.gymapp.controller.statistics.StatisticsUiState
import com.gymapp.model.domain.MuscleGroup
import java.util.Locale

@Composable
fun StatisticsRoute(controller: StatisticsController) {
    val state by controller.state.collectAsStateWithLifecycle()
    StatisticsScreen(state = state)
}

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "Volume musculaire", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Semaine en cours · séries de travail terminées",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.volumes.isEmpty() -> {
                Text(
                    "Validez puis terminez une séance pour afficher les contributions primaires et secondaires.",
                )
            }
            else -> state.volumes.forEach { volume ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = volume.muscleGroup.frenchLabel(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${formatEffectiveSets(volume.effectiveSetBasisPoints)} séries effectives",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatEffectiveSets(basisPoints: Int): String =
    String.format(Locale.FRANCE, "%.2f", basisPoints / 10_000.0)
        .trimEnd('0')
        .trimEnd(',')

private fun MuscleGroup.frenchLabel(): String = when (this) {
    MuscleGroup.CHEST -> "Pectoraux"
    MuscleGroup.BACK -> "Dos"
    MuscleGroup.SHOULDERS -> "Épaules"
    MuscleGroup.REAR_DELTOIDS -> "Deltoïdes postérieurs"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.FOREARMS -> "Avant-bras"
    MuscleGroup.CORE -> "Ceinture abdominale"
    MuscleGroup.QUADRICEPS -> "Quadriceps"
    MuscleGroup.HAMSTRINGS -> "Ischio-jambiers"
    MuscleGroup.GLUTES -> "Fessiers"
    MuscleGroup.CALVES -> "Mollets"
}

