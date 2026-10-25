package ru.ilyavakarin.pdfreader

import org.junit.Assert.*
import org.junit.Test

class ReadingRulesTest {
    @Test fun pageNumbersAreOneBasedAndBounded() {
        assertEquals(0,ReadingRules.pageIndex(" 1 ",300))
        assertEquals(299,ReadingRules.pageIndex("300",300))
        for (input in listOf("0","-1","301","abc","9999999999999","")) assertNull(ReadingRules.pageIndex(input,300))
    }
    @Test fun historyRetainsTwentyMostRecentUniqueDocuments() {
        val docs=(0..24).map { RecentDocument("uri:$it","PDF $it",it.toLong()) }
        val recent=ReadingRules.recent(docs+docs[24].copy(openedAt=100))
        assertEquals(20,recent.size)
        assertEquals(100L,recent.first().openedAt)
        assertEquals(20,recent.map { it.uri }.toSet().size)
        assertEquals("uri:5",recent.last().uri)
    }
    @Test fun restoredPositionHandlesShortenedDocumentAndInvalidOffsets() {
        assertEquals(9 to 0f,ReadingRules.position(99,Float.NaN,10))
        assertEquals(0 to 0f,ReadingRules.position(-1,-0.5f,10))
        assertEquals(4 to 0.4f,ReadingRules.position(4,0.4f,10))
        assertEquals(4 to 0.999f,ReadingRules.position(4,8f,10))
    }
}
