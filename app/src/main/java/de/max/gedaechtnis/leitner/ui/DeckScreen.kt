package de.max.gedaechtnis.leitner.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.*
import de.max.gedaechtnis.data.Card
import de.max.gedaechtnis.leitner.engine.Direction
import de.max.gedaechtnis.leitner.engine.LeitnerMode

/** 7.7 Lernkartei – Fächer. */
@Composable
fun DeckScreen(vm: DeckViewModel, onBack: () -> Unit, onStartReview: (Long) -> Unit, onManageCards: (Long) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<DeckDialog?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val bytes = uri?.let { runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() }
        if (uri != null) vm.importCsv(bytes)
    }
    val deck = st.selected
    val accent = AppColors.LeitnerAccent

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(R.drawable.ui_arrow_back, stringResource(R.string.back), onBack, filled = false)
            Text(stringResource(R.string.leitner_title), style = AppText.H20, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (deck != null) AppIconButton(R.drawable.ui_add, stringResource(R.string.card_add), { dialog = DeckDialog.Card(null) }, tint = accent)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.decks, key = { it.id }) { d ->
                    AppChip(d.name, d.id == deck?.id, { vm.select(d.id) }, accent = accent, onAccent = AppColors.OnLeitnerAccent)
                }
                item { AppChip("+ " + stringResource(R.string.deck_new), false, { dialog = DeckDialog.NewDeck }, accent = accent) }
            }
            if (deck == null) {
                Text(stringResource(R.string.no_decks), style = AppText.Body14.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(top = 24.dp))
            } else {
                SectionLabel(stringResource(R.string.label_difficulty), Modifier.padding(top = 8.dp, bottom = 8.dp))
                ModeSegments(deck.mode, vm::setMode)
                Text(
                    stringResource(modeHintRes(deck.mode)), style = AppText.Body12.copy(color = AppColors.TextSecondary),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                BoxesCard(st)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.due_today, st.due), style = AppText.Body13.copy(color = AppColors.TextSecondary), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.csv_import), style = AppText.Medium14.copy(color = accent),
                        modifier = Modifier.heightIn(min = 48.dp).clickable { picker.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream")) }.padding(vertical = 12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    SecondaryButton(stringResource(R.string.cards_manage), { onManageCards(deck.id) })
                    SecondaryButton(stringResource(R.string.deck_rename), { dialog = DeckDialog.Rename })
                    SecondaryButton(stringResource(R.string.deck_delete), { dialog = DeckDialog.Delete }, textColor = AppColors.Error)
                }
            }
        }
        if (deck != null) {
            PrimaryButton(
                stringResource(R.string.start_review, st.due), { onStartReview(deck.id) },
                Modifier.padding(bottom = 12.dp, top = 8.dp), accent = accent, onAccent = AppColors.OnLeitnerAccent, enabled = st.due > 0,
            )
        }
    }

    when (val d = dialog) {
        DeckDialog.NewDeck -> TextDialog(stringResource(R.string.deck_new), "", { vm.createDeck(it); dialog = null }) { dialog = null }
        DeckDialog.Rename -> TextDialog(stringResource(R.string.deck_rename), deck?.name.orEmpty(), { vm.rename(it); dialog = null }) { dialog = null }
        DeckDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.deck_delete_title, deck?.name.orEmpty())) },
            text = { Text(stringResource(R.string.deck_delete_text)) },
            confirmButton = { TextButton({ vm.deleteDeck(); dialog = null }) { Text(stringResource(R.string.deck_delete), color = AppColors.Error) } },
            dismissButton = { TextButton({ dialog = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.Surface,
        )
        is DeckDialog.Card -> CardEditorDialog(d.card, onSave = { f, b, img -> vm.saveCard(d.card, f, b, img); dialog = null }, onDismiss = { dialog = null })
        null -> Unit
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text(stringResource(R.string.csv_import)) },
            text = {
                Text(
                    when (m) {
                        is ImportMessage.Done -> stringResource(R.string.csv_result, m.imported, m.duplicates, m.invalid)
                        ImportMessage.Failed -> stringResource(R.string.csv_failed)
                    },
                )
            },
            confirmButton = { TextButton(vm::clearMessage) { Text(stringResource(R.string.ok)) } },
            containerColor = AppColors.Surface,
        )
    }
}

sealed interface DeckDialog {
    data object NewDeck : DeckDialog
    data object Rename : DeckDialog
    data object Delete : DeckDialog
    data class Card(val card: de.max.gedaechtnis.data.Card?) : DeckDialog
}

