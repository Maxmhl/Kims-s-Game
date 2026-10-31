package de.max.gedaechtnis.kim.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.max.gedaechtnis.R
import de.max.gedaechtnis.core.GridGap
import de.max.gedaechtnis.kim.engine.ChangeType
import de.max.gedaechtnis.kim.engine.Mode
import kotlin.math.min

@StringRes
fun Mode.nameRes(): Int = when (this) {
    Mode.MISSING -> R.string.mode_missing
    Mode.CHANGED -> R.string.mode_changed
    Mode.RECONSTRUCTION -> R.string.mode_reconstruction
    Mode.FREE_RECALL -> R.string.mode_free_recall
    Mode.SEQUENCE -> R.string.mode_sequence
}

@StringRes
fun levelNameRes(level: Int): Int = when (level) {
    1 -> R.string.level_name_1
    2 -> R.string.level_name_2
    3 -> R.string.level_name_3
    4 -> R.string.level_name_4
    5 -> R.string.level_name_5
    else -> R.string.level_name_6
}

@StringRes
fun ChangeType.verbRes(): Int = when (this) {
    ChangeType.REMOVED -> R.string.change_removed
    ChangeType.REPLACED -> R.string.change_replaced
    ChangeType.SWAPPED -> R.string.change_swapped
    ChangeType.RECOLORED -> R.string.change_recolored
    ChangeType.ROTATED -> R.string.change_rotated
    ChangeType.FAKE -> R.string.change_fake
}

/**
 * Raster mit fester Spaltenzahl. Feldbreite ergibt sich aus der verfügbaren Breite;
 * die Höhe ist höchstens 92 dp (Spezifikation: 4 Spalten, 92 dp bei 20 Objekten).
 */
@Composable
fun KimGrid(
    count: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    maxCellHeight: Dp = 92.dp,
    cell: @Composable (index: Int, cellWidth: Dp, cellHeight: Dp, iconSize: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = (maxWidth - GridGap * (columns - 1)) / columns
        val h = min(maxCellHeight.value, w.value * 1.13f).dp
        val icon = min(40f, w.value * 0.62f).dp
        val rows = (count + columns - 1) / columns
        Column(verticalArrangement = Arrangement.spacedBy(GridGap)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    for (c in 0 until columns) {
                        val i = r * columns + c
                        if (i < count) cell(i, w, h, icon) else Spacer(Modifier.size(w, h))
                    }
                }
            }
        }
    }
}
