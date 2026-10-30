package ru.ilyavakarin.pdfreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Provider metadata and grants live here, independently of reader screen state. */
class DocumentAccess(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun name(uri: Uri): String = withContext(Dispatchers.IO) {
        val fallback = if (uri.scheme == "file") File(uri.path.orEmpty()).name else uri.lastPathSegment
        val displayName = if (uri.scheme == "content") {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
            } catch (_: IllegalArgumentException) {
                null // Some providers do not implement display-name metadata.
            } catch (_: UnsupportedOperationException) {
                null
            }
        } else null
        displayName?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() } ?: "Документ.pdf"
    }

    fun retain(uri: Uri) {
        if (uri.scheme != "content") return
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // ACTION_VIEW commonly grants access only while the receiving task exists.
        } catch (_: UnsupportedOperationException) {
            // Temporary access can still be used to read the current document.
        }
    }

    fun release(uri: Uri) {
        if (uri.scheme != "content") return
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // This URI may never have had a persistent grant.
        }
    }
}
