package app.workouttracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workouttracker.data.Exercise
import app.workouttracker.data.ExerciseDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel(private val dao: ExerciseDao) : ViewModel() {

    /** Exercises grouped by category, in display order. */
    val byCategory: StateFlow<Map<String, List<Exercise>>> = dao.observeAll()
        .map { list -> list.groupBy { it.category } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun add(name: String, category: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.insert(Exercise(name = trimmed, category = category.trim().ifEmpty { "Other" }))
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch { dao.delete(exercise) }
    }
}
