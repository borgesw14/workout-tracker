package app.workouttracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.workouttracker.ui.exercises.ExercisesScreen
import app.workouttracker.ui.log.LogHomeScreen
import app.workouttracker.ui.log.SessionScreen
import app.workouttracker.ui.plan.PlanScreen
import app.workouttracker.ui.plan.TemplateEditorScreen
import app.workouttracker.ui.stats.StatsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Plan("plan", "Plan", Icons.Filled.CalendarMonth),
    Log("log", "Log", Icons.Filled.FitnessCenter),
    Exercises("exercises", "Exercises", Icons.AutoMirrored.Filled.List),
    Stats("stats", "Stats", Icons.Filled.BarChart),
}

@Composable
fun WorkoutTrackerApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        // Each screen's own top bar handles the status bar; this only pads for the bottom navigation.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Plan.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            // Nested so the Plan tab stays highlighted while editing a template.
            navigation(startDestination = "plan/home", route = Tab.Plan.route) {
                composable("plan/home") {
                    PlanScreen(onEditTemplate = { id -> navController.navigate("plan/template/$id") })
                }
                composable(
                    "plan/template/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    TemplateEditorScreen(
                        templateId = entry.arguments?.getLong("id") ?: 0L,
                        onClose = { navController.popBackStack() },
                    )
                }
            }
            navigation(startDestination = "log/home", route = Tab.Log.route) {
                composable("log/home") {
                    LogHomeScreen(onOpenSession = { id -> navController.navigate("log/session/$id") })
                }
                composable(
                    "log/session/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    SessionScreen(
                        sessionId = entry.arguments?.getLong("id") ?: 0L,
                        onClose = { navController.popBackStack() },
                    )
                }
            }
            composable(Tab.Exercises.route) { ExercisesScreen() }
            composable(Tab.Stats.route) { StatsScreen() }
        }
    }
}
