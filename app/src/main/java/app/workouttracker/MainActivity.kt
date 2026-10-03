package app.workouttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.workouttracker.settings.LocalWeightUnit
import app.workouttracker.ui.WorkoutTrackerApp
import app.workouttracker.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val unit by (application as WorkoutApp).weightUnit.unit.collectAsState()
            WorkoutTheme {
                CompositionLocalProvider(LocalWeightUnit provides unit) {
                    WorkoutTrackerApp()
                }
            }
        }
    }
}
