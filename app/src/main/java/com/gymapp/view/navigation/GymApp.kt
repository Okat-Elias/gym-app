package com.gymapp.view.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymapp.controller.MainAction
import com.gymapp.controller.MainController
import com.gymapp.controller.MainDestination

@Composable
fun GymApp(
    mainController: MainController,
    dashboard: @Composable () -> Unit,
    routines: @Composable () -> Unit,
    workout: @Composable () -> Unit,
    statistics: @Composable () -> Unit,
    tools: @Composable () -> Unit,
) {
    val state by mainController.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = state.destination == destination,
                        onClick = {
                            mainController.onAction(MainAction.SelectDestination(destination))
                        },
                        icon = {
                            Text(
                                text = destination.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.destination) {
                MainDestination.DASHBOARD -> dashboard()
                MainDestination.ROUTINES -> routines()
                MainDestination.WORKOUT -> workout()
                MainDestination.STATISTICS -> statistics()
                MainDestination.TOOLS -> tools()
            }
        }
    }
}
