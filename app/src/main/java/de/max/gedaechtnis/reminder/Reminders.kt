package de.max.gedaechtnis.reminder

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.max.gedaechtnis.MainActivity
import de.max.gedaechtnis.R
import de.max.gedaechtnis.data.AppDatabase
import de.max.gedaechtnis.data.Clock
import de.max.gedaechtnis.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL = "reminder"
private const val WORK_NAME = "daily_reminder"

object Reminders {

    fun createChannel(context: Context) {
        val ch = NotificationChannel(REMINDER_CHANNEL, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Verzögerung bis zur nächsten Uhrzeit [hour]:[minute]. */
    fun delayUntil(now: LocalDateTime, hour: Int, minute: Int): Duration {
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val delay = delayUntil(LocalDateTime.now(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun database(): AppDatabase
    fun settings(): SettingsRepository
    fun clock(): Clock
}

/** Tägliche Erinnerung, nur wenn Karten fällig sind. Plant sich danach selbst für den nächsten Tag. */
class ReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val s = ep.settings().settings.first()
        if (!s.reminderOn) return Result.success()
        val due = ep.database().decks().dueCount(ep.clock().today())
        if (due > 0 && canNotify()) notify(due)
        Reminders.schedule(context, s.reminderHour, s.reminderMinute)
        return Result.success()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // canNotify() wurde vor dem Aufruf geprüft
    private fun notify(due: Int) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.resources.getQuantityString(R.plurals.reminder_text, due, due))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) }
    }
}
