package app.workouttracker

import android.app.Application
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.reminders.Reminders

class WorkoutApp : Application() {
    val database: WorkoutDatabase by lazy { WorkoutDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
