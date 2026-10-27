package ru.ilyavakarin.pdfreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    @Test fun savedPositionSurvivesRepositoryRecreationAndRemoval() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val history=HistoryRepository(context)
        val uri="content://pdf-reader-test/history-document"
        try {
            history.opened(RecentDocument(uri,"Тест.pdf",100))
            history.position(uri,12,0.42f)
            val restored=HistoryRepository(context).documents.first().first { it.uri==uri }
            assertEquals(12,restored.page)
            assertEquals(0.42f,restored.fraction,0.001f)
            history.remove(uri)
            assertFalse(history.documents.first().any {it.uri==uri})
        } finally { history.remove(uri) }
    }
}
