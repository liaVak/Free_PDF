package ru.ilyavakarin.pdfreader

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPdfTest {
    private val inst = InstrumentationRegistry.getInstrumentation()

    private fun view(uri: Uri) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, PdfIntents.MIME_TYPE)
        setPackage(inst.targetContext.packageName)
    }

    private fun sendFromExternalApp(name: String) {
        inst.context.startActivity(Intent().apply {
            component = ComponentName(inst.context.packageName, ExternalPdfSenderActivity::class.java.name)
            putExtra("fixture", name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun resumed(): MainActivity? {
        var activity: MainActivity? = null
        inst.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        }
        return activity
    }

    private fun await(predicate: (MainActivity, ReaderState) -> Boolean): MainActivity {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val activity = resumed()
            var matches = false
            if (activity != null) inst.runOnMainSync { matches = predicate(activity, activity.model.state.value) }
            if (matches) return checkNotNull(activity)
            Thread.sleep(50)
        }
        throw AssertionError("Expected document state not reached")
    }

    private fun closeReader() {
        resumed()?.let { activity -> inst.runOnMainSync { activity.finish() } }
        inst.waitForIdleSync()
    }

    @Test fun registeredForLocalPdfButNotOtherTypesOrWebUrls() {
        val pm = inst.targetContext.packageManager
        for (scheme in listOf("content", "file")) {
            val result = pm.resolveActivity(view(Uri.parse("$scheme:///example.pdf")), PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(MainActivity::class.java.name, result?.activityInfo?.name)
        }
        for ((uri, mime) in listOf("content:///text.txt" to "text/plain", "https://example.org/file.pdf" to PdfIntents.MIME_TYPE)) {
            val intent = view(Uri.parse(uri)).setDataAndType(Uri.parse(uri), mime)
            assertNull(pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY))
            assertNull(PdfIntents.documentUri(intent))
        }
        assertNull(PdfIntents.documentUri(Intent(Intent.ACTION_SEND)))
        assertNull(PdfIntents.documentUri(Intent(Intent.ACTION_VIEW)))
    }

    @Test fun externalContentOpensAndNextIntentReusesReaderAcrossRecreation() {
        closeReader()
        try {
            sendFromExternalApp("embedded")
            val original = await { _, state -> state.document?.name == "embedded.pdf" }
            inst.runOnMainSync { assertEquals(1, original.model.state.value.document?.engine?.pageCount) }
            sendFromExternalApp("mixed")
            val second = await { _, state -> state.document?.name == "mixed.pdf" }
            assertSame(original, second)
            inst.runOnMainSync {
                assertEquals(3, second.model.state.value.document?.engine?.pageCount)
                assertEquals(second.model.state.value.document?.uri, second.intent.data)
                second.recreate()
            }
            val recreated = await { activity, state -> activity !== second && state.document?.name == "mixed.pdf" }
            inst.runOnMainSync { recreated.model.close(); recreated.recreate() }
            await { activity, state -> activity !== recreated && !state.loading && state.document == null }
        } finally {
            closeReader()
            runBlocking {
                val history = HistoryRepository(inst.targetContext)
                for (name in listOf("embedded", "mixed")) {
                    history.remove("content://${inst.context.packageName}.fixtures/fixtures/$name.pdf")
                }
            }
        }
    }

    @Test fun missingExternalDocumentReportsErrorWithoutCrashing() {
        closeReader()
        try {
            sendFromExternalApp("missing")
            await { _, state -> !state.loading && state.document == null && state.error != null }
        } finally { closeReader() }
    }
}
