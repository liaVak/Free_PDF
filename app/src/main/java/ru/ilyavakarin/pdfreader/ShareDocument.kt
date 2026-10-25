package ru.ilyavakarin.pdfreader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ShareDocument {
    private const val MAX_AGE = 24*60*60*1000L
    fun sendIntent(context: Context, uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM,uri)
        clipData = ClipData.newRawUri("PDF",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    suspend fun prepare(context:Context, source:Uri, title:String):Intent = withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"shared/${UUID.randomUUID()}").apply { mkdirs() }
        val safeName=title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").take(100)
            .ifBlank { "Документ.pdf" }.let { if(it.endsWith(".pdf",true)) it else "$it.pdf" }
        val file=File(directory,safeName)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: error("Не удалось прочитать PDF")
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            sendIntent(context,uri)
        } catch(e:Exception) { directory.deleteRecursively();throw e }
    }
    suspend fun cleanup(context:Context) = withContext(Dispatchers.IO) {
        val cutoff=System.currentTimeMillis()-MAX_AGE
        File(context.cacheDir,"shared").listFiles()?.filter { it.lastModified()<cutoff }
            ?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()?.filter { it.name.startsWith("render-") && it.lastModified()<cutoff }
            ?.forEach { it.delete() }
        Unit
    }
}
