package app.workouttracker

import android.app.Application
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.reminders.Reminders
import app.workouttracker.settings.WeightUnitSetting

class WorkoutApp : Application() {
    val database: WorkoutDatabase by lazy { WorkoutDatabase.build(this) }
    val weightUnit: WeightUnitSetting by lazy { WeightUnitSetting(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
