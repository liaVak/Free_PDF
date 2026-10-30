package ru.ilyavakarin.pdfreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    val model: ReaderViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewModel/SavedStateHandle restores the current document after recreation.
        if (savedInstanceState == null) openIncomingDocument(intent)
        enableEdgeToEdge()
        setContent {
            val dark=isSystemInDarkTheme()
            MaterialTheme(colorScheme=if(dark) darkColorScheme(
                primary=Color(0xFF8BD6C5),secondary=Color(0xFFB1CCC4),background=Color(0xFF101815),surface=Color(0xFF101815),
            ) else lightColorScheme(
                primary=Color(0xFF216B5D),secondary=Color(0xFF526B62),background=Color(0xFFF7F9F5),surface=Color(0xFFF7F9F5),
            )) { PdfReaderApp(model) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIncomingDocument(intent)
    }

    private fun openIncomingDocument(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = PdfIntents.documentUri(intent)
        if (uri == null) {
            model.showError("Не удалось открыть файл. Выберите PDF на устройстве.")
        } else {
            model.open(uri, persistPermission = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        }
    }

    override fun onStop() {
        model.persistPosition()
        super.onStop()
    }
}
