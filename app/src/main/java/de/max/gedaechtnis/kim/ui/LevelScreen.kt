package de.max.gedaechtnis.kim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.engine.DistractionKind
import de.max.gedaechtnis.kim.engine.LevelSpec
import de.max.gedaechtnis.kim.engine.Mode
import java.util.Locale

/** 7.2 Kim – Modus und Stufe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelScreen(vm: KimViewModel, balancing: Balancing, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val s = settings ?: return
    val progress = s.progressOf(s.mode)
    val width = LocalConfiguration.current.screenWidthDp

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(R.drawable.ui_arrow_back, stringResource(R.string.back), onBack, filled = false)
                Text(stringResource(R.string.kim_title), style = AppText.H20, modifier = Modifier.padding(start = 4.dp))
            }
            if (state.voided) {
                Box(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, AppColors.Error, RoundedCornerShape(14.dp)).clickable { vm.clearVoided() }.padding(12.dp),
                ) { Text(stringResource(R.string.round_voided), style = AppText.Body13) }
            }
            SectionLabel(stringResource(R.string.label_mode), Modifier.padding(top = 12.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m ->
                    AppChip(stringResource(m.nameRes()), s.mode == m, { vm.selectMode(m) })
                }
            }
            SectionLabel(stringResource(R.string.label_level), Modifier.padding(top = 8.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                balancing.levels.forEach { spec ->
                    val locked = spec.level > progress.unlocked
                    val done = spec.level < progress.unlocked
                    val selected = !s.adaptive && s.level == spec.level && !locked
                    LevelRow(
                        spec = spec, locked = locked, done = done, selected = selected,
                        streak = if (spec.level == progress.unlocked) progress.successStreak else 0,
                        required = balancing.promotion.rounds,
                        enabled = !s.adaptive && !locked,
                        onClick = { vm.selectLevel(spec.level) },
                    )
                }
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(CardShape).background(AppColors.Surface)
                    .border(1.dp, AppColors.Outline, CardShape)
                    .clickable { vm.setAdaptive(!s.adaptive) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adaptive_title), style = AppText.Semi14)
                    Text(
                        stringResource(R.string.adaptive_value, String.format(Locale.GERMANY, "%.1f", s.adaptiveState.d)),
                        style = AppText.Body12.copy(color = AppColors.TextSecondary),
                    )
                }
                Checkbox(
                    checked = s.adaptive, onCheckedChange = { vm.setAdaptive(it) },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.KimAccent, checkmarkColor = AppColors.OnKimAccent, uncheckedColor = AppColors.TextSecondary),
                )
            }
            Spacer(Modifier.padding(6.dp))
        }
        Box(Modifier.padding(horizontal = ScreenMargin, vertical = 12.dp)) {
            val label = if (s.adaptive) stringResource(R.string.start_round_adaptive)
            else stringResource(R.string.start_round_level, s.level.coerceIn(1, progress.unlocked.coerceAtMost(balancing.maxLevel)))
            PrimaryButton(label, onClick = { vm.startRound(width) })
        }
    }

    state.outcome?.takeIf { it.suggestDemotion }?.let { o ->
        AlertDialog(
            onDismissRequest = vm::dismissDemotion,
            title = { Text(stringResource(R.string.demotion_title)) },
            text = { Text(stringResource(R.string.demotion_text, progress.unlocked)) },
            confirmButton = { TextButton(onClick = vm::acceptDemotion) { Text(stringResource(R.string.demotion_accept)) } },
            dismissButton = { TextButton(onClick = vm::dismissDemotion) { Text(stringResource(R.string.demotion_keep)) } },
            containerColor = AppColors.Surface,
        )
    }
}

@Composable
private fun LevelRow(
    spec: LevelSpec, locked: Boolean, done: Boolean, selected: Boolean, streak: Int, required: Int,
    enabled: Boolean, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = AppColors.KimAccent
    Row(
        Modifier.fillMaxWidth().alpha(if (locked) 0.55f else 1f).clip(shape)
            .background(if (selected) accent.copy(alpha = 0.10f) else AppColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${spec.level}", style = AppText.Mono18.copy(color = if (selected) accent else AppColors.TextSecondary), modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(levelNameRes(spec.level)), style = AppText.H16.copy(color = if (selected) accent else AppColors.TextPrimary))
            Text(paramLine(spec), style = AppText.Body12.copy(color = AppColors.TextSecondary))
        }
        val status = when {
            locked -> stringResource(R.string.level_status_locked)
            done -> stringResource(R.string.level_status_done)
            else -> stringResource(R.string.level_status_progress, streak, required)
        }
        Text(status, style = AppText.Body12.copy(color = if (done) AppColors.Success else if (selected) accent else AppColors.TextSecondary))
    }
}

@Composable
private fun paramLine(s: LevelSpec): String {
    val objects = if (s.objectsMin == s.objectsMax) "${s.objectsMin}" else "${s.objectsMin}–${s.objectsMax}"
    val changes = if (s.changesMin == s.changesMax) "${s.changesMax}" else "${s.changesMin}–${s.changesMax}"
    val changeText = if (s.changesMin == s.changesMax && s.changesMax <= 2) {
        pluralStringResource(R.plurals.params_changes, s.changesMax, s.changesMax)
    } else changes
    val parts = mutableListOf(stringResource(R.string.params_objects, objects), stringResource(R.string.params_seconds, s.memorizeSec), changeText)
    when {
        s.oneErrorLosesRound -> parts += stringResource(R.string.params_one_error)
        s.exactPositions -> parts += stringResource(R.string.params_exact)
        s.unknownCount -> parts += stringResource(R.string.params_unknown)
        s.distraction == DistractionKind.CALC -> parts += stringResource(R.string.params_calc)
    }
    return parts.joinToString(" · ")
}
