package de.max.gedaechtnis.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Wartet (echte Zeit + simulierte Looper-Zeit), bis ein Text sichtbar ist. */
fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 20_000) {
    val start = System.currentTimeMillis()
    while (onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
        if (System.currentTimeMillis() - start > timeoutMs) throw AssertionError("Text „$text“ nicht erschienen. Sichtbar:\n" + onRoot().printToString(maxDepth = 12).take(2500))
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        waitForIdle()
    }
}

fun ComposeTestRule.clickText(text: String, index: Int = 0) {
    onAllNodesWithText(text)[index].performClick()
    waitForIdle()
}

fun ComposeTestRule.hasText(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.clickDescription(description: String) {
    onAllNodesWithContentDescription(description)[0].performClick()
    waitForIdle()
}
