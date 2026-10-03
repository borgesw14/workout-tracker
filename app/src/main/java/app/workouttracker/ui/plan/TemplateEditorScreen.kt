package app.workouttracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.workouttracker.WorkoutApp
import app.workouttracker.data.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(templateId: Long, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as WorkoutApp
    val vm: TemplateEditorViewModel = viewModel(key = "template-$templateId") {
        TemplateEditorViewModel(app.database, templateId)
    }
    val library by vm.library.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New template" else "Edit template") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete template")
                        }
                    }
                    TextButton(onClick = { vm.save(onClose) }, enabled = vm.canSave) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    label = { Text("Name, e.g. Push day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = vm.notes,
                    onValueChange = { vm.notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            itemsIndexed(vm.slots, key = { _, s -> s.key }) { index, slot ->
                SlotCard(
                    slot = slot,
                    isFirst = index == 0,
                    isLast = index == vm.slots.lastIndex,
                    onChange = { vm.update(index, it) },
                    onMove = { vm.move(index, it) },
                    onRemove = { vm.remove(index) },
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(
            library = library,
            onPick = {
                vm.addExercise(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${vm.name.ifBlank { "this template" }}?") },
            text = { Text("It's also removed from every day it's scheduled on. Workouts you've already logged stay.") },
            confirmButton = { TextButton(onClick = { vm.delete(onClose) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SlotCard(
    slot: EditableSlot,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (EditableSlot) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(slot.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${slot.exerciseName}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 12.dp)) {
                NumberField("Sets", slot.sets, Modifier.weight(1f)) { onChange(slot.copy(sets = it)) }
                NumberField("Reps", slot.reps, Modifier.weight(1f)) { onChange(slot.copy(reps = it)) }
                NumberField("Weight", slot.weight, Modifier.weight(1.3f), decimal = true) {
                    onChange(slot.copy(weight = it))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    val pattern = if (decimal) Regex("""\d{0,4}(\.\d{0,2})?""") else Regex("""\d{0,3}""")
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.matches(pattern)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ExercisePickerDialog(library: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = library.filter { it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    shown.groupBy { it.category }.forEach { (category, exercises) ->
                        item(key = "h-$category") {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        exercises.forEach { exercise ->
                            item(key = exercise.id) {
                                Text(
                                    exercise.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(exercise) }
                                        .padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
