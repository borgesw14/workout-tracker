package app.workouttracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.workouttracker.MainActivity
import app.workouttracker.R
import app.workouttracker.WorkoutApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The daily reminder setting, kept in SharedPreferences. Off until the user turns it on. */
data class ReminderSettings(val enabled: Boolean, val time: LocalTime) {
    companion object {
        private const val PREFS = "reminders"

        fun load(context: Context): ReminderSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ReminderSettings(
                enabled = p.getBoolean("enabled", false),
                time = LocalTime.of(p.getInt("hour", 8), p.getInt("minute", 0)),
            )
        }

        fun save(context: Context, settings: ReminderSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", settings.enabled)
                .putInt("hour", settings.time.hour)
                .putInt("minute", settings.time.minute)
                .apply()
            Reminders.schedule(context, settings)
        }
    }
}

object Reminders {
    const val CHANNEL_ID = "workout_reminders"
    private const val WORK_NAME = "daily_workout_reminder"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "A morning nudge on days you have a workout planned"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Runs the check once a day at the chosen time, or cancels it when reminders are off. */
    fun schedule(context: Context, settings: ReminderSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(settings.time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}

/** Posts a notification when today has planned workouts. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = (applicationContext as WorkoutApp).database
        val planned = db.scheduleDao().plannedOn(LocalDate.now().toEpochDay())
        if (planned.isNotEmpty()) {
            val names = planned.joinToString(", ") { it.templateName }
            Reminders.post(applicationContext, "Workout today", names)
        }
        return Result.success()
    }
}
