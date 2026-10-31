package de.max.gedaechtnis.kim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.kim.engine.DistractionKind
import de.max.gedaechtnis.kim.engine.distraction.Task
import kotlinx.coroutines.delay

private val StroopColors = listOf(
    Color(0xFFF07167), Color(0xFF6C8CFF), Color(0xFF6BD49A), Color(0xFFF5E050), Color(0xFFB28CFF), Color(0xFFFF9A3D),
)

private val StroopNames = listOf(R.string.color_red, R.string.color_blue, R.string.color_green, R.string.color_yellow, R.string.color_purple, R.string.color_orange)

/** 7.4 Kim – Ablenkung (oder leere Pause bei Stufe 1–2). */
@Composable
fun DistractScreen(vm: KimViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val d by vm.distract.collectAsStateWithLifecycle()
    val cfg = state.round?.config ?: return

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.label_distraction), Modifier.weight(1f), AppColors.LeitnerAccent)
            if (d.task != null) Text(stringResource(R.string.task_of, d.index + 1, d.planned), style = AppText.Body12.copy(color = AppColors.TextSecondary))
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatCountdown(timer.remainingMs), style = AppText.Mono24.copy(color = AppColors.LeitnerAccent))
            Box(Modifier.padding(start = 12.dp).weight(1f)) { LinearBar(timer.fraction, AppColors.LeitnerAccent) }
        }
        val task = d.task
        if (task == null) {
            Text(
                stringResource(if (cfg.distraction?.kind != DistractionKind.NONE) R.string.distract_loading else R.string.pause_empty),
                style = AppText.Body14.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 8.dp),
            )
            Box(Modifier.weight(1f))
        } else {
            Text(stringResource(R.string.hint_distraction), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(bottom = 4.dp))
            when (task) {
                is Task.Calc -> NumberTask(
                    prompt = task.expression + " =", expected = task.answer, feedback = d.feedback, taskKey = task.key,
                    onDone = { ok, sol -> vm.onTaskResult(ok, sol) },
                )
                is Task.Count -> CountTask(task, d.feedback) { ok, sol -> vm.onTaskResult(ok, sol) }
                is Task.Stroop -> StroopTask(task, d.feedback) { ok, sol -> vm.onTaskResult(ok, sol) }
                is Task.Reaction -> ReactionTask(task, d.feedback) { ok, sol -> vm.onTaskResult(ok, sol) }
            }
        }
        Box(
            Modifier.padding(top = 8.dp, bottom = 12.dp).fillMaxWidth().heightIn(min = 48.dp).clip(ButtonShape)
                .border(1.dp, AppColors.Outline, ButtonShape).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.time_up_to_recall), style = AppText.Body13.copy(color = AppColors.TextSecondary)) }
    }
}

@Composable
private fun ColumnScope.NumberTask(
    prompt: String,
    expected: Int,
    feedback: TaskFeedback?,
    taskKey: String,
    onDone: (Boolean, String) -> Unit,
) {
    var input by remember(taskKey) { mutableStateOf("") }
    TaskBody(prompt, input, feedback, onDigit = { if (input.length < 5) input += it }, onDelete = { input = input.dropLast(1) }) {
        input.toIntOrNull()?.let { v -> onDone(v == expected, expected.toString()) }
    }
}

@Composable
private fun ColumnScope.CountTask(task: Task.Count, feedback: TaskFeedback?, onDone: (Boolean, String) -> Unit) {
    var step by remember(task.key) { mutableStateOf(0) }
    var input by remember(task.key) { mutableStateOf("") }
    val current = task.start - task.step * step
    val prompt = "$current − ${task.step} ="
    TaskBody(prompt, input, feedback, onDigit = { if (input.length < 6) input += it }, onDelete = { input = input.dropLast(1) }) {
        val v = input.toIntOrNull() ?: return@TaskBody
        val expected = current - task.step
        input = ""
        when {
            v != expected -> onDone(false, expected.toString())
            step + 1 >= task.steps -> onDone(true, "")
            else -> step += 1
        }
    }
}

