package de.max.gedaechtnis.leitner.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 7.8 Lernkartei – Abfrage (Hart: Eingabe; Normal/Streng: Aufdecken und selbst bewerten). */
@Composable
fun ReviewScreen(vm: ReviewViewModel, onClose: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val timeLeft by vm.timeLeft.collectAsStateWithLifecycle()
    val accent = AppColors.LeitnerAccent
    val cur = st.current

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        if (st.loading) return@Column
        if (st.finished || cur == null) { FinishedView(st, onClose); return@Column }
        val hard = cur.deck.mode == LeitnerMode.HARD
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(R.drawable.ui_close, stringResource(R.string.close), onClose, filled = false)
            Text(
                stringResource(R.string.review_header, cur.deck.name, st.index + 1, st.queue.size),
                style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (hard) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Error.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(stringResource(R.string.badge_hard), style = AppText.Body12.copy(color = AppColors.Error))
                }
            }
        }
        LinearBar((st.index + (if (st.result != null) 1 else 0)).toFloat() / st.queue.size, accent, Modifier.padding(vertical = 8.dp), height = 4.dp)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val resultColor = when (st.result?.verdict) { ReviewVerdict.CORRECT -> AppColors.Success; null -> AppColors.Outline; else -> AppColors.Error }
            AppCard(Modifier.fillMaxWidth().padding(top = 8.dp), borderColor = resultColor) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.card_box_since, cur.card.box, cur.daysSince.toInt()),
                            style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.weight(1f),
                        )
                        if (hard && st.result == null) {
                            Text(formatCountdown(timeLeft), style = AppText.Mono18.copy(color = if (timeLeft <= 5_000) AppColors.Error else accent))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    CardImage(cur.card.imageUri)
                    Text(cur.question, style = AppText.H24.copy(fontSize = androidx.compose.ui.unit.TextUnit(25f, androidx.compose.ui.unit.TextUnitType.Sp)))
                    Spacer(Modifier.height(24.dp))
                    if (hard && st.result == null) {
                        val limit = 15_000f
                        LinearBar((timeLeft / limit), if (timeLeft <= 5_000) AppColors.Error else accent)
                    }
                    if (!hard && st.revealed) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Outline))
                        Text(cur.answer, style = AppText.H20.copy(color = accent), modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
            st.result?.let { r ->
                ResultBox(r, cur.answer, Modifier.padding(top = 12.dp))
            }
            if (hard && st.result == null) HardInput(vm)
        }

        // Aktionen
        Column(Modifier.padding(bottom = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                st.result != null -> PrimaryButton(stringResource(R.string.next_card), vm::next, accent = accent, onAccent = AppColors.OnLeitnerAccent)
                hard -> Unit
                !st.revealed -> PrimaryButton(stringResource(R.string.reveal), vm::reveal, accent = accent, onAccent = AppColors.OnLeitnerAccent)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.not_known), { vm.rate(false) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), textColor = AppColors.Error) }
                    Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.known), { vm.rate(true) }, accent = accent, onAccent = AppColors.OnLeitnerAccent) }
                }
            }
        }
    }
}

@Composable
private fun HardInput(vm: ReviewViewModel) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.answer_hint), style = AppText.Body12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.answer_placeholder), style = AppText.Mono18.copy(color = AppColors.TextSecondary)) },
            textStyle = AppText.Mono18, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.check(text) }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.LeitnerAccent, unfocusedBorderColor = AppColors.Outline,
                focusedContainerColor = AppColors.SurfaceNav, unfocusedContainerColor = AppColors.SurfaceNav, cursorColor = AppColors.LeitnerAccent,
            ),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(stringResource(R.string.check), { vm.check(text) }, accent = AppColors.LeitnerAccent, onAccent = AppColors.OnLeitnerAccent)
    }
}

@Composable
private fun ResultBox(r: ReviewResult, answer: String, modifier: Modifier) {
    val color = if (r.verdict == ReviewVerdict.CORRECT) AppColors.Success else AppColors.Error
    val text = when (r.verdict) {
        ReviewVerdict.CORRECT -> stringResource(R.string.review_correct, r.newBox)
        ReviewVerdict.WRONG -> stringResource(R.string.review_wrong, r.newBox)
        ReviewVerdict.TIMEOUT -> stringResource(R.string.review_timeout, r.newBox)
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(if (r.verdict == ReviewVerdict.CORRECT) 2.dp else 2.dp, color, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(text, style = AppText.Semi14.copy(color = color))
        if (r.verdict != ReviewVerdict.CORRECT) {
            Text(stringResource(R.string.correct_answer, answer), style = AppText.Mono14, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CardImage(uri: String?) {
    if (uri == null) return
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(bottom = 12.dp))
    }
}

@Composable
private fun FinishedView(st: ReviewState, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.review_done_title), style = AppText.H24)
        Text(
            if (st.queue.isEmpty()) stringResource(R.string.review_nothing_due)
            else stringResource(R.string.review_done_text, st.correctCount, st.queue.size),
            style = AppText.Body14.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(vertical = 12.dp),
        )
        PrimaryButton(stringResource(R.string.back), onClose, accent = AppColors.LeitnerAccent, onAccent = AppColors.OnLeitnerAccent)
    }
}
