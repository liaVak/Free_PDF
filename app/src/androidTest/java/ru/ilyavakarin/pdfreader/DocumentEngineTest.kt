package ru.ilyavakarin.pdfreader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentEngineTest {
    @Test fun largeDocumentAndMixedPageSizes() = runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation()
        for (name in listOf("large", "mixed", "scan", "fallback", "fallback-cyrillic")) {
            val file=File(inst.targetContext.cacheDir,"test-$name.pdf")
            inst.context.assets.open("pdf/$name.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
            val engine=AndroidPdfEngine.open(inst.targetContext,Uri.fromFile(file))
            try {
                if(name=="large") assertEquals(300,engine.pageCount)
                if(name=="mixed") {
                    assertEquals(3,engine.pageCount)
                    assertEquals(PageSize(600,800),engine.pageSize(0))
                    assertEquals(PageSize(800,500),engine.pageSize(1))
                }
                for(index in setOf(0,engine.pageCount-1)) {
                    val image=engine.render(index,600)
                    assertTrue(image.width in 1..2048)
                    assertTrue(image.height in 1..2048)
                }
                if(name.startsWith("fallback")) {
                    val image=engine.render(0,600)
                    File(inst.targetContext.getExternalFilesDir(null),"$name-android.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
                }
            } finally {engine.close();file.delete()}
        }
    }
    @Test fun corruptedDocumentFailsWithoutCrashingProcess() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"corrupt.pdf").apply { writeText("not a PDF") }
        try {
            val result=runCatching { AndroidPdfEngine.open(context,Uri.fromFile(file)) }
            assertTrue(result.isFailure)
        } finally {file.delete()}
    }
}
