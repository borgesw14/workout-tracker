package app.workouttracker.ui.log

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.workouttracker.WorkoutApp
import app.workouttracker.data.SessionExerciseDetail
import app.workouttracker.data.SetEntry
import app.workouttracker.settings.LocalWeightUnit
import app.workouttracker.ui.plan.ExercisePickerDialog
import app.workouttracker.ui.plan.formatWeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(sessionId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkoutApp
    val vm: SessionViewModel = viewModel(key = "session-$sessionId") {
        SessionViewModel(app.database, sessionId, SessionViewModel.prefs(app))
    }
    val session by vm.session.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val finished = session?.finishedAt != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.name.orEmpty())
                        session?.finishedAt?.let {
                            Text("Finished ${formatDateTime(it)}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename workout")
                    }
                    IconButton(onClick = { confirmDiscard = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete workout")
                    }
                },
            )
        },
        bottomBar = {
            vm.restEndsAt?.let { endsAt ->
                RestTimerBar(
                    endsAt = endsAt,
                    onAdjust = vm::adjustRest,
                    onStop = vm::stopRest,
                    onDone = {
                        vibrate(context)
                        vm.stopRest()
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(exercises, key = { it.id }) { item ->
                ExerciseCard(
                    item = item,
                    logged = sets.filter { it.exerciseId == item.exerciseId },
                    lastTime = vm.lastTime[item.exerciseId].orEmpty(),
                    entry = vm.entries[item.id] ?: Entry("", ""),
                    onEntry = { vm.setEntry(item, it) },
                    onLog = { vm.logSet(item) },
                    onDeleteSet = vm::deleteSet,
                    onRemove = { vm.removeExercise(item) },
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (!finished) {
                item {
                    Button(
                        onClick = { vm.finish(onClose) },
                        enabled = sets.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Finish workout") }
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

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Delete this workout?") },
            text = { Text("All the sets in it are deleted too.") },
            confirmButton = { TextButton(onClick = { vm.discard(onClose) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(session?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename workout") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseCard(
    item: SessionExerciseDetail,
    logged: List<SetEntry>,
    lastTime: List<SetEntry>,
    entry: Entry,
    onEntry: (Entry) -> Unit,
    onLog: () -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onRemove: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.exerciseName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (logged.isEmpty()) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${item.exerciseName}")
                    }
                }
            }
            targetText(item, unit.label)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lastTime.isNotEmpty()) {
                Text(
                    "Last time: " + lastTime.joinToString(", ") { "${formatWeight(it.weight)} ${unit.label} × ${it.reps}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            logged.forEach { set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${set.setNumber}", modifier = Modifier.width(64.dp))
                    Text(
                        "${formatWeight(set.weight)} ${unit.label} × ${set.reps}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteSet(set) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete set ${set.setNumber}")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Stepper(
                    label = "Weight (${unit.label})",
                    value = entry.weight,
                    decimal = true,
                    onValue = { onEntry(entry.copy(weight = it)) },
                    onStep = { dir ->
                        val next = ((entry.weight.toDoubleOrNull() ?: 0.0) + dir * unit.step).coerceAtLeast(0.0)
                        onEntry(entry.copy(weight = formatWeight(next)))
                    },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = "Reps",
                    value = entry.reps,
                    decimal = false,
                    onValue = { onEntry(entry.copy(reps = it)) },
                    onStep = { dir ->
                        val next = ((entry.reps.toIntOrNull() ?: 0) + dir).coerceAtLeast(0)
                        onEntry(entry.copy(reps = next.toString()))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onLog,
                enabled = (entry.reps.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) {
                Text("Log set ${logged.size + 1}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun targetText(item: SessionExerciseDetail, unit: String): String? {
    val sets = item.targetSets ?: return null
    val reps = item.targetReps ?: return null
    val weight = item.targetWeight?.let { " @ ${formatWeight(it)} $unit" }.orEmpty()
    return "Target: $sets × $reps$weight"
}

/** A number field with big − and + buttons either side, for one-handed use at the gym. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    decimal: Boolean,
    onValue: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier,
) {
    val pattern = if (decimal) Regex("""\d{0,4}(\.\d{0,2})?""") else Regex("""\d{0,3}""")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onStep(-1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Less $label")
            }
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.matches(pattern)) onValue(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            FilledTonalIconButton(onClick = { onStep(1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "More $label")
            }
        }
    }
}

@Composable
private fun RestTimerBar(endsAt: Long, onAdjust: (Int) -> Unit, onStop: () -> Unit, onDone: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endsAt) {
                onDone()
                break
            }
            delay(250)
        }
    }
    val remaining = ((endsAt - now + 999) / 1000).coerceAtLeast(0)
    // The app's bottom navigation already sits below this, so no system inset here.
    BottomAppBar(modifier = Modifier.height(72.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
        Text("Rest", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        Text(
            "%d:%02d".format(remaining / 60, remaining % 60),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        TextButton(onClick = { onAdjust(-15) }) { Text("−15s") }
        TextButton(onClick = { onAdjust(15) }) { Text("+15s") }
        TextButton(onClick = onStop) { Text("Skip") }
    }
}

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
}

private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
