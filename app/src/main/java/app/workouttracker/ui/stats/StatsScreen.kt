package app.workouttracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.workouttracker.WorkoutApp
import app.workouttracker.settings.LocalWeightUnit
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("M/d")
private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onOpenSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as WorkoutApp
    val vm: StatsViewModel = viewModel { StatsViewModel(app.database) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            summary?.let { SummaryTiles(it) }
            if (exercises.isEmpty()) {
                Text(
                    "Finish a workout in the Log tab and your progress shows up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProgressCard(vm)
            }
            VolumeCard(vm)
        }
    }
}

@Composable
private fun SummaryTiles(s: Summary) {
    val unit = LocalWeightUnit.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile("This week", "${s.workoutsThisWeek}", if (s.workoutsThisWeek == 1) "workout" else "workouts", Modifier.weight(1f))
        Tile("Lifted this week", formatNumber(s.volumeThisWeek), unit.label, Modifier.weight(1f))
        val pct = if (s.due == 0) "–" else "${(s.completed * 100.0 / s.due).roundToInt()}%"
        Tile("Plan, last 4 weeks", pct, "${s.completed} of ${s.due} done", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, caption: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressCard(vm: StatsViewModel) {
    val unit = LocalWeightUnit.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val exercise by vm.selectedExercise.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val points by vm.progress.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    // -1 means "the latest point", so a new workout is selected automatically.
    var selected by remember(exercise, metric) { mutableIntStateOf(-1) }
    val index = if (selected in points.indices) selected else points.lastIndex
    var showTable by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = exercise?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Exercise") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    exercises.forEach { e ->
                        DropdownMenuItem(text = { Text(e.name) }, onClick = { vm.choose(e); open = false })
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProgressMetric.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == metric,
                        onClick = { vm.metric.value = m },
                        shape = SegmentedButtonDefaults.itemShape(i, ProgressMetric.entries.size),
                    ) { Text(m.label) }
                }
            }

            if (points.isNotEmpty()) {
                val p = points[index]
                val best = points.maxOf { it.value }
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${formatNumber(p.value)} ${unit.label}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${p.date.format(longDate)} · best set ${p.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column {
                        Text("Best", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatNumber(best)} ${unit.label}", style = MaterialTheme.typography.titleMedium)
                    }
                }
                LineChart(points.map { it.value }, index, { selected = it })
                Text(
                    if (metric == ProgressMetric.OneRepMax) {
                        "Estimated one-rep max from your best set each workout. Tap a point to see it."
                    } else {
                        "Heaviest set each workout. Tap a point to see it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showTable = !showTable }) { Text(if (showTable) "Hide data" else "Show data") }
                if (showTable) {
                    points.asReversed().forEach {
                        Row {
                            Text(it.date.format(longDate), Modifier.weight(1f))
                            Text(it.detail, Modifier.weight(1f))
                            Text("${formatNumber(it.value)} ${unit.label}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(vm: StatsViewModel) {
    val unit = LocalWeightUnit.current
    val weeks by vm.weekly.collectAsStateWithLifecycle()
    if (weeks.isEmpty()) return
    var selected by remember { mutableIntStateOf(-1) }
    val index = if (selected in weeks.indices) selected else weeks.lastIndex
    val w = weeks[index]

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly volume", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatNumber(w.volume)} ${unit.label}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Week of ${w.weekStart.format(longDate)} · ${w.workouts} ${if (w.workouts == 1) "workout" else "workouts"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarChart(
                values = weeks.map { it.volume },
                labels = weeks.map { it.weekStart.format(shortDate) },
                selected = index,
                onSelect = { selected = it },
            )
            Text(
                "Weight × reps added up across every set, last 8 weeks. Tap a bar to see that week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
