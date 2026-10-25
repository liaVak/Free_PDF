package ru.ilyavakarin.pdfreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Gate: execute on Android 12 before integrating the reader UI. */
@RunWith(AndroidJUnit4::class)
class FontRenderingTest {
    @Test fun embeddedFontsMatchReference() = runBlocking {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        for (name in listOf("embedded", "full-truetype", "scripts-cff")) {
            val file = File(context.cacheDir,"font-$name.pdf")
            inst.context.assets.open("pdf/$name.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
            val engine = AndroidPdfEngine.open(context,Uri.fromFile(file))
            try {
                assertEquals(1,engine.pageCount)
                val reference = inst.context.assets.open("reference/$name.png").use { BitmapFactory.decodeStream(it) }
                val actual = engine.render(0,reference.width)
                assertEquals(reference.height,actual.height)
                // Symmetric one-pixel ink tolerance permits antialiasing differences across engines.
                val recall = inkRecall(reference,actual)
                val precision = inkRecall(actual,reference)
                assertTrue("$name missing/moved ink: $recall",recall > 0.95)
                assertTrue("$name extra/moved ink: $precision",precision > 0.95)
                val output=File(context.getExternalFilesDir(null),"$name-android.png")
                output.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG,100,it) }
                val zoom = engine.render(0,1200)
                assertTrue(zoom.width > actual.width)
            } finally { engine.close();file.delete() }
        }
    }
    private fun inkRecall(a: Bitmap,b: Bitmap): Double {
        fun dark(v:Int) = (android.graphics.Color.red(v)+android.graphics.Color.green(v)+android.graphics.Color.blue(v)) < 600
        var ink=0;var matched=0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (!dark(a.getPixel(x,y))) continue
            ink++
            var found=false
            for (dy in -1..1) for (dx in -1..1) {
                val xx=x+dx;val yy=y+dy
                if (xx in 0 until b.width && yy in 0 until b.height && dark(b.getPixel(xx,yy))) found=true
            }
            if(found)matched++
        }
        return if(ink==0) 0.0 else matched.toDouble()/ink
    }
}
