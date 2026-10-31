package de.max.gedaechtnis.core

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.max.gedaechtnis.kim.engine.Cell
import de.max.gedaechtnis.kim.engine.GameObject

val ScreenMargin = 20.dp
val GridGap = 8.dp
val CardShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(14.dp)
val CellShape = RoundedCornerShape(14.dp)

fun Modifier.outlined(shape: androidx.compose.ui.graphics.Shape = CardShape, color: Color = AppColors.Outline, width: Dp = 1.dp) =
    border(width, color, shape)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = AppColors.TextSecondary) {
    Text(text.uppercase(), style = AppText.SectionLabel.copy(color = color), modifier = modifier)
}

/** Karte: Surface, 1 dp Outline, große Rundung. */
@Composable
fun AppCard(modifier: Modifier = Modifier, borderColor: Color = AppColors.Outline, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(CardShape)
            .background(AppColors.Surface)
            .border(1.dp, borderColor, CardShape),
    ) { content() }
}

/** Hauptbutton: 52 dp hoch, volle Breite, Akzentfarbe, Text 16 sp SemiBold. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.KimAccent,
    onAccent: Color = AppColors.OnKimAccent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.Semi16.copy(color = onAccent), textAlign = TextAlign.Center) }
}

fun leitnerButtonColors() = AppColors.LeitnerAccent to AppColors.OnLeitnerAccent

/** Nebenbutton: 48–52 dp, transparent mit 1 dp Outline. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = AppColors.TextPrimary,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(ButtonShape)
            .border(1.dp, AppColors.Outline, ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.Medium14.copy(color = textColor), textAlign = TextAlign.Center) }
}

/** Icon-Button: 44 × 44 dp, Radius 12 (Touch-Fläche 48 dp). */
@Composable
fun AppIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    tint: Color = AppColors.TextPrimary,
) {
    Box(
        modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (filled) Modifier.background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}

/** Chip, voll gerundet (20 dp); Auswahl = Akzentfarbe gefüllt. Touch-Fläche mindestens 48 dp. */
@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.KimAccent,
    onAccent: Color = AppColors.OnKimAccent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) accent else AppColors.Surface)
                .border(1.dp, if (selected) accent else AppColors.Outline, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(text, style = AppText.Medium14.copy(color = if (selected) onAccent else AppColors.TextPrimary))
        }
    }
}

/** Info-Chip ohne Interaktion (z. B. „Fehlklick −15“). */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = AppText.Body12.copy(color = AppColors.TextSecondary)) }
}

/** Fortschrittsbalken, Spur in Outline-Farbe. */
@Composable
fun LinearBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(AppColors.Outline, size = size, cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
    }
}

/** Countdown-Text (Mono) für verbleibende Millisekunden: m:ss. */
fun formatCountdown(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

enum class CellMark { NONE, SELECTED, FOUND, FALSE_CLICK, MISSED, REFERENCE }

/** Katalog-Icon, eingefärbt mit der Objektfarbe und gegebenenfalls gedreht. */
@Composable
fun ObjectIcon(obj: GameObject, rotation: Int, size: Dp, modifier: Modifier = Modifier) {
    val res = IconRegistry.icons[obj.iconRes] ?: return
    Icon(
        painter = painterResource(res),
        contentDescription = obj.label,
        tint = AppColors.obj(obj.color),
        modifier = modifier.size(size).rotate(rotation.toFloat()),
    )
}

/**
 * Rasterfeld mit Objekt. Zustände unterscheiden sich immer auch über Rahmenart oder Symbol, nie nur über die Farbe:
 * markiert = 2 dp Akzentrahmen + Häkchen, erkannt = durchgezogen grün, Fehlklick = gestrichelt rot,
 * übersehen = gepunktet amber.
 */
@Composable
fun GridCell(
    cell: Cell?,
    modifier: Modifier = Modifier,
    mark: CellMark = CellMark.NONE,
    iconSize: Dp = 40.dp,
    corner: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(corner)
    val accent = AppColors.KimAccent
    val borderColor = when (mark) {
        CellMark.SELECTED -> accent
        CellMark.FOUND -> AppColors.Success
        CellMark.FALSE_CLICK -> AppColors.Error
        CellMark.MISSED -> accent
        CellMark.REFERENCE -> AppColors.TextSecondary
        CellMark.NONE -> AppColors.Outline
    }
    val dash = when (mark) {
        CellMark.FALSE_CLICK -> floatArrayOf(10f, 8f)
        CellMark.MISSED -> floatArrayOf(2f, 6f)
        else -> null
    }
    val base = modifier
        .clip(shape)
        .background(if (mark == CellMark.SELECTED) accent.copy(alpha = 0.10f) else AppColors.Surface)
        .then(if (onClick != null) Modifier.clickable(role = Role.Checkbox, onClick = onClick) else Modifier)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    Box(base, contentAlignment = Alignment.Center) {
        if (cell != null) ObjectIcon(cell.obj, cell.rotation, iconSize)
        Canvas(Modifier.matchParentSize()) {
            val w = if (mark == CellMark.NONE) 1.dp.toPx() else 2.dp.toPx()
            val inset = w / 2
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
                style = Stroke(w, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }),
            )
        }
        if (mark == CellMark.SELECTED) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) { Text("✓", style = AppText.Body12.copy(color = AppColors.OnKimAccent)) }
        }
        badge?.invoke()
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        SectionLabel(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}
