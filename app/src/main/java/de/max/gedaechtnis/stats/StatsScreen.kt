package de.max.gedaechtnis.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.data.KimRepository
import de.max.gedaechtnis.data.LeitnerRepository
import de.max.gedaechtnis.data.RoundResult
import de.max.gedaechtnis.data.ReviewLog
import de.max.gedaechtnis.data.Card
import de.max.gedaechtnis.data.Clock
import de.max.gedaechtnis.data.TrainingRepository
import de.max.gedaechtnis.kim.engine.ChangeType
import de.max.gedaechtnis.kim.engine.Mode
import de.max.gedaechtnis.kim.ui.DetailRecord
import de.max.gedaechtnis.kim.ui.nameRes
import de.max.gedaechtnis.kim.ui.verbRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class DayPoint(val epochDay: Long, val accuracy: Float?, val level: Float?)
data class TypeStat(val type: ChangeType, val total: Int, val missed: Int) { val rate: Float get() = if (total == 0) 0f else missed.toFloat() / total }
data class Heat(val rows: Int, val cols: Int, val rate: Array<FloatArray>, val samples: Array<IntArray>)
data class DayBoxes(val epochDay: Long, val counts: List<Int>)

data class StatsState(
    val roundCount: Int = 0,
    val days: List<DayPoint> = emptyList(),
    val types: List<TypeStat> = emptyList(),
    val heat: Heat? = null,
    val bestPoints: Map<Mode, Int> = emptyMap(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val boxHistory: List<DayBoxes> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/** Reine Auswertung der gespeicherten Runden (für Tests zugänglich). */
object StatsCalc {
    fun dayPoints(rounds: List<RoundResult>, today: Long, days: Int = 14): List<DayPoint> =
        (today - days + 1..today).map { d ->
            val r = rounds.filter { it.epochDay == d }
            DayPoint(
                d,
                if (r.isEmpty()) null else r.map { it.accuracy }.average().toFloat(),
                r.mapNotNull { it.level }.maxOrNull()?.toFloat(),
            )
        }

    fun typeStats(rounds: List<RoundResult>): List<TypeStat> {
        val total = HashMap<ChangeType, Int>()
        val missed = HashMap<ChangeType, Int>()
        for (r in rounds) {
            if (r.mode != Mode.CHANGED.name && r.mode != Mode.MISSING.name) continue
            for (d in decode(r)) {
                val t = d.type?.let { runCatching { ChangeType.valueOf(it) }.getOrNull() } ?: continue
                if (t == ChangeType.FAKE) continue
                if (d.outcome == "FALSE_CLICK") continue
                total[t] = (total[t] ?: 0) + 1
                if (d.outcome == "MISSED") missed[t] = (missed[t] ?: 0) + 1
            }
        }
        return ChangeType.entries.filter { it != ChangeType.FAKE && (total[it] ?: 0) > 0 }
            .map { TypeStat(it, total.getValue(it), missed[it] ?: 0) }
            .sortedByDescending { it.rate }
    }

    fun heat(rounds: List<RoundResult>): Heat? {
        val grid = rounds.filter { it.mode == Mode.CHANGED.name || it.mode == Mode.MISSING.name || it.mode == Mode.RECONSTRUCTION.name }
        if (grid.isEmpty()) return null
        val maxCols = grid.maxOf { it.columns }
        val maxRows = grid.maxOf { (it.objectCount + it.columns - 1) / it.columns }
        val errors = Array(maxRows) { IntArray(maxCols) }
        val samples = Array(maxRows) { IntArray(maxCols) }
        for (r in grid) {
            for (i in 0 until r.objectCount) samples[i / r.columns][i % r.columns]++
            for (d in decode(r)) if (d.outcome != "FOUND") errors[d.row][d.col]++
        }
        val rate = Array(maxRows) { row -> FloatArray(maxCols) { c -> if (samples[row][c] == 0) 0f else errors[row][c].toFloat() / samples[row][c] } }
        return Heat(maxRows, maxCols, rate, samples)
    }

    private fun decode(r: RoundResult): List<DetailRecord> =
        runCatching { json.decodeFromString<List<DetailRecord>>(r.changesJson) }.getOrDefault(emptyList())

    /** Karten je Fach am Ende der letzten [days] Tage, aus Protokoll und Anlegedatum rekonstruiert. */
    fun boxHistory(cards: List<Card>, logs: List<ReviewLog>, today: Long, days: Int = 7, boxes: Int = 7): List<DayBoxes> {
        val byCard = logs.groupBy { it.cardId }
        return (today - days + 1..today).map { d ->
            val counts = IntArray(boxes)
            for (c in cards) {
                if (c.createdDay > d) continue
                val last = byCard[c.id]?.filter { it.date <= d }?.maxByOrNull { it.id }
                val box = (last?.boxAfter ?: 1).coerceIn(1, boxes)
                counts[box - 1]++
            }
            DayBoxes(d, counts.toList())
        }
    }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    kim: KimRepository,
    leitner: LeitnerRepository,
    training: TrainingRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<StatsState> = combine(kim.rounds, training.completedDays, leitner.reviewLogs, leitner.deckList) { rounds, completed, logs, _ ->
        val today = clock.today()
        StatsState(
            roundCount = rounds.size,
            days = StatsCalc.dayPoints(rounds, today),
            types = StatsCalc.typeStats(rounds),
            heat = StatsCalc.heat(rounds),
            bestPoints = rounds.groupBy { Mode.valueOf(it.mode) }.mapValues { (_, v) -> v.maxOf { it.points } },
            streak = Streak.current(completed, today),
            bestStreak = Streak.best(completed),
            boxHistory = StatsCalc.boxHistory(leitner.allCards(), logs, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}

@Composable
fun StatsScreen(vm: StatsViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin)) {
        Text(stringResource(R.string.nav_stats), style = AppText.H24, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        if (st.roundCount == 0 && st.boxHistory.all { it.counts.all { n -> n == 0 } }) {
            Text(stringResource(R.string.stats_empty), style = AppText.Body14.copy(color = AppColors.TextSecondary))
            return@Column
        }
        StatCard(stringResource(R.string.stats_history)) {
            Text(stringResource(R.string.stats_accuracy_per_day), style = AppText.Body12.copy(color = AppColors.TextSecondary))
            LineChart(st.days.map { it.accuracy }, 1f, AppColors.KimAccent, Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.stats_level_per_day), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 12.dp))
            LineChart(st.days.map { it.level }, 6f, AppColors.LeitnerAccent, Modifier.padding(top = 6.dp))
            DayAxis(st.days)
        }
        StatCard(stringResource(R.string.stats_error_types)) {
            if (st.types.isEmpty()) Text(stringResource(R.string.stats_no_data), style = AppText.Body13.copy(color = AppColors.TextSecondary))
            st.types.forEach { t ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(t.type.verbRes()), style = AppText.Body13, modifier = Modifier.width(96.dp))
                    Box(Modifier.weight(1f)) { LinearBar(t.rate, AppColors.KimAccent, height = 8.dp) }
                    Text("${(t.rate * 100).roundToInt()} %", style = AppText.Mono12, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                }
            }
            if (st.types.isNotEmpty()) Text(stringResource(R.string.stats_error_types_hint), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 6.dp))
        }
        StatCard(stringResource(R.string.stats_heatmap)) {
            val heat = st.heat
            if (heat == null) Text(stringResource(R.string.stats_no_data), style = AppText.Body13.copy(color = AppColors.TextSecondary))
            else {
                Heatmap(heat)
                Text(stringResource(R.string.stats_heatmap_hint), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 8.dp))
            }
        }
        StatCard(stringResource(R.string.stats_records)) {
            Row(Modifier.fillMaxWidth()) {
                RecordValue(Modifier.weight(1f), "${st.streak}", stringResource(R.string.stats_streak))
                RecordValue(Modifier.weight(1f), "${st.bestStreak}", stringResource(R.string.stats_best_streak))
            }
            Mode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(m.nameRes()), style = AppText.Body13, modifier = Modifier.weight(1f))
                    Text("${st.bestPoints[m] ?: 0}", style = AppText.Mono14.copy(color = AppColors.KimAccent))
                }
            }
        }
        StatCard(stringResource(R.string.stats_boxes_over_time)) {
            BoxHistoryChart(st.boxHistory)
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = AppText.H16, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun RecordValue(modifier: Modifier, value: String, label: String) {
    Column(modifier) {
        Text(value, style = AppText.Mono24)
        Text(label, style = AppText.Body12.copy(color = AppColors.TextSecondary))
    }
}

/** Verlaufskurve; Tage ohne Wert bleiben als Lücke. */
@Composable
fun LineChart(values: List<Float?>, maxValue: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width
        val h = size.height
        // Hilfslinien bei 0, 50 und 100 %
        for (f in listOf(0f, 0.5f, 1f)) drawLine(AppColors.Outline, Offset(0f, h - h * f), Offset(w, h - h * f), 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val step = w / (values.size - 1)
        var path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                if (open) { drawPath(path, color, style = Stroke(2.dp.toPx())); path = Path(); open = false }
                return@forEachIndexed
            }
            val p = Offset(i * step, h - h * (v / maxValue).coerceIn(0f, 1f))
            if (!open) { path.moveTo(p.x, p.y); open = true } else path.lineTo(p.x, p.y)
            drawCircle(color, 3.dp.toPx(), p)
        }
        if (open) drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun DayAxis(days: List<DayPoint>) {
    if (days.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(days.first(), days[days.size / 2], days.last()).forEach {
            val d = LocalDate.ofEpochDay(it.epochDay)
            Text("${d.dayOfMonth}.${d.monthValue}.", style = AppText.Body12.copy(color = AppColors.TextSecondary))
        }
    }
}

/** Fehler-Heatmap nach Rasterposition: je heller Amber, desto häufiger Fehler. */
@Composable
private fun Heatmap(heat: Heat) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0 until heat.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0 until heat.cols) {
                    val n = heat.samples[r][c]
                    val rate = heat.rate[r][c]
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (n == 0) Color.Transparent else AppColors.KimAccent.copy(alpha = 0.08f + 0.85f * rate)),
                        contentAlignment = Alignment.Center,
                    ) { if (n > 0) Text("${(rate * 100).roundToInt()}", style = AppText.Mono12.copy(color = if (rate > 0.5f) AppColors.OnKimAccent else AppColors.TextSecondary)) }
                }
            }
        }
    }
}

@Composable
private fun BoxHistoryChart(days: List<DayBoxes>) {
    val maxTotal = (days.maxOfOrNull { it.counts.sum() } ?: 0).coerceAtLeast(1)
    val boxesUsed = (days.maxOfOrNull { d -> d.counts.indexOfLast { it > 0 } + 1 } ?: 1).coerceAtLeast(1)
    val colors = listOf(AppColors.LeitnerAccent, Color(0xFF4BA3B5), Color(0xFF3E8392), AppColors.LeitnerBoxMuted, Color(0xFF244A52), Color(0xFF1B3A40), Color(0xFF142D32))
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                for (b in boxesUsed - 1 downTo 0) {
                    val n = d.counts[b]
                    if (n > 0) Box(Modifier.fillMaxWidth().height((100f * n / maxTotal).dp.coerceAtLeast(2.dp)).background(colors[b]))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            Text("${LocalDate.ofEpochDay(d.epochDay).dayOfMonth}.", style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.weight(1f))
        }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in 0 until boxesUsed) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(colors[b]))
            Text(" ${b + 1}", style = AppText.Body12.copy(color = AppColors.TextSecondary))
        }
    }
}
