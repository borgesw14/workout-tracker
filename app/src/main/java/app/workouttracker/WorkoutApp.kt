package app.workouttracker

import android.app.Application
import app.workouttracker.data.WorkoutDatabase

class WorkoutApp : Application() {
    val database: WorkoutDatabase by lazy { WorkoutDatabase.build(this) }
}
