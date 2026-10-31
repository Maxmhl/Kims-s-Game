package de.max.gedaechtnis.kim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.kim.engine.*
import java.util.Locale
import kotlin.math.roundToInt

/** 7.6 Kim – Ergebnis. */
@Composable
fun ResultScreen(vm: KimViewModel, onToStart: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val outcome = state.outcome ?: return
    val round = outcome.round
    val cfg = round.config
    val eval = outcome.evaluation
    val width = LocalConfiguration.current.screenWidthDp

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel(
                if (cfg.level != null) stringResource(R.string.label_result_level, cfg.level) else stringResource(R.string.label_result_adaptive),
                Modifier.padding(top = 16.dp), AppColors.KimAccent,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text("${outcome.points}", style = AppText.Mono44)
                Text(stringResource(R.string.points), style = AppText.Body13.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${(eval.accuracy * 100).roundToInt()} %", style = AppText.Mono34.copy(color = AppColors.KimAccent))
                    Text(stringResource(R.string.hit_rate), style = AppText.Body12.copy(color = AppColors.TextSecondary))
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Modifier.weight(1f), "${eval.hits} / ${eval.hits + eval.missed}", stringResource(R.string.tile_hits), AppColors.Success)
                Tile(Modifier.weight(1f), "${eval.falseClicks}", stringResource(R.string.tile_false_clicks), AppColors.Error)
                Tile(Modifier.weight(1f), "${eval.missed}", stringResource(R.string.tile_missed), AppColors.KimAccent)
            }
            if (eval.lost) {
                Text(stringResource(R.string.round_lost), style = AppText.Body13.copy(color = AppColors.Error), modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(Modifier.height(14.dp))
            BeforeAfter(outcome, vm)
            Legend()

            Column(Modifier.padding(top = 12.dp)) {
                eval.details.forEach { DetailLine(it, cfg.mode) }
            }
            Text(
                noteText(outcome), style = AppText.Body12.copy(color = AppColors.TextSecondary),
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
        Row(Modifier.padding(bottom = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.to_start), { vm.toStart(); onToStart() }, Modifier.weight(1f).height(52.dp))
            Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.next_round), { vm.startRound(width) }) }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        Text(value, style = AppText.Mono22.copy(color = color))
        Text(label, style = AppText.Body12.copy(color = AppColors.TextSecondary))
    }
}

@Composable
private fun BeforeAfter(outcome: RoundOutcome, vm: KimViewModel) {
    val round = outcome.round
    val cfg = round.config
    val details = outcome.evaluation.details
    val byPos = details.filter { it.position >= 0 }.associateBy { it.position }
    fun markOf(i: Int): CellMark = when (byPos[i]?.outcome) {
        Outcome.FOUND -> CellMark.FOUND
        Outcome.FALSE_CLICK -> CellMark.FALSE_CLICK
        Outcome.MISSED -> CellMark.MISSED
        null -> CellMark.NONE
    }
    val beforeMarks = { i: Int -> if (byPos[i] != null && byPos[i]?.outcome != Outcome.FALSE_CLICK) CellMark.REFERENCE else CellMark.NONE }
    val answerCells: List<Cell?>? = when (cfg.mode) {
        Mode.CHANGED, Mode.MISSING -> round.changed
        Mode.RECONSTRUCTION -> {
            val placed = (outcome.answer as? Answer.Placement)?.placed.orEmpty()
            List(cfg.objectCount) { i -> placed[i]?.let { id -> round.options.firstOrNull { it.id == id }?.let { Cell(it, 0) } } }
        }
        Mode.SEQUENCE -> {
            val given = (outcome.answer as? Answer.Order)?.ids.orEmpty()
            List(cfg.objectCount) { i -> given.getOrNull(i)?.let { id -> round.options.firstOrNull { it.id == id }?.let { Cell(it, 0) } } }
        }
        Mode.FREE_RECALL -> null
    }
    val answerMark: (Int) -> CellMark = when (cfg.mode) {
        Mode.CHANGED -> ::markOf
        Mode.MISSING -> ::markOf
        Mode.RECONSTRUCTION -> { i -> if (answerCells?.getOrNull(i) != null) markFor(byPos[i]) else if (byPos[i]?.outcome == Outcome.MISSED) CellMark.MISSED else CellMark.NONE }
        Mode.SEQUENCE -> ::markOf
        Mode.FREE_RECALL -> { _ -> CellMark.NONE }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.label_before), style = AppText.Medium14.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(bottom = 6.dp))
            MiniGrid(round.original, cfg.columns) { i -> if (cfg.mode == Mode.FREE_RECALL || cfg.mode == Mode.SEQUENCE) markOf(i) else beforeMarks(i) }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.label_your_answer), style = AppText.Medium14.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(bottom = 6.dp))
            if (answerCells != null) MiniGrid(answerCells, cfg.columns, answerMark)
            else {
                val named = (outcome.answer as? Answer.Names)?.bases.orEmpty()
                Text(named.joinToString(", ") { vm.catalogLabel(it) }.ifEmpty { "–" }, style = AppText.Body13.copy(color = AppColors.TextSecondary))
            }
        }
    }
}

