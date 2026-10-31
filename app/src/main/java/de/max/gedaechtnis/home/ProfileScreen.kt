package de.max.gedaechtnis.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.data.Settings
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.reminder.Reminders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSound(on: Boolean) = viewModelScope.launch { repo.setSound(on) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { repo.setHaptics(on) }

    fun setReminder(on: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        repo.setReminder(on, hour, minute)
        if (on) Reminders.schedule(context, hour, minute) else Reminders.cancel(context)
    }
}

/** Profil: Ton, Haptik und tägliche Erinnerung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var pickTime by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Ohne Erlaubnis bleibt die Erinnerung zwar geplant, zeigt aber keine Mitteilung.
        vm.setReminder(true, settings.reminderHour, settings.reminderMinute)
        if (!granted) Unit
    }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin)) {
        Text(stringResource(R.string.nav_profile), style = AppText.H24, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        SectionLabel(stringResource(R.string.label_feedback), Modifier.padding(bottom = 4.dp))
        SwitchRow(R.drawable.ui_volume_up, stringResource(R.string.setting_sound), settings.sound, vm::setSound)
        SwitchRow(R.drawable.ui_notifications, stringResource(R.string.setting_haptics), settings.haptics, vm::setHaptics)

        SectionLabel(stringResource(R.string.label_reminder), Modifier.padding(top = 16.dp, bottom = 4.dp))
        SwitchRow(R.drawable.ui_schedule, stringResource(R.string.setting_reminder), settings.reminderOn) { on ->
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else vm.setReminder(on, settings.reminderHour, settings.reminderMinute)
        }
        if (settings.reminderOn) {
            SecondaryButton(
                stringResource(R.string.reminder_time, "%02d:%02d".format(settings.reminderHour, settings.reminderMinute)),
                { pickTime = true }, Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Text(stringResource(R.string.reminder_hint), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 8.dp))

        SectionLabel(stringResource(R.string.label_about), Modifier.padding(top = 20.dp, bottom = 4.dp))
        Text(stringResource(R.string.about_offline), style = AppText.Body13.copy(color = AppColors.TextSecondary))
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        Text(stringResource(R.string.about_version, version), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
    }

    if (pickTime) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.reminder_pick)) },
            text = { TimePicker(state) },
            confirmButton = { TextButton({ vm.setReminder(true, state.hour, state.minute); pickTime = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.Surface,
        )
    }
}

@Composable
private fun SwitchRow(icon: Int, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), null, tint = AppColors.TextSecondary)
        Text(label, style = AppText.Body15, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.KimAccent, checkedThumbColor = AppColors.OnKimAccent, uncheckedTrackColor = AppColors.Surface, uncheckedThumbColor = AppColors.TextSecondary, uncheckedBorderColor = AppColors.Outline),
        )
    }
}
