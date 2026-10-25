package ru.ilyavakarin.pdfreader

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareDocumentTest {
    @Test fun sharedCopyIsIdenticalAndGrantsReadOnlyAccess() = runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val file=File(context.cacheDir,"share-original.pdf")
        inst.context.assets.open("pdf/embedded.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            val intent=ShareDocument.prepare(context,Uri.fromFile(file),"../Книга.pdf")
            assertEquals(Intent.ACTION_SEND,intent.action)
            assertEquals("application/pdf",intent.type)
            val uri=androidx.core.content.IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)!!
            assertEquals("content",uri.scheme)
            assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(file.readBytes(),it.readBytes()) }
        } finally {file.delete()}
    }
}
