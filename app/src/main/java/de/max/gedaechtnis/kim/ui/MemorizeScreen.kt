package de.max.gedaechtnis.kim.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.kim.engine.Mode
import de.max.gedaechtnis.kim.engine.Round

/** Gemeinsamer Kopf für Einprägen und Abfrage: Label, Titel, Countdown. */
@Composable
fun KimTitle(round: Round, adaptiveLabel: Boolean = round.config.level == null): String {
    val modeName = stringResource(round.config.mode.nameRes())
    return if (adaptiveLabel) stringResource(R.string.title_adaptive, modeName)
    else stringResource(R.string.title_level, round.config.level ?: 0, modeName)
}

/** 7.3 Kim – Einprägen. */
@Composable
fun MemorizeScreen(vm: KimViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val round = state.round ?: return
    val cfg = round.config
    var confirmAbort by remember { mutableStateOf(false) }
    val low = timer.remainingMs <= 5_000
    val color = if (low) AppColors.Error else AppColors.KimAccent

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.label_memorize), color = AppColors.KimAccent)
                Text(KimTitle(round), style = AppText.H20, modifier = Modifier.padding(top = 4.dp))
            }
            AppIconButton(R.drawable.ui_close, stringResource(R.string.close), { confirmAbort = true }, filled = false)
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatCountdown(timer.remainingMs), style = AppText.Mono34.copy(color = color))
            Box(Modifier.padding(start = 14.dp).weight(1f)) { LinearBar(timer.fraction, color, height = 6.dp) }
        }
        if (cfg.mode == Mode.SEQUENCE) {
            SequenceMemorize(round, timer.elapsedMs, Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                KimGrid(cfg.objectCount, cfg.columns, Modifier.padding(top = 8.dp)) { i, w, h, icon ->
                    GridCell(round.original[i], Modifier.size(w, h), iconSize = icon)
                }
            }
        }
        Text(
            stringResource(R.string.hint_memorize), style = AppText.Body12.copy(color = AppColors.TextSecondary),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        if (cfg.mode != Mode.SEQUENCE) {
            SecondaryButton(stringResource(R.string.memorized_done), vm::finishMemorizeEarly, Modifier.fillMaxWidth().padding(bottom = 12.dp))
        } else Spacer(Modifier.height(12.dp))
    }

    if (confirmAbort) AbortDialog(onConfirm = { confirmAbort = false; vm.abort() }, onDismiss = { confirmAbort = false })
}

@Composable
private fun SequenceMemorize(round: Round, elapsedMs: Long, modifier: Modifier) {
    val cfg = round.config
    val index = (elapsedMs / cfg.sequenceItemMs).toInt().coerceIn(0, round.sequence.size - 1)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.sequence_progress, index + 1, round.sequence.size), style = AppText.Body14.copy(color = AppColors.TextSecondary))
        Spacer(Modifier.height(16.dp))
        AnimatedContent(targetState = index, label = "sequence") { i ->
            Box(
                Modifier.size(160.dp).clip(RoundedCornerShape(24.dp)).background(AppColors.Surface)
                    .border(1.dp, AppColors.Outline, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) { ObjectIcon(round.sequence[i], 0, 96.dp) }
        }
    }
}

@Composable
fun AbortDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.abort_title)) },
        text = { Text(stringResource(R.string.abort_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.abort_confirm), color = AppColors.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.abort_continue)) } },
        containerColor = AppColors.Surface,
    )
}
