package app.workouttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutTemplate::class,
        TemplateExercise::class,
        ScheduledWorkout::class,
        WorkoutSession::class,
        SetEntry::class,
        SessionExercise::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "workouts.db"

        fun build(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Fills the exercise library with common lifts the first time the database is created. */
private object SeedCallback : RoomDatabase.Callback() {
    private val defaults = listOf(
        "Chest" to listOf("Bench Press", "Incline Dumbbell Press", "Push-Up"),
        "Back" to listOf("Deadlift", "Barbell Row", "Pull-Up", "Lat Pulldown"),
        "Legs" to listOf("Back Squat", "Romanian Deadlift", "Leg Press", "Walking Lunge"),
        "Shoulders" to listOf("Overhead Press", "Lateral Raise"),
        "Arms" to listOf("Barbell Curl", "Tricep Pushdown"),
        "Core" to listOf("Plank", "Hanging Leg Raise"),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        defaults.forEach { (category, names) ->
            names.forEach { name ->
                db.execSQL(
                    "INSERT INTO Exercise (name, category, notes) VALUES (?, ?, '')",
                    arrayOf(name, category),
                )
            }
        }
    }
}
