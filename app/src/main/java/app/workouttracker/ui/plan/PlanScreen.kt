package app.workouttracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.workouttracker.WorkoutApp
import app.workouttracker.data.ScheduleStatus
import app.workouttracker.data.ScheduledItem
import app.workouttracker.data.TemplateSummary
import app.workouttracker.reminders.ReminderDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onEditTemplate: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as WorkoutApp
    val vm: PlanViewModel = viewModel { PlanViewModel(app.database) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showReminder by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan") },
                actions = {
                    IconButton(onClick = { showReminder = true }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Workout reminder")
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { onEditTemplate(0L) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New template") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Calendar") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Templates") })
            }
            when (tab) {
                0 -> CalendarTab(vm, onCreateTemplate = { tab = 1; onEditTemplate(0L) })
                else -> TemplatesTab(vm, onEditTemplate)
            }
        }
    }

    if (showReminder) ReminderDialog(onDismiss = { showReminder = false })
}

@Composable
private fun CalendarTab(vm: PlanViewModel, onCreateTemplate: () -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val scheduled by vm.scheduled.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    var scheduling by remember { mutableStateOf(false) }
    val dayItems = scheduled[selected].orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            MonthGrid(
                month = month,
                selected = selected,
                scheduled = scheduled,
                onPrev = { vm.showMonth(-1) },
                onNext = { vm.showMonth(1) },
                onSelect = vm::select,
            )
            HorizontalDivider()
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            ) {
                Text(
                    selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (templates.isEmpty()) onCreateTemplate() else scheduling = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Schedule", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (dayItems.isEmpty()) {
            item {
                Text(
                    if (templates.isEmpty()) "Create a template first, then schedule it here."
                    else "Nothing planned. Rest day?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(dayItems, key = { it.id }) { item ->
            ScheduledRow(
                item = item,
                onStatus = { vm.setStatus(item, it) },
                onRemove = { vm.remove(item) },
            )
        }
    }

    if (scheduling) {
        ScheduleDialog(
            day = selected,
            templates = templates,
            onDismiss = { scheduling = false },
            onSchedule = { templateId, extraWeeks ->
                vm.schedule(templateId, selected, extraWeeks)
                scheduling = false
            },
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    scheduled: Map<LocalDate, List<ScheduledItem>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val weekdays = (0L until 7L).map { firstDow.plus(it) }
    val leading = (month.atDay(1).dayOfWeek.value - firstDow.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row {
            weekdays.forEach { dow: DayOfWeek ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(day, day == selected, day == today, scheduled[day].orEmpty()) { onSelect(day) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    items: List<ScheduledItem>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(2.dp)
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            color = if (isSelected) colors.onPrimary else colors.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            items.take(3).forEach { item ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor(item.status, onPrimary = isSelected)),
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: String, onPrimary: Boolean = false): Color {
    val colors = MaterialTheme.colorScheme
    if (onPrimary) return colors.onPrimary
    return when (status) {
        ScheduleStatus.COMPLETED.name -> Color(0xFF2E9E5B)
        ScheduleStatus.SKIPPED.name -> colors.outline
        else -> colors.primary
    }
}

@Composable
private fun ScheduledRow(item: ScheduledItem, onStatus: (ScheduleStatus) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val statusLabel = when (item.status) {
        ScheduleStatus.COMPLETED.name -> "Done"
        ScheduleStatus.SKIPPED.name -> "Skipped"
        else -> "Planned"
    }
    ListItem(
        leadingContent = { Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(item.status))) },
        headlineContent = { Text(item.templateName) },
        supportingContent = { Text(statusLabel) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${item.templateName}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (item.status != ScheduleStatus.COMPLETED.name) {
                        DropdownMenuItem(text = { Text("Mark done") }, onClick = { onStatus(ScheduleStatus.COMPLETED); menu = false })
                    }
                    if (item.status != ScheduleStatus.SKIPPED.name) {
                        DropdownMenuItem(text = { Text("Mark skipped") }, onClick = { onStatus(ScheduleStatus.SKIPPED); menu = false })
                    }
                    if (item.status != ScheduleStatus.PLANNED.name) {
                        DropdownMenuItem(text = { Text("Mark planned") }, onClick = { onStatus(ScheduleStatus.PLANNED); menu = false })
                    }
                    DropdownMenuItem(text = { Text("Remove from this day") }, onClick = { onRemove(); menu = false })
                }
            }
        },
    )
}

private val RepeatOptions = listOf(
    "Just this day" to 0,
    "Weekly for 4 weeks" to 3,
    "Weekly for 8 weeks" to 7,
    "Weekly for 12 weeks" to 11,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    day: LocalDate,
    templates: List<TemplateSummary>,
    onDismiss: () -> Unit,
    onSchedule: (templateId: Long, extraWeeks: Int) -> Unit,
) {
    var template by remember { mutableStateOf(templates.first()) }
    var repeat by remember { mutableStateOf(RepeatOptions.first()) }
    var templateOpen by remember { mutableStateOf(false) }
    var repeatOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule for ${day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = templateOpen, onExpandedChange = { templateOpen = it }) {
                    OutlinedTextField(
                        value = template.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Workout") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(templateOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = templateOpen, onDismissRequest = { templateOpen = false }) {
                        templates.forEach { t ->
                            DropdownMenuItem(text = { Text(t.name) }, onClick = { template = t; templateOpen = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = repeatOpen, onExpandedChange = { repeatOpen = it }) {
                    OutlinedTextField(
                        value = repeat.first,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repeatOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = repeatOpen, onDismissRequest = { repeatOpen = false }) {
                        RepeatOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option.first) }, onClick = { repeat = option; repeatOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSchedule(template.id, repeat.second) }) { Text("Schedule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TemplatesTab(vm: PlanViewModel, onEditTemplate: (Long) -> Unit) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    if (templates.isEmpty()) {
        Text(
            "No templates yet. A template is a reusable workout, like \"Push day\", with its exercises and targets.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(templates, key = { it.id }) { t ->
            ListItem(
                headlineContent = { Text(t.name) },
                supportingContent = {
                    Text(if (t.exerciseCount == 1) "1 exercise" else "${t.exerciseCount} exercises")
                },
                modifier = Modifier.clickable { onEditTemplate(t.id) },
            )
            HorizontalDivider()
        }
    }
}
