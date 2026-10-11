package app.workouttracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workouttracker.data.Exercise
import app.workouttracker.data.ScheduleStatus
import app.workouttracker.data.WorkoutDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ProgressMetric(val label: String) { TopWeight("Top set"), OneRepMax("Est. 1RM") }

/** One point on the progress chart: the best value from one workout. */
data class ProgressPoint(val date: LocalDate, val value: Double, val detail: String)

/** Total volume for one week, starting [weekStart]. */
data class WeekVolume(val weekStart: LocalDate, val volume: Double, val workouts: Int)

data class Summary(
    val workoutsThisWeek: Int,
    val volumeThisWeek: Double,
    val completed: Int,
    val due: Int,
)

private const val WEEKS = 8

/** Epley formula. One rep is the weight itself. */
fun estimatedOneRepMax(weight: Double, reps: Int): Double =
    if (reps <= 1) weight else weight * (1 + reps / 30.0)

class StatsViewModel(private val db: WorkoutDatabase) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val today = LocalDate.now()
    private val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
    private val firstWeek = thisWeek.minusWeeks((WEEKS - 1).toLong())

    private fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()
    private fun Long.toDate() = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    val exercises: StateFlow<List<Exercise>> = db.statsDao().observeLoggedExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The chosen exercise id, or null to follow the first one with data. */
    private val chosen = MutableStateFlow<Long?>(null)
    val metric = MutableStateFlow(ProgressMetric.OneRepMax)

    val selectedExercise: StateFlow<Exercise?> = combine(exercises, chosen) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<List<ProgressPoint>> = selectedExercise
        .flatMapLatest { ex -> if (ex == null) flowOf(emptyList()) else db.statsDao().observeSetsForExercise(ex.id) }
        .combine(metric) { sets, m ->
            sets.groupBy { it.sessionId }.values.map { session ->
                val best = when (m) {
                    ProgressMetric.TopWeight -> session.maxWith(compareBy({ it.weight }, { it.reps }))
                    ProgressMetric.OneRepMax -> session.maxBy { estimatedOneRepMax(it.weight, it.reps) }
                }
                val value = if (m == ProgressMetric.TopWeight) best.weight else estimatedOneRepMax(best.weight, best.reps)
                ProgressPoint(best.startedAt.toDate(), value, "${formatNumber(best.weight)} × ${best.reps}")
            }.sortedBy { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val volumes: Flow<List<WeekVolume>> = db.statsDao().observeSessionVolumes(firstWeek.millis())
        .map { sessions ->
            val byWeek = sessions.groupBy { it.startedAt.toDate().with(TemporalAdjusters.previousOrSame(firstDay)) }
            (0 until WEEKS).map { i ->
                val week = firstWeek.plusWeeks(i.toLong())
                val list = byWeek[week].orEmpty()
                WeekVolume(week, list.sumOf { it.volume }, list.size)
            }
        }

    val weekly: StateFlow<List<WeekVolume>> = volumes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This week's totals, and plan adherence over the last four weeks up to today. */
    val summary: StateFlow<Summary?> = combine(
        volumes,
        db.scheduleDao().observeRange(thisWeek.minusWeeks(3).toEpochDay(), today.toEpochDay()),
    ) { weeks, scheduled ->
        val current = weeks.lastOrNull()
        Summary(
            workoutsThisWeek = current?.workouts ?: 0,
            volumeThisWeek = current?.volume ?: 0.0,
            completed = scheduled.count { it.status == ScheduleStatus.COMPLETED.name },
            // Today's still-planned workouts aren't missed yet.
            due = scheduled.count { it.status != ScheduleStatus.PLANNED.name || it.epochDay < today.toEpochDay() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(exercise: Exercise) {
        chosen.value = exercise.id
    }
}
