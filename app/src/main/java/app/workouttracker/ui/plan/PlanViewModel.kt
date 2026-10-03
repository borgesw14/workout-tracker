package app.workouttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workouttracker.data.ScheduleStatus
import app.workouttracker.data.ScheduledItem
import app.workouttracker.data.ScheduledWorkout
import app.workouttracker.data.TemplateSummary
import app.workouttracker.data.WorkoutDatabase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlanViewModel(private val db: WorkoutDatabase) : ViewModel() {

    val month = MutableStateFlow(YearMonth.now())
    val selectedDay = MutableStateFlow(LocalDate.now())

    /** Scheduled workouts in the visible month, keyed by day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val scheduled: StateFlow<Map<LocalDate, List<ScheduledItem>>> = month
        .flatMapLatest { m ->
            db.scheduleDao().observeRange(m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay())
        }
        .map { items -> items.groupBy { LocalDate.ofEpochDay(it.epochDay) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val templates: StateFlow<List<TemplateSummary>> = db.templateDao().observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun select(day: LocalDate) {
        selectedDay.value = day
        month.value = YearMonth.from(day)
    }

    /** Schedules [templateId] on [day], and on the same weekday for the following [extraWeeks] weeks. */
    fun schedule(templateId: Long, day: LocalDate, extraWeeks: Int) {
        viewModelScope.launch {
            db.scheduleDao().insertAll(
                (0..extraWeeks).map { w ->
                    ScheduledWorkout(templateId = templateId, epochDay = day.plusWeeks(w.toLong()).toEpochDay())
                }
            )
        }
    }

    fun setStatus(item: ScheduledItem, status: ScheduleStatus) {
        viewModelScope.launch { db.scheduleDao().setStatus(item.id, status.name) }
    }

    fun remove(item: ScheduledItem) {
        viewModelScope.launch { db.scheduleDao().delete(item.id) }
    }
}
