package ru.ilyavakarin.pdfreader

import android.content.Intent
import android.net.Uri

/** Only local document URIs are supported; network URLs are never fetched. */
object PdfIntents {
    const val MIME_TYPE = "application/pdf"

    fun documentUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme !in setOf("content", "file")) return null
        // A content provider may supply the MIME type to Android without setting Intent.type.
        if (intent.type != null && intent.type != MIME_TYPE) return null
        return uri
    }
}
