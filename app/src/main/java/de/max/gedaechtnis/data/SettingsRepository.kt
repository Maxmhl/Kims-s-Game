package de.max.gedaechtnis.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.max.gedaechtnis.kim.engine.AdaptiveState
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.engine.LevelProgress
import de.max.gedaechtnis.kim.engine.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val mode: Mode,
    val level: Int,
    val adaptive: Boolean,
    val adaptiveState: AdaptiveState,
    val progress: Map<Mode, LevelProgress>,
    val sound: Boolean,
    val haptics: Boolean,
    val reminderOn: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val lastDeckId: Long,
) {
    fun progressOf(mode: Mode): LevelProgress = progress[mode] ?: LevelProgress()
}

/** Einstellungen und Spielfortschritt (DataStore). */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val balancing: Balancing,
) {
    private val store get() = context.dataStore

    private object K {
        val mode = stringPreferencesKey("mode")
        val level = intPreferencesKey("level")
        val adaptive = booleanPreferencesKey("adaptive")
        val adaptiveD = doublePreferencesKey("adaptive_d")
        val adaptiveHigh = intPreferencesKey("adaptive_high")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val reminderOn = booleanPreferencesKey("reminder_on")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val lastDeck = longPreferencesKey("last_deck")
        fun progress(m: Mode) = stringPreferencesKey("progress_${m.name}")
    }

    val settings: Flow<Settings> = store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val mode = this[K.mode]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.CHANGED
        val progress = Mode.entries.associateWith { decodeProgress(this[K.progress(it)]) }
        return Settings(
            mode = mode,
            level = this[K.level] ?: 1,
            adaptive = this[K.adaptive] ?: false,
            adaptiveState = AdaptiveState(this[K.adaptiveD] ?: balancing.adaptive.start, this[K.adaptiveHigh] ?: 0),
            progress = progress,
            sound = this[K.sound] ?: true,
            haptics = this[K.haptics] ?: true,
            reminderOn = this[K.reminderOn] ?: false,
            reminderHour = this[K.reminderHour] ?: 19,
            reminderMinute = this[K.reminderMinute] ?: 0,
            lastDeckId = this[K.lastDeck] ?: -1L,
        )
    }

    private fun decodeProgress(s: String?): LevelProgress {
        val parts = s?.split(",")?.mapNotNull { it.toIntOrNull() }
        return if (parts != null && parts.size == 3) LevelProgress(parts[0], parts[1], parts[2]) else LevelProgress()
    }

    suspend fun setMode(mode: Mode) = store.edit { it[K.mode] = mode.name }
    suspend fun setLevel(level: Int) = store.edit { it[K.level] = level }
    suspend fun setAdaptive(on: Boolean) = store.edit { it[K.adaptive] = on }
    suspend fun setAdaptiveState(s: AdaptiveState) = store.edit { it[K.adaptiveD] = s.d; it[K.adaptiveHigh] = s.highStreak }
    suspend fun setProgress(mode: Mode, p: LevelProgress) =
        store.edit { it[K.progress(mode)] = "${p.unlocked},${p.successStreak},${p.lowStreak}" }
    suspend fun setSound(on: Boolean) = store.edit { it[K.sound] = on }
    suspend fun setHaptics(on: Boolean) = store.edit { it[K.haptics] = on }
    suspend fun setReminder(on: Boolean, hour: Int, minute: Int) =
        store.edit { it[K.reminderOn] = on; it[K.reminderHour] = hour; it[K.reminderMinute] = minute }
    suspend fun setLastDeck(id: Long) = store.edit { it[K.lastDeck] = id }
}
