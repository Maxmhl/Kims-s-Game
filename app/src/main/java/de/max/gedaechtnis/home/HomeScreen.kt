package de.max.gedaechtnis.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.data.Settings
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.data.TodayState
import de.max.gedaechtnis.data.TrainingRepository
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.ui.levelNameRes
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import de.max.gedaechtnis.data.LeitnerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HomeState(val today: TodayState?, val settings: Settings?, val decks: Int, val deckMode: LeitnerMode?)

@HiltViewModel
class HomeViewModel @Inject constructor(
    training: TrainingRepository,
    settings: SettingsRepository,
    leitner: LeitnerRepository,
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(training.todayState(), settings.settings, leitner.deckList) { t, s, d ->
        HomeState(t, s, d.size, d.firstOrNull { it.id == s.lastDeckId }?.mode ?: d.firstOrNull()?.mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(null, null, 0, null))
}

/** 7.1 Start. */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    balancing: Balancing,
    onSettings: () -> Unit,
    onContinue: (HomeTarget) -> Unit,
    onOpenKim: () -> Unit,
    onOpenLeitner: () -> Unit,
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val today = st.today
    val settings = st.settings
    if (today == null || settings == null) return
    val progress = settings.progressOf(settings.mode)
    val level = progress.unlocked.coerceAtMost(balancing.maxLevel)
    val nextTarget = when {
        !today.kimDone -> HomeTarget.KIM
        !today.cardsDone -> HomeTarget.CARDS
        !today.dailyDone -> HomeTarget.DAILY
        else -> null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY))
                Text(date, style = AppText.Body13.copy(color = AppColors.TextSecondary))
                Text(stringResource(R.string.app_name), style = AppText.H24, modifier = Modifier.padding(top = 2.dp))
            }
            AppIconButton(R.drawable.ui_settings, stringResource(R.string.settings), onSettings)
        }

        AppCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.today_training), style = AppText.H16, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.done_count, today.doneCount), style = AppText.Mono12.copy(color = AppColors.TextSecondary))
                }
                Spacer(Modifier.height(8.dp))
                CheckItem(stringResource(R.string.todo_kim, level), today.kimDone, AppColors.KimAccent)
                CheckItem(stringResource(R.string.todo_cards, today.cardsDue), today.cardsDone, AppColors.LeitnerAccent)
                CheckItem(stringResource(R.string.todo_daily), today.dailyDone, AppColors.KimAccent)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (nextTarget != null) AppColors.TextPrimary else AppColors.Outline)
                        .clickable(enabled = nextTarget != null) { nextTarget?.let(onContinue) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (nextTarget != null) R.string.continue_training else R.string.training_done),
                        style = AppText.Semi14.copy(color = if (nextTarget != null) AppColors.Background else AppColors.TextSecondary),
                    )
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.streak_days, today.streak), style = AppText.Mono24)
                Text(stringResource(R.string.streak_label), style = AppText.Body12.copy(color = AppColors.TextSecondary))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                today.last7.forEachIndexed { i, done ->
                    val isToday = i == today.last7.lastIndex
                    DayBox(done, open = isToday && !done)
                }
            }
        }

        SectionLabel(stringResource(R.string.label_games), Modifier.padding(top = 18.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kim
            AppCard(Modifier.weight(1f).clickable(onClick = onOpenKim)) {
                Column(Modifier.padding(14.dp)) {
                    GameBadge(AppColors.KimAccent, R.drawable.ui_extension)
                    Text(stringResource(R.string.kim_title), style = AppText.H17, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        if (progress.unlocked > balancing.maxLevel) stringResource(R.string.kim_master)
                        else stringResource(R.string.kim_level_line, level, stringResource(levelNameRes(level))),
                        style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 2.dp),
                    )
                    LinearBar(progress.successStreak.toFloat() / balancing.promotion.rounds, AppColors.KimAccent, Modifier.padding(top = 10.dp), height = 4.dp)
                    Text(
                        if (progress.unlocked > balancing.maxLevel) stringResource(R.string.kim_all_done)
                        else stringResource(R.string.kim_progress_line, progress.successStreak, balancing.promotion.rounds, level + 1),
                        style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // Lernkartei
            AppCard(Modifier.weight(1f).clickable(onClick = onOpenLeitner)) {
                Column(Modifier.padding(14.dp)) {
                    GameBadge(AppColors.LeitnerAccent, R.drawable.ui_style)
                    Text(stringResource(R.string.leitner_title), style = AppText.H17, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        stringResource(R.string.leitner_line, st.decks, st.deckMode?.let { stringResource(modeName(it)) } ?: "–"),
                        style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 2.dp),
                    )
                    Text("${today.cardsDue}", style = AppText.Mono34.copy(color = AppColors.LeitnerAccent), modifier = Modifier.padding(top = 6.dp))
                    Text(stringResource(R.string.cards_due_today), style = AppText.Body12.copy(color = AppColors.TextSecondary))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

enum class HomeTarget { KIM, CARDS, DAILY }

private fun modeName(m: LeitnerMode) = when (m) {
    LeitnerMode.NORMAL -> R.string.diff_normal
    LeitnerMode.STRICT -> R.string.diff_strict
    LeitnerMode.HARD -> R.string.diff_hard
}

@Composable
private fun GameBadge(color: Color, icon: Int) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun CheckItem(text: String, done: Boolean, color: Color) {
    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (done) AppColors.Success else Color.Transparent)
                .border(1.5.dp, if (done) AppColors.Success else color, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(painterResource(R.drawable.ui_check), null, tint = AppColors.Background, modifier = Modifier.size(14.dp)) }
        Text(
            text, modifier = Modifier.padding(start = 10.dp),
            style = AppText.Body14.copy(
                color = if (done) AppColors.TextSecondary else AppColors.TextPrimary,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
    }
}

@Composable
private fun DayBox(done: Boolean, open: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val r = CornerRadius(5.dp.toPx())
        if (done) drawRoundRect(AppColors.KimAccent, cornerRadius = r)
        else drawRoundRect(
            if (open) AppColors.KimAccent else AppColors.Outline, size = Size(size.width, size.height), cornerRadius = r,
            style = Stroke(1.5.dp.toPx(), pathEffect = if (open) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null),
        )
    }
}
