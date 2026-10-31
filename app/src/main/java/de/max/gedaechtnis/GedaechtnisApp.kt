package de.max.gedaechtnis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GedaechtnisApp : Application() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val s = settings.settings.first()
            if (s.reminderOn) Reminders.schedule(this@GedaechtnisApp, s.reminderHour, s.reminderMinute)
        }
    }
}
