package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymapp.controller.MainAction
import com.gymapp.controller.MainController
import com.gymapp.controller.MainDestination
import com.gymapp.controller.dashboard.DashboardController
import com.gymapp.controller.statistics.StatisticsController
import com.gymapp.controller.tools.PlateCalculatorController
import com.gymapp.controller.workout.WorkoutController
import com.gymapp.di.controllerFactory
import com.gymapp.model.usecase.CalculateWeeklyMuscleVolume
import com.gymapp.view.dashboard.DashboardRoute
import com.gymapp.view.navigation.GymApp
import com.gymapp.view.statistics.StatisticsRoute
import com.gymapp.view.theme.GymTheme
import com.gymapp.view.tools.PlateCalculatorScreen
import com.gymapp.view.workout.WorkoutRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as GymApplication).container

        setContent {
            GymTheme {
                val mainController: MainController = viewModel()
                val dashboardController: DashboardController = viewModel(
                    factory = remember(container) {
                        controllerFactory {
                            DashboardController(
                                exerciseRepository = container.exerciseRepository,
                                workoutRepository = container.workoutRepository,
                            )
                        }
                    },
                )
                val workoutController: WorkoutController = viewModel(
                    factory = remember(container) {
                        controllerFactory {
                            WorkoutController(
                                exerciseRepository = container.exerciseRepository,
                                workoutRepository = container.workoutRepository,
                            )
                        }
                    },
                )
                val statisticsController: StatisticsController = viewModel(
                    factory = remember(container) {
                        controllerFactory {
                            StatisticsController(
                                exerciseRepository = container.exerciseRepository,
                                workoutRepository = container.workoutRepository,
                                calculateWeeklyMuscleVolume = CalculateWeeklyMuscleVolume(),
                            )
                        }
                    },
                )
                val plateCalculatorController: PlateCalculatorController = viewModel()
                val plateCalculatorState by
                    plateCalculatorController.state.collectAsStateWithLifecycle()

                GymApp(
                    mainController = mainController,
                    dashboard = {
                        DashboardRoute(
                            controller = dashboardController,
                            onStartWorkout = {
                                mainController.onAction(
                                    MainAction.SelectDestination(MainDestination.WORKOUT),
                                )
                            },
                            onOpenStatistics = {
                                mainController.onAction(
                                    MainAction.SelectDestination(MainDestination.STATISTICS),
                                )
                            },
                            onOpenTools = {
                                mainController.onAction(
                                    MainAction.SelectDestination(MainDestination.TOOLS),
                                )
                            },
                        )
                    },
                    workout = { WorkoutRoute(workoutController) },
                    statistics = { StatisticsRoute(statisticsController) },
                    tools = {
                        PlateCalculatorScreen(
                            state = plateCalculatorState,
                            onAction = plateCalculatorController::onAction,
                        )
                    },
                )
            }
        }
    }
}