@Composable
private fun ColumnScope.TaskBody(
    prompt: String,
    input: String,
    feedback: TaskFeedback?,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
) {
    val borderColor = when {
        feedback == null -> AppColors.Outline
        feedback.correct -> AppColors.Success
        else -> AppColors.Error
    }
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(prompt, style = AppText.Mono36, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(width = 132.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, borderColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(input, style = AppText.Mono24) }
        Text(
            if (feedback != null && !feedback.correct) stringResource(R.string.wrong_solution, feedback.solution) else "",
            style = AppText.Body13.copy(color = AppColors.Error), modifier = Modifier.height(24.dp).padding(top = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        NumberPad(enabled = feedback == null, onDigit = onDigit, onDelete = onDelete, onOk = onOk)
    }
}

/** Ziffernblock 3 × 4 (1–9, Löschen, 0, OK in Cyan), Tasten 60 dp hoch. */
@Composable
fun NumberPad(enabled: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val ok = k == "OK"
                    val shape = RoundedCornerShape(12.dp)
                    val del = stringResource(R.string.delete)
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(shape)
                            .background(if (ok) AppColors.LeitnerAccent else AppColors.Surface)
                            .border(1.dp, if (ok) AppColors.LeitnerAccent else AppColors.Outline, shape)
                            .clickable(enabled = enabled) { when (k) { "OK" -> onOk(); "⌫" -> onDelete(); else -> onDigit(k) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(painterResource(R.drawable.ui_backspace), del, tint = AppColors.TextSecondary)
                        else Text(k, style = AppText.Mono22.copy(color = if (ok) AppColors.OnLeitnerAccent else AppColors.TextPrimary))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.StroopTask(task: Task.Stroop, feedback: TaskFeedback?, onDone: (Boolean, String) -> Unit) {
    var index by remember(task.key) { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.stroop_prompt), style = AppText.Body13.copy(color = AppColors.TextSecondary))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            task.words.forEachIndexed { i, w ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(StroopNames[w]).uppercase(),
                        style = AppText.H20.copy(color = StroopColors[task.inks[i]]),
                    )
                    Box(Modifier.padding(top = 4.dp).size(width = 28.dp, height = 3.dp).background(if (i == index && feedback == null) AppColors.TextPrimary else Color.Transparent))
                }
            }
        }
        Text(
            if (feedback != null && !feedback.correct) stringResource(R.string.wrong_solution, feedback.solution) else "",
            style = AppText.Body13.copy(color = AppColors.Error), modifier = Modifier.height(24.dp).padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StroopColors.indices.toList().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val name = stringResource(StroopNames[c])
                        Box(
                            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp)).background(AppColors.Surface)
                                .clickable(enabled = feedback == null) {
                                    if (c != task.inks[index]) onDone(false, name)
                                    else if (index + 1 >= task.inks.size) onDone(true, "")
                                    else index += 1
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(StroopColors[c]))
                        }
                    }
                }
            }
        }
    }
}

private enum class ReactionState { WAITING, GO }

@Composable
private fun ColumnScope.ReactionTask(task: Task.Reaction, feedback: TaskFeedback?, onDone: (Boolean, String) -> Unit) {
    var state by remember(task) { mutableStateOf(ReactionState.WAITING) }
    LaunchedEffect(task) {
        delay(task.delayMs)
        state = ReactionState.GO
        delay(1_500)
        if (state == ReactionState.GO) onDone(false, "")
    }
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val go = state == ReactionState.GO
        Box(
            Modifier.size(180.dp).clip(CircleShape)
                .background((if (go) AppColors.Success else AppColors.Error).copy(alpha = 0.18f))
                .border(3.dp, if (go) AppColors.Success else AppColors.Error, CircleShape)
                .clickable(enabled = feedback == null) { onDone(go, "") },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (go) R.string.reaction_go else R.string.reaction_wait), style = AppText.H20)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reaction_hint), style = AppText.Body13.copy(color = AppColors.TextSecondary))
    }
}
