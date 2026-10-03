package app.workouttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import app.workouttracker.WorkoutApp
import app.workouttracker.data.DefaultExercises
import app.workouttracker.data.Exercise
import app.workouttracker.data.WorkoutDatabase
import app.workouttracker.reminders.ReminderDialog
import app.workouttracker.settings.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ClearKind(val title: String, val body: String, val done: String) {
    History(
        "Clear workout history?",
        "Deletes every workout and set you've logged, and un-marks completed days on the calendar. " +
            "Your exercises, templates and schedule stay.",
        "Workout history cleared",
    ),
    Everything(
        "Clear all data?",
        "Deletes all workouts, sets, templates, scheduled days and your own exercises, " +
            "and restores the starter exercise list. This can't be undone.",
        "All data cleared",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as WorkoutApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showReminder by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ClearKind?>(null) }
    val unit by app.weightUnit.unit.collectAsState()
    var switchTo by remember { mutableStateOf<WeightUnit?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Workout reminder") },
                supportingContent = { Text("A notification on days you have a workout planned") },
                trailingContent = { TextButton(onClick = { showReminder = true }) { Text("Change") } },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Weight unit") },
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = u == unit,
                                onClick = { if (u != unit) switchTo = u },
                                shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                            ) { Text(u.label) }
                        }
                    }
                },
            )
            HorizontalDivider()
            Text(
                "Your data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                OutlinedButton(
                    onClick = { confirm = ClearKind.History },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear workout history") }
                OutlinedButton(
                    onClick = { confirm = ClearKind.Everything },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear all data") }
            }
        }
    }

    if (showReminder) ReminderDialog(onDismiss = { showReminder = false })

    switchTo?.let { target ->
        AlertDialog(
            onDismissRequest = { switchTo = null },
            title = { Text("Switch to ${target.label}?") },
            text = {
                Text(
                    "Convert changes every logged weight and template target to ${target.label}, rounded to 0.1. " +
                        "Keep numbers only changes the label. Use it if you've been entering ${target.label} all along."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    switchTo = null
                    scope.launch {
                        convertWeights(app.database, if (target == WeightUnit.KG) LB_TO_KG else 1 / LB_TO_KG)
                        app.weightUnit.set(target)
                        snackbar.showSnackbar("Weights converted to ${target.label}")
                    }
                }) { Text("Convert") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { switchTo = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        app.weightUnit.set(target)
                        switchTo = null
                    }) { Text("Keep numbers") }
                }
            },
        )
    }

    confirm?.let { kind ->
        ConfirmClearDialog(
            kind = kind,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                scope.launch {
                    clear(app.database, kind)
                    snackbar.showSnackbar(kind.done)
                }
            },
        )
    }
}

/** For "Clear all data" the button only enables once DELETE is typed, so it can't happen by a stray tap. */
@Composable
private fun ConfirmClearDialog(kind: ClearKind, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val needsTyping = kind == ClearKind.Everything
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(kind.body)
                if (needsTyping) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type DELETE to confirm") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !needsTyping || typed.trim() == "DELETE",
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private suspend fun clear(db: WorkoutDatabase, kind: ClearKind) {
    withContext(Dispatchers.IO) {
        when (kind) {
            ClearKind.History -> db.withTransaction {
                db.sessionDao().deleteAll()
                db.scheduleDao().clearCompleted()
            }
            ClearKind.Everything -> {
                db.clearAllTables()
                DefaultExercises.forEach { (category, names) ->
                    names.forEach { db.exerciseDao().insert(Exercise(name = it, category = category)) }
                }
            }
        }
    }
}

private const val LB_TO_KG = 0.45359237

private suspend fun convertWeights(db: WorkoutDatabase, factor: Double) {
    withContext(Dispatchers.IO) {
        db.withTransaction {
            db.sessionDao().scaleSetWeights(factor)
            db.sessionDao().scaleSessionTargets(factor)
            db.templateDao().scaleTargets(factor)
        }
    }
}
