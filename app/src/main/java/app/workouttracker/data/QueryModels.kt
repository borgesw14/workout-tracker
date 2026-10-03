package app.workouttracker.data

/** A template with how many exercises it holds, for the templates list. */
data class TemplateSummary(
    val id: Long,
    val name: String,
    val notes: String,
    val exerciseCount: Int,
)

/** A template's exercise slot joined with the exercise name. */
data class TemplateExerciseDetail(
    val exerciseId: Long,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double?,
)

/** A scheduled workout joined with its template's name. */
data class ScheduledItem(
    val id: Long,
    val templateId: Long,
    val epochDay: Long,
    val status: String,
    val templateName: String,
)

/** A finished session with its totals, for the history list. */
data class SessionSummary(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val setCount: Int,
    val volume: Double,
)

/** An exercise in a session joined with its name. */
data class SessionExerciseDetail(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: Double?,
)