private fun modeHintRes(mode: LeitnerMode) = when (mode) {
    LeitnerMode.NORMAL -> R.string.mode_hint_normal
    LeitnerMode.STRICT -> R.string.mode_hint_strict
    LeitnerMode.HARD -> R.string.mode_hint_hard
}

@Composable
private fun ModeSegments(mode: LeitnerMode, onSelect: (LeitnerMode) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(AppColors.SurfaceNav).border(1.dp, AppColors.Outline, shape).padding(3.dp)) {
        LeitnerMode.entries.forEach { m ->
            val sel = m == mode
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) AppColors.Outline else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(when (m) { LeitnerMode.NORMAL -> R.string.diff_normal; LeitnerMode.STRICT -> R.string.diff_strict; LeitnerMode.HARD -> R.string.diff_hard }),
                    style = AppText.Medium14.copy(color = if (sel) AppColors.TextPrimary else AppColors.TextSecondary),
                )
            }
        }
    }
}

/** Säulen je Fach, Höhe proportional zur Kartenanzahl (max. 150 dp). */
@Composable
private fun BoxesCard(st: DeckUiState) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.boxes_title), style = AppText.H16, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.cards_count, st.total), style = AppText.Body12.copy(color = AppColors.TextSecondary))
            }
            val maxCount = (st.boxCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                st.boxCounts.forEachIndexed { i, n ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = AppText.Mono12.copy(color = AppColors.TextSecondary), modifier = Modifier.padding(bottom = 4.dp))
                        Box(
                            Modifier.fillMaxWidth().height((150 * n / maxCount.toFloat()).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(6.dp)).background(if (i == 0) AppColors.LeitnerAccent else AppColors.LeitnerBoxMuted),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.boxCounts.indices.forEach { i ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.box_n, i + 1), style = AppText.Body12.copy(color = AppColors.TextPrimary), textAlign = TextAlign.Center, maxLines = 1)
                        val days = st.intervals.getOrNull(i) ?: 0
                        Text(if (days == 1) stringResource(R.string.interval_daily) else stringResource(R.string.interval_days, days), style = AppText.Body12.copy(color = AppColors.TextSecondary), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.deck_name)) }) },
        confirmButton = { TextButton({ onOk(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = AppColors.Surface,
    )
}

/** Karteneditor: Vorderseite, Rückseite, optionales Bild (über den Systemdialog, ohne Berechtigung). */
@Composable
fun CardEditorDialog(card: Card?, onSave: (String, String, String?) -> Unit, onDismiss: () -> Unit) {
    var front by remember { mutableStateOf(card?.front.orEmpty()) }
    var back by remember { mutableStateOf(card?.back.orEmpty()) }
    var image by remember { mutableStateOf(card?.imageUri) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            image = uri.toString()
        }
    }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.LeitnerAccent, unfocusedBorderColor = AppColors.Outline, cursorColor = AppColors.LeitnerAccent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (card == null) R.string.card_add else R.string.card_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(front, { front = it }, label = { Text(stringResource(R.string.card_front)) }, colors = colors, keyboardOptions = KeyboardOptions.Default)
                OutlinedTextField(back, { back = it }, label = { Text(stringResource(R.string.card_back)) }, colors = colors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(stringResource(if (image == null) R.string.card_image_add else R.string.card_image_change), { picker.launch(arrayOf("image/*")) })
                    if (image != null) SecondaryButton(stringResource(R.string.card_image_remove), { image = null }, textColor = AppColors.Error)
                }
            }
        },
        confirmButton = { TextButton({ onSave(front, back, image) }, enabled = front.isNotBlank() && back.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = AppColors.Surface,
    )
}

/** Kartenliste eines Stapels (bearbeiten, löschen). */
@Composable
fun CardListScreen(vm: DeckViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Card?>(null) }
    val cards = st.cards.filter { it.direction == Direction.FORWARD }
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(R.drawable.ui_arrow_back, stringResource(R.string.back), onBack, filled = false)
            Text(st.selected?.name.orEmpty(), style = AppText.H20, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(14.dp))
                        .clickable { edit = c }.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(c.front, style = AppText.Semi14, maxLines = 2)
                        Text(c.back, style = AppText.Body13.copy(color = AppColors.TextSecondary), maxLines = 2)
                        Text(stringResource(R.string.box_n, c.box), style = AppText.Body12.copy(color = AppColors.LeitnerAccent))
                    }
                    AppIconButton(R.drawable.ui_delete, stringResource(R.string.deck_delete), { vm.deleteCard(c) }, filled = false, tint = AppColors.Error)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    edit?.let { c -> CardEditorDialog(c, onSave = { f, b, img -> vm.saveCard(c, f, b, img); edit = null }, onDismiss = { edit = null }) }
}
