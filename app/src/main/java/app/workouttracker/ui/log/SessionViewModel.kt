package app.workouttracker.ui.log

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.workouttracker.data.Exercise
import app.workouttracker.data.ScheduleStatus
import app.workouttracker.data.SessionExercise
import app.workouttracker.data.SessionExerciseDetail
import app.workouttracker.data.SetEntry
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.data.WorkoutSession
import app.workouttracker.ui.plan.formatWeight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What's typed in an exercise's entry row. Kept as text so a field can be empty mid-edit. */
data class Entry(val weight: String, val reps: String)

class SessionViewModel(
    private val db: WorkoutDatabase,
    private val sessionId: Long,
    private val prefs: android.content.SharedPreferences,
) : ViewModel() {
    private val dao = db.sessionDao()

    val session: StateFlow<WorkoutSession?> = dao.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val exercises: StateFlow<List<SessionExerciseDetail>> = dao.observeExercises(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sets: StateFlow<List<SetEntry>> = dao.observeSets(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val library: StateFlow<List<Exercise>> = db.exerciseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sets from the last session that included each exercise, keyed by exercise id. */
    val lastTime = mutableStateMapOf<Long, List<SetEntry>>()

    /** Entry rows keyed by session exercise id. */
    val entries = mutableStateMapOf<Long, Entry>()

    var restSeconds by mutableStateOf(prefs.getInt("rest_seconds", 90))
        private set

    /** When the running rest timer ends, in epoch millis, or null when no timer is running. */
    var restEndsAt by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            exercises.collect { list -> list.forEach { prepare(it) } }
        }
    }

    private suspend fun prepare(item: SessionExerciseDetail) {
        if (item.exerciseId !in lastTime) {
            lastTime[item.exerciseId] = dao.lastTimeSets(item.exerciseId, sessionId)
        }
        if (item.id !in entries) {
            val logged = dao.setsFor(sessionId, item.exerciseId)
            entries[item.id] = suggestion(item, setNumber = logged.size + 1, previous = logged.lastOrNull())
        }
    }

    /**
     * Prefill for set [setNumber]: the same set from last time, else the set just logged,
     * else the template target.
     */
    private fun suggestion(item: SessionExerciseDetail, setNumber: Int, previous: SetEntry?): Entry {
        val last = lastTime[item.exerciseId].orEmpty()
        val match = last.getOrNull(setNumber - 1) ?: previous ?: last.lastOrNull()
        return when {
            match != null -> Entry(formatWeight(match.weight), match.reps.toString())
            else -> Entry(item.targetWeight?.let(::formatWeight).orEmpty(), item.targetReps?.toString().orEmpty())
        }
    }

    fun setEntry(item: SessionExerciseDetail, entry: Entry) {
        entries[item.id] = entry
    }

    fun logSet(item: SessionExerciseDetail) {
        val entry = entries[item.id] ?: return
        val reps = entry.reps.toIntOrNull() ?: return
        val weight = entry.weight.toDoubleOrNull() ?: 0.0
        val setNumber = sets.value.count { it.exerciseId == item.exerciseId } + 1
        viewModelScope.launch {
            val logged = SetEntry(
                sessionId = sessionId,
                exerciseId = item.exerciseId,
                setNumber = setNumber,
                weight = weight,
                reps = reps,
                loggedAt = System.currentTimeMillis(),
            )
            dao.insertSet(logged)
            entries[item.id] = suggestion(item, setNumber + 1, previous = logged)
            startRest()
        }
    }

    fun deleteSet(set: SetEntry) {
        viewModelScope.launch {
            db.withTransaction {
                dao.deleteSet(set.id)
                // Renumber the exercise's remaining sets so they stay 1, 2, 3...
                sets.value.filter { it.exerciseId == set.exerciseId && it.id != set.id }
                    .sortedBy { it.setNumber }
                    .forEachIndexed { i, s ->
                        if (s.setNumber != i + 1) dao.renumberSet(s.id, i + 1)
                    }
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            val position = dao.maxPosition(sessionId) + 1
            dao.insertExercises(listOf(SessionExercise(sessionId = sessionId, exerciseId = exercise.id, position = position)))
        }
    }

    fun removeExercise(item: SessionExerciseDetail) {
        viewModelScope.launch {
            dao.deleteExercise(item.id)
            entries.remove(item.id)
        }
    }

    fun rename(name: String) {
        val current = session.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch { dao.update(current.copy(name = name.trim())) }
    }

    fun finish(onDone: () -> Unit) {
        val current = session.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                dao.update(current.copy(finishedAt = System.currentTimeMillis()))
                current.scheduledWorkoutId?.let { db.scheduleDao().setStatus(it, ScheduleStatus.COMPLETED.name) }
            }
            restEndsAt = null
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            dao.delete(sessionId)
            onDone()
        }
    }

    fun startRest() {
        restEndsAt = System.currentTimeMillis() + restSeconds * 1000L
    }

    fun adjustRest(deltaSeconds: Int) {
        restSeconds = (restSeconds + deltaSeconds).coerceIn(15, 600)
        prefs.edit().putInt("rest_seconds", restSeconds).apply()
        restEndsAt = restEndsAt?.plus(deltaSeconds * 1000L)
    }

    fun stopRest() {
        restEndsAt = null
    }

    companion object {
        fun prefs(context: Context) = context.getSharedPreferences("logging", Context.MODE_PRIVATE)
    }
}