private fun markFor(d: Detail?): CellMark = when (d?.outcome) {
    Outcome.FOUND -> CellMark.FOUND
    Outcome.FALSE_CLICK -> CellMark.FALSE_CLICK
    Outcome.MISSED -> CellMark.MISSED
    null -> CellMark.NONE
}

@Composable
private fun MiniGrid(cells: List<Cell?>, columns: Int, mark: (Int) -> CellMark) {
    val gap = 3.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w: Dp = (maxWidth - gap * (columns - 1)) / columns
        val rows = (cells.size + columns - 1) / columns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until columns) {
                        val i = r * columns + c
                        if (i < cells.size) GridCell(cells[i], Modifier.size(w), mark = mark(i), iconSize = w * 0.62f, corner = 5.dp)
                        else Spacer(Modifier.size(w))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(CellMark.FOUND, stringResource(R.string.legend_found))
        LegendItem(CellMark.FALSE_CLICK, stringResource(R.string.legend_false_click))
        LegendItem(CellMark.MISSED, stringResource(R.string.legend_missed))
    }
}

@Composable
private fun LegendItem(mark: CellMark, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GridCell(null, Modifier.size(14.dp), mark = mark, corner = 3.dp)
        Text(label, style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun DetailLine(d: Detail, mode: Mode) {
    val obj = d.objectAfter ?: d.objectBefore
    val name = obj?.label ?: d.label ?: ""
    val text = when {
        mode == Mode.CHANGED && d.position >= 0 -> {
            val verb = d.type?.let { stringResource(it.verbRes()) } ?: stringResource(R.string.change_unchanged)
            stringResource(R.string.line_field, d.position + 1, "${d.objectBefore?.label ?: name} $verb")
        }
        d.position >= 0 && mode == Mode.SEQUENCE -> stringResource(R.string.line_position, d.position + 1, d.objectBefore?.label ?: name)
        d.position >= 0 -> stringResource(R.string.line_field, d.position + 1, d.objectBefore?.label ?: name)
        else -> stringResource(R.string.line_not_present, name)
    }
    val (label, color) = when (d.outcome) {
        Outcome.FOUND -> stringResource(R.string.outcome_found) to AppColors.Success
        Outcome.MISSED -> stringResource(R.string.outcome_missed) to AppColors.KimAccent
        Outcome.FALSE_CLICK -> stringResource(R.string.outcome_false_click) to AppColors.Error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = AppText.Body13, modifier = Modifier.weight(1f))
        Text(label, style = AppText.Medium14.copy(color = color), modifier = Modifier.padding(start = 8.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Outline))
}

@Composable
private fun noteText(o: RoundOutcome): String = when (val n = o.note) {
    ProgressNote.None -> ""
    is ProgressNote.Adaptive -> stringResource(R.string.note_adaptive, String.format(Locale.GERMANY, "%.1f", n.d))
    is ProgressNote.LevelUp -> if (n.next != null) stringResource(R.string.note_level_up, n.level, n.next) else stringResource(R.string.note_master_done)
    is ProgressNote.Streak -> if (n.counted) stringResource(R.string.note_counts, n.level, n.streak, n.required)
    else stringResource(R.string.note_not_counted, n.threshold, n.level, n.streak, n.required)
}
