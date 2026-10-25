package ru.ilyavakarin.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** All indices are zero based; region coordinates are in PDF points. */
interface PdfEngine {
    val pageCount: Int
    suspend fun pageSize(index: Int): PageSize
    suspend fun render(index: Int, width: Int, region: PageRegion? = null): Bitmap
    suspend fun close()
}
data class PageSize(val width: Int, val height: Int)
data class PageRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

class AndroidPdfEngine private constructor(
    private val renderer: PdfRenderer,
    private val temporaryFile: File?,
) : PdfEngine {
    private val mutex = Mutex()
    private var closed = false
    override val pageCount = renderer.pageCount
    private val sizes = mutableMapOf<Int, PageSize>()
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    override suspend fun pageSize(index: Int): PageSize = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            sizes.getOrPut(index) { renderer.openPage(index).use { PageSize(it.width, it.height) } }
        }
    }
    override suspend fun render(index: Int, width: Int, region: PageRegion?): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            val key = "$index:$width:$region"
            cache.get(key) ?: renderer.openPage(index).use { page ->
                val r = region ?: PageRegion(0f, 0f, page.width.toFloat(), page.height.toFloat())
                require(r.right > r.left && r.bottom > r.top)
                // Bound both the longest edge and total allocation, including unusually tall pages.
                val ratio = (r.bottom-r.top)/(r.right-r.left)
                val w = width.coerceIn(1, 2048).coerceAtMost((2048f/ratio).toInt().coerceAtLeast(1))
                val h = (w*ratio).toInt().coerceIn(1, 2048)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setScale(w/(r.right-r.left), h/(r.bottom-r.top))
                    postTranslate(-r.left*w/(r.right-r.left), -r.top*h/(r.bottom-r.top))
                }
                page.render(bitmap, Rect(0,0,w,h), matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bitmap)
                bitmap
            }
        }
    }
    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!closed) {
                closed = true
                cache.evictAll()
                renderer.close()
                temporaryFile?.delete()
            }
        }
    }
    companion object {
        suspend fun open(context: Context, uri: Uri): AndroidPdfEngine = withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            var temporary: File? = null
            try {
                descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Не удалось открыть документ")
                val seekable = runCatching { Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET) }.isSuccess
                if (!seekable) {
                    descriptor.close()
                    descriptor = null
                    temporary = File.createTempFile("render-", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { input.copyTo(it) }
                    } ?: error("Не удалось прочитать документ")
                    descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                val renderer = PdfRenderer(descriptor)
                descriptor = null // ownership transferred to PdfRenderer
                if (renderer.pageCount == 0) {
                    renderer.close()
                    error("В документе нет страниц")
                }
                AndroidPdfEngine(renderer, temporary)
            } catch (e: Exception) {
                descriptor?.close()
                temporary?.delete()
                throw e
            }
        }
    }
}
