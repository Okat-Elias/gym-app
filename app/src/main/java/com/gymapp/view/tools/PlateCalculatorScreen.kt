package com.gymapp.view.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gymapp.controller.tools.PlateCalculationStatus
import com.gymapp.controller.tools.PlateCalculatorAction
import com.gymapp.controller.tools.PlateCalculatorUiState
import java.math.BigDecimal

@Composable
fun PlateCalculatorScreen(
    state: PlateCalculatorUiState,
    onAction: (PlateCalculatorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Calculateur de disques",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Choisissez le poids total, barre comprise. Le chargement est indiqué pour chaque côté.",
            style = MaterialTheme.typography.bodyLarge,
        )

        TargetWeightCard(
            targetWeightGrams = state.targetWeightGrams,
            onDecrease = { onAction(PlateCalculatorAction.DecreaseTarget) },
            onIncrease = { onAction(PlateCalculatorAction.IncreaseTarget) },
        )

        BarWeightSelector(
            selectedBarWeightGrams = state.barWeightGrams,
            onSelectTwentyKilograms = {
                onAction(PlateCalculatorAction.SelectTwentyKilogramBar)
            },
            onSelectFifteenKilograms = {
                onAction(PlateCalculatorAction.SelectFifteenKilogramBar)
            },
        )

        LoadingResultCard(state = state)

        OutlinedButton(
            onClick = { onAction(PlateCalculatorAction.Reset) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Réinitialiser à 60 kilogrammes avec une barre de 20 kilogrammes"
                },
        ) {
            Text("Réinitialiser")
        }
    }
}

@Composable
private fun TargetWeightCard(
    targetWeightGrams: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Poids cible",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatKilograms(targetWeightGrams),
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription =
                        "Poids cible ${spokenKilograms(targetWeightGrams)}"
                },
                style = MaterialTheme.typography.displaySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription =
                                "Diminuer le poids cible de 2,5 kilogrammes"
                        },
                ) {
                    Text("− 2,5 kg")
                }
                Button(
                    onClick = onIncrease,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription =
                                "Augmenter le poids cible de 2,5 kilogrammes"
                        },
                ) {
                    Text("+ 2,5 kg")
                }
            }
        }
    }
}

@Composable
private fun BarWeightSelector(
    selectedBarWeightGrams: Long,
    onSelectTwentyKilograms: () -> Unit,
    onSelectFifteenKilograms: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Poids de la barre",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BarWeightButton(
                label = "20 kg",
                selected = selectedBarWeightGrams == 20_000L,
                onClick = onSelectTwentyKilograms,
                modifier = Modifier.weight(1f),
            )
            BarWeightButton(
                label = "15 kg",
                selected = selectedBarWeightGrams == 15_000L,
                onClick = onSelectFifteenKilograms,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BarWeightButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticsModifier = modifier.semantics {
        role = Role.RadioButton
        this.selected = selected
        contentDescription = "Barre de $label${if (selected) ", sélectionnée" else ""}"
    }

    if (selected) {
        Button(
            onClick = onClick,
            modifier = semanticsModifier,
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = semanticsModifier,
        ) {
            Text(label)
        }
    }
}

@Composable
private fun LoadingResultCard(state: PlateCalculatorUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Chargement proposé",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )

            when (val status = state.status) {
                PlateCalculationStatus.Exact -> {
                    Text(
                        text = "Chargement exact : ${formatKilograms(state.loadedWeightGrams.orZero())}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                is PlateCalculationStatus.Closest -> {
                    Text(
                        text = "Poids réalisable : ${formatKilograms(state.loadedWeightGrams.orZero())}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = closestDifferenceLabel(status.differenceFromTargetGrams),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                PlateCalculationStatus.TargetBelowBar -> {
                    Text(
                        text = "Le poids cible doit être supérieur ou égal au poids de la barre.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (state.status !is PlateCalculationStatus.TargetBelowBar) {
                Spacer(modifier = Modifier.height(2.dp))
                if (state.allocations.isEmpty()) {
                    Text("Aucun disque : utilisez la barre seule.")
                } else {
                    state.allocations.forEach { allocation ->
                        Text(
                            text = buildString {
                                append(allocation.countPerSide)
                                append(" × ")
                                append(formatKilograms(allocation.weightGrams))
                                append(" de chaque côté")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun closestDifferenceLabel(differenceGrams: Long): String = when {
    differenceGrams > 0L ->
        "${formatKilograms(differenceGrams)} au-dessus du poids cible."

    differenceGrams < 0L ->
        "${formatKilograms(-differenceGrams)} en dessous du poids cible."

    else -> "Le poids cible est réalisable."
}

private fun formatKilograms(weightGrams: Long): String =
    "${kilogramsValue(weightGrams)} kg"

private fun spokenKilograms(weightGrams: Long): String =
    "${kilogramsValue(weightGrams)} kilogrammes"

private fun kilogramsValue(weightGrams: Long): String =
    BigDecimal.valueOf(weightGrams, GRAMS_DECIMAL_SCALE)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

private fun Long?.orZero(): Long = this ?: 0L

private const val GRAMS_DECIMAL_SCALE = 3
