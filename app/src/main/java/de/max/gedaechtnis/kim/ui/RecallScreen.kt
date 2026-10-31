package de.max.gedaechtnis.kim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.kim.engine.Cell
import de.max.gedaechtnis.kim.engine.Mode
import de.max.gedaechtnis.kim.engine.Round
import kotlin.math.roundToInt

/** 7.5 Kim – Abfrage, für alle fünf Modi. */
@Composable
fun RecallScreen(vm: KimViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val round = state.round ?: return
    val cfg = round.config
    val input = state.input
    val haptic = LocalHapticFeedback.current
    val tick = { if (settings?.haptics != false) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    var confirmAbort by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.label_recall), color = AppColors.KimAccent)
                Text(stringResource(cfg.mode.nameRes()), style = AppText.H20, modifier = Modifier.padding(top = 4.dp))
            }
            if (cfg.answerSec != null) {
                Text(
                    formatCountdown(timer.remainingMs),
                    style = AppText.Mono24.copy(color = if (timer.remainingMs <= 5_000) AppColors.Error else AppColors.KimAccent),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            AppIconButton(R.drawable.ui_close, stringResource(R.string.close), { confirmAbort = true }, filled = false)
        }
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cfg.mode == Mode.CHANGED) {
                InfoChip(if (cfg.showChangeCount) stringResource(R.string.chip_changes_n, round.realChanges.size) else stringResource(R.string.chip_changes_unknown))
            }
            if (cfg.falseClickPenalty) InfoChip(stringResource(R.string.chip_false_click, 15))
            if (cfg.answerSec == null) InfoChip(stringResource(R.string.chip_no_time_limit))
        }

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                when (cfg.mode) {
                    Mode.CHANGED -> ChangedRecall(round, input, vm, tick)
                    Mode.MISSING -> MissingRecall(round, input, vm, tick)
                    Mode.RECONSTRUCTION -> ReconstructionRecall(round, input, vm, tick)
                    Mode.FREE_RECALL -> FreeRecall(round, input, vm)
                    Mode.SEQUENCE -> SequenceRecall(round, input, vm, tick)
                }
            }
        }

        Text(
            hintFor(round, input), style = AppText.Body12.copy(color = AppColors.TextSecondary),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (cfg.mode == Mode.CHANGED && cfg.changeMin == 0) {
            SecondaryButton(stringResource(R.string.no_change), { vm.submit(noChange = true) }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        PrimaryButton(stringResource(R.string.evaluate), { vm.submit() }, Modifier.padding(bottom = 12.dp))
    }

    if (confirmAbort) AbortDialog(onConfirm = { confirmAbort = false; vm.abort() }, onDismiss = { confirmAbort = false })
}

@Composable
private fun hintFor(round: Round, input: RecallInput): String = when (round.config.mode) {
    Mode.CHANGED -> stringResource(R.string.hint_changed, input.marked.size)
    Mode.MISSING -> stringResource(if (round.config.showChangeCount) R.string.hint_missing_n else R.string.hint_missing, round.realChanges.size)
    Mode.RECONSTRUCTION -> stringResource(R.string.hint_reconstruction)
    Mode.FREE_RECALL -> stringResource(R.string.hint_free_recall, input.names.size)
    Mode.SEQUENCE -> stringResource(if (round.config.reverseSequence) R.string.hint_sequence_backward else R.string.hint_sequence_forward)
}

@Composable
private fun ChangedRecall(round: Round, input: RecallInput, vm: KimViewModel, tick: () -> Unit) {
    val empty = stringResource(R.string.field_empty)
    KimGrid(round.config.objectCount, round.config.columns) { i, w, h, icon ->
        val cell = round.changed[i]
        GridCell(
            cell, Modifier.size(w, h), mark = if (i in input.marked) CellMark.SELECTED else CellMark.NONE, iconSize = icon,
            onClick = { vm.toggleMarked(i); tick() },
            description = cell?.obj?.label ?: empty,
        )
    }
}

@Composable
private fun MissingRecall(round: Round, input: RecallInput, vm: KimViewModel, tick: () -> Unit) {
    val cfg = round.config
    val empty = stringResource(R.string.field_empty)
    KimGrid(cfg.objectCount, cfg.columns) { i, w, h, icon ->
        GridCell(round.changed[i], Modifier.size(w, h), iconSize = icon, description = round.changed[i]?.obj?.label ?: empty)
    }
    SectionLabel(stringResource(R.string.label_options), Modifier.padding(top = 16.dp, bottom = 8.dp))
    KimGrid(round.options.size, 4) { i, w, h, icon ->
        val o = round.options[i]
        GridCell(
            Cell(o, 0), Modifier.size(w, h), mark = if (o.id in input.selectedObjects) CellMark.SELECTED else CellMark.NONE, iconSize = icon,
            onClick = { vm.toggleObject(o.id); tick() }, description = o.label,
        )
    }
}

@Composable
private fun ReconstructionRecall(round: Round, input: RecallInput, vm: KimViewModel, tick: () -> Unit) {
    val cfg = round.config
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var selected by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val placedIds = input.placed.values.toSet()
    val density = LocalDensity.current
    val half = with(density) { 28.dp.toPx() }
    val empty = stringResource(R.string.field_empty)

    fun drop(id: String, at: Offset) {
        val target = bounds.entries.firstOrNull { it.value.contains(at) }?.key
        if (target != null) { vm.place(target, id); tick() }
    }

    Box(Modifier.onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        Column {
            KimGrid(cfg.objectCount, cfg.columns) { i, w, h, icon ->
                val id = input.placed[i]
                val obj = id?.let { k -> round.options.firstOrNull { it.id == k } }
                Box(Modifier.size(w, h).onGloballyPositioned { bounds[i] = it.boundsInRoot() }) {
                    GridCell(
                        obj?.let { Cell(it, 0) }, Modifier.size(w, h), iconSize = icon,
                        onClick = {
                            if (obj != null) vm.unplace(i)
                            else selected?.let { s -> vm.place(i, s); selected = null; tick() }
                        },
                        description = obj?.label ?: empty,
                    )
                }
            }
            SectionLabel(stringResource(R.string.label_tray), Modifier.padding(top = 16.dp, bottom = 8.dp))
            val tray = round.options.filter { it.id !in placedIds }
            FlowTray(tray, selected, dragId, onSelect = { selected = if (selected == it) null else it }, onDragStart = { id, pos -> dragId = id; dragPos = pos }, onDrag = { dragPos += it }, onDragEnd = {
                dragId?.let { id -> drop(id, dragPos) }
                dragId = null
            })
        }
        dragId?.let { id ->
            round.options.firstOrNull { it.id == id }?.let { o ->
                Box(
                    Modifier.offset { IntOffset((dragPos.x - rootOrigin.x - half).roundToInt(), (dragPos.y - rootOrigin.y - half).roundToInt()) }
                        .size(56.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)
                        .border(2.dp, AppColors.KimAccent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { ObjectIcon(o, 0, 36.dp) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowTray(
    items: List<de.max.gedaechtnis.kim.engine.GameObject>,
    selected: String?,
    dragId: String?,
    onSelect: (String) -> Unit,
    onDragStart: (String, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { o ->
            var origin by remember(o.id) { mutableStateOf(Offset.Zero) }
            val isSel = selected == o.id
            Box(
                Modifier.size(56.dp).alpha(if (dragId == o.id) 0.3f else 1f).clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) AppColors.KimAccent.copy(alpha = 0.10f) else AppColors.Surface)
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) AppColors.KimAccent else AppColors.Outline, RoundedCornerShape(14.dp))
                    .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                    .clickable { onSelect(o.id) }
                    .pointerInput(o.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart(o.id, origin + it) },
                            onDrag = { change, amount -> change.consume(); onDrag(amount) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { ObjectIcon(o, 0, 36.dp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeRecall(round: Round, input: RecallInput, vm: KimViewModel) {
    var text by remember { mutableStateOf("") }
    val all = vm.catalogBases
    val q = text.trim().lowercase()
    val suggestions = if (q.isEmpty()) emptyList() else all.filter { (base, label) -> base !in input.names && label.lowercase().contains(q) }.take(8)
    fun commit(base: String) { vm.addName(base); text = "" }

    OutlinedTextField(
        value = text, onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.free_recall_placeholder), style = AppText.Mono18.copy(color = AppColors.TextSecondary)) },
        textStyle = AppText.Mono18, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { all.firstOrNull { it.second.lowercase() == q }?.let { commit(it.first) } ?: suggestions.firstOrNull()?.let { commit(it.first) } }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.KimAccent, unfocusedBorderColor = AppColors.Outline,
            focusedContainerColor = AppColors.SurfaceNav, unfocusedContainerColor = AppColors.SurfaceNav, cursorColor = AppColors.KimAccent,
        ),
    )
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { (base, label) -> AppChip(label, false, { commit(base) }) }
    }
    SectionLabel(stringResource(R.string.label_named), Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        input.names.forEach { base -> AppChip("${vm.catalogLabel(base)}  ✕", true, { vm.removeName(base) }) }
    }
}

@Composable
private fun SequenceRecall(round: Round, input: RecallInput, vm: KimViewModel, tick: () -> Unit) {
    val total = round.sequence.size
    val slotCols = minOf(total, 5)
    val empty = stringResource(R.string.field_empty)
    KimGrid(total, slotCols, maxCellHeight = 64.dp) { i, w, h, icon ->
        val id = input.order.getOrNull(i)
        val obj = id?.let { k -> round.options.firstOrNull { it.id == k } }
        GridCell(
            obj?.let { Cell(it, 0) }, Modifier.size(w, h), iconSize = icon, mark = if (obj != null) CellMark.SELECTED else CellMark.NONE,
            description = obj?.label ?: empty,
            badge = { Text("${i + 1}", style = AppText.Mono12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(4.dp)) },
        )
    }
    Row(Modifier.padding(top = 8.dp)) {
        SecondaryButton(stringResource(R.string.undo), { vm.popOrder() }, enabled = input.order.isNotEmpty())
    }
    SectionLabel(stringResource(R.string.label_options), Modifier.padding(top = 12.dp, bottom = 8.dp))
    KimGrid(round.options.size, 4) { i, w, h, icon ->
        val o = round.options[i]
        val used = o.id in input.order
        GridCell(
            Cell(o, 0), Modifier.size(w, h).alpha(if (used) 0.35f else 1f), iconSize = icon,
            onClick = if (used || input.order.size >= total) null else ({ vm.pushOrder(o.id); tick() }),
            description = o.label,
        )
    }
}
