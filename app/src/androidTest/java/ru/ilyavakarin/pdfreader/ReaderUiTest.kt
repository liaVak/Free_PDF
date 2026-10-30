package ru.ilyavakarin.pdfreader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun openJumpZoomAndReopenFromHistory() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val file = File(context.cacheDir, "shared/ui-test-mixed.pdf")
        file.parentFile!!.mkdirs()
        inst.context.assets.open("pdf/mixed.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        try {
            compose.onNodeWithText("Открыть PDF").assertIsDisplayed()
            compose.runOnUiThread { compose.activity.model.open(uri, false) }
            compose.waitUntil(120_000) {
                compose.onAllNodesWithContentDescription("Страница 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("page-counter").performClick()
            compose.onNodeWithTag("page-number").performTextReplacement("3")
            compose.onNodeWithText("Перейти", useUnmergedTree = true).performClick()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Увеличить").performClick()
            compose.onNodeWithText("150%").assertIsDisplayed()
            compose.onNodeWithContentDescription("Уменьшить").performClick()
            compose.onNodeWithText("100%").assertIsDisplayed()
            compose.onNodeWithContentDescription("К документам").performClick()
            compose.onNodeWithText(file.name).performClick()
            compose.waitUntil(120_000) {
                compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(120_000) {
                compose.onAllNodesWithContentDescription("Страница 3").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            compose.runOnUiThread { compose.activity.model.close() }
            runBlocking { HistoryRepository(context).remove(uri.toString()) }
            file.delete()
        }
    }
}
