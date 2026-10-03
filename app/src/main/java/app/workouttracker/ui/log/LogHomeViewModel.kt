package app.workouttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.workouttracker.data.ScheduleStatus
import app.workouttracker.data.ScheduledItem
import app.workouttracker.data.SessionExercise
import app.workouttracker.data.SessionSummary
import app.workouttracker.data.TemplateSummary
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.data.WorkoutSession
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogHomeViewModel(private val db: WorkoutDatabase) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()

    val active: StateFlow<WorkoutSession?> = db.sessionDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val plannedToday: StateFlow<List<ScheduledItem>> = db.scheduleDao().observeRange(today, today)
        .map { items -> items.filter { it.status == ScheduleStatus.PLANNED.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateSummary>> = db.templateDao().observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<SessionSummary>> = db.sessionDao().observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startScheduled(item: ScheduledItem, onStarted: (Long) -> Unit) =
        start(item.templateName, item.templateId, item.id, onStarted)

    /** Starting a template that's also planned for today counts toward that plan. */
    fun startTemplate(template: TemplateSummary, onStarted: (Long) -> Unit) {
        val planned = plannedToday.value.firstOrNull { it.templateId == template.id }
        start(template.name, template.id, planned?.id, onStarted)
    }

    fun startEmpty(onStarted: (Long) -> Unit) = start("Workout", null, null, onStarted)

    private fun start(name: String, templateId: Long?, scheduledId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.withTransaction {
                val sessionId = db.sessionDao().insert(
                    WorkoutSession(name = name, scheduledWorkoutId = scheduledId, startedAt = System.currentTimeMillis())
                )
                if (templateId != null) {
                    db.sessionDao().insertExercises(
                        db.templateDao().exercisesFor(templateId).mapIndexed { i, t ->
                            SessionExercise(
                                sessionId = sessionId,
                                exerciseId = t.exerciseId,
                                position = i,
                                targetSets = t.targetSets,
                                targetReps = t.targetReps,
                                targetWeight = t.targetWeight,
                            )
                        }
                    )
                }
                sessionId
            }
            onStarted(id)
        }
    }
}
