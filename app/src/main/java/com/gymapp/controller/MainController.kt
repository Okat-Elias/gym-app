package com.gymapp.controller

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainDestination(val label: String, val shortLabel: String) {
    DASHBOARD(label = "Accueil", shortLabel = "A"),
    ROUTINES(label = "Routines", shortLabel = "R"),
    WORKOUT(label = "Séance", shortLabel = "S"),
    STATISTICS(label = "Statistiques", shortLabel = "Stats"),
    TOOLS(label = "Outils", shortLabel = "O"),
}

data class MainUiState(
    val destination: MainDestination = MainDestination.DASHBOARD,
)

sealed interface MainAction {
    data class SelectDestination(val destination: MainDestination) : MainAction
}

class MainController : ViewModel(), ScreenController<MainUiState, MainAction> {
    private val mutableState = MutableStateFlow(MainUiState())
    override val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    override fun onAction(action: MainAction) {
        when (action) {
            is MainAction.SelectDestination -> {
                mutableState.value = mutableState.value.copy(destination = action.destination)
            }
        }
    }
}
