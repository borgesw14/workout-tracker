package app.workouttracker.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.workouttracker.WorkoutApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHomeScreen(onOpenSession: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as WorkoutApp
    val vm: LogHomeViewModel = viewModel { LogHomeViewModel(app.database) }
    val active by vm.active.collectAsStateWithLifecycle()
    val planned by vm.plannedToday.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Log") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = active
            if (current != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenSession(current.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("In progress", style = MaterialTheme.typography.labelLarge)
                            Text(current.name, style = MaterialTheme.typography.headlineSmall)
                            Button(
                                onClick = { onOpenSession(current.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                Text("Resume workout", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else {
                if (planned.isNotEmpty()) {
                    item { SectionTitle("Planned for today") }
                    items(planned, key = { "p${it.id}" }) { item ->
                        Button(
                            onClick = { vm.startScheduled(item, onOpenSession) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("Start ${item.templateName}", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (templates.isNotEmpty()) {
                    item { SectionTitle("Start from a template") }
                    items(templates, key = { "t${it.id}" }) { t ->
                        OutlinedButton(
                            onClick = { vm.startTemplate(t, onOpenSession) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text(t.name) }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { vm.startEmpty(onOpenSession) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Empty workout") }
                }
            }

            if (history.isNotEmpty()) {
                item { SectionTitle("History") }
                items(history, key = { "h${it.id}" }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = {
                            val sets = if (s.setCount == 1) "1 set" else "${s.setCount} sets"
                            Text("${formatDate(s.startedAt)} · $sets · ${"%,d".format(s.volume.roundToLong())} volume")
                        },
                        modifier = Modifier.clickable { onOpenSession(s.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
