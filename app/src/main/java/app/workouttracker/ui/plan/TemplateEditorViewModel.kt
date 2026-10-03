package app.workouttracker.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.workouttracker.data.Exercise
import app.workouttracker.data.TemplateExercise
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.data.WorkoutTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One editable exercise row. Numbers are kept as text so fields can be briefly empty while typing. */
data class EditableSlot(
    val key: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val sets: String,
    val reps: String,
    val weight: String,
)

class TemplateEditorViewModel(
    private val db: WorkoutDatabase,
    private val templateId: Long,
) : ViewModel() {

    val isNew = templateId == 0L
    var name by mutableStateOf("")
    var notes by mutableStateOf("")
    val slots = mutableStateListOf<EditableSlot>()
    var loaded by mutableStateOf(isNew)
        private set

    val library: StateFlow<List<Exercise>> = db.exerciseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextKey = 1L

    init {
        if (!isNew) viewModelScope.launch {
            val dao = db.templateDao()
            dao.get(templateId)?.let {
                name = it.name
                notes = it.notes
            }
            dao.exercisesFor(templateId).forEach {
                slots += EditableSlot(
                    key = nextKey++,
                    exerciseId = it.exerciseId,
                    exerciseName = it.exerciseName,
                    sets = it.targetSets.toString(),
                    reps = it.targetReps.toString(),
                    weight = it.targetWeight?.let(::formatWeight).orEmpty(),
                )
            }
            loaded = true
        }
    }

    fun addExercise(exercise: Exercise) {
        slots += EditableSlot(nextKey++, exercise.id, exercise.name, sets = "3", reps = "10", weight = "")
    }

    fun update(index: Int, slot: EditableSlot) {
        slots[index] = slot
    }

    fun remove(index: Int) {
        slots.removeAt(index)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in slots.indices) return
        val item = slots.removeAt(index)
        slots.add(target, item)
    }

    val canSave: Boolean get() = name.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            db.withTransaction {
                val dao = db.templateDao()
                val id = if (isNew) {
                    dao.insert(WorkoutTemplate(name = name.trim(), notes = notes.trim()))
                } else {
                    dao.update(WorkoutTemplate(id = templateId, name = name.trim(), notes = notes.trim()))
                    dao.clearExercises(templateId)
                    templateId
                }
                dao.insertExercises(
                    slots.mapIndexed { i, s ->
                        TemplateExercise(
                            templateId = id,
                            exerciseId = s.exerciseId,
                            position = i,
                            targetSets = s.sets.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            targetReps = s.reps.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            targetWeight = s.weight.toDoubleOrNull(),
                        )
                    }
                )
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (isNew) return onDone()
        viewModelScope.launch {
            db.templateDao().delete(templateId)
            onDone()
        }
    }
}

/** 100.0 -> "100", 22.5 -> "22.5". */
fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
