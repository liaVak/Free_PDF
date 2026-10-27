package ru.ilyavakarin.pdfreader

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
    override fun onStop() {
        model.persistPosition()
        super.onStop()
    }
}
