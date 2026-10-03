package app.workouttracker.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { ReminderSettings.load(context) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var denied by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(initial.time.hour, initial.time.minute, DateFormat.is24HourFormat(context))
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        denied = !granted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout reminder") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Notify me on days I have a workout planned",
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (on && !Reminders.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                enabled = on
                            }
                        },
                    )
                }
                if (denied) {
                    Text(
                        "Notifications are blocked. Allow them for Workout Tracker in Android settings.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (enabled) {
                    Text("Remind me at", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    TimeInput(state = time)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ReminderSettings.save(context, ReminderSettings(enabled, LocalTime.of(time.hour, time.minute)))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
