package app.workouttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dates are stored as epoch days (LocalDate.toEpochDay) and instants as epoch millis,
 * so the schema needs no type converters.
 */

@Entity(indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val notes: String = "",
)

/** A reusable workout, e.g. "Push day". */
@Entity
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
)

/** One exercise slot in a template, with the targets to aim for. */
@Entity(
    foreignKeys = [
        ForeignKey(WorkoutTemplate::class, ["id"], ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double? = null,
)

enum class ScheduleStatus { PLANNED, COMPLETED, SKIPPED }

/** A template placed on a calendar day. */
@Entity(
    foreignKeys = [
        ForeignKey(WorkoutTemplate::class, ["id"], ["templateId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index("epochDay")],
)
data class ScheduledWorkout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val epochDay: Long,
    val status: String = ScheduleStatus.PLANNED.name,
)

/** A workout actually performed; may or may not come from the schedule. */
@Entity(
    foreignKeys = [
        ForeignKey(ScheduledWorkout::class, ["id"], ["scheduledWorkoutId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("scheduledWorkoutId"), Index("startedAt")],
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledWorkoutId: Long? = null,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

/** One logged set. */
@Entity(
    foreignKeys = [
        ForeignKey(WorkoutSession::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val loggedAt: Long,
)
