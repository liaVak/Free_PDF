package ru.ilyavakarin.pdfreader

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class OpenDocument(
    val uri:Uri,
    val name:String,
    val engine:PdfEngine,
    val restoredPage:Int,
    val restoredFraction:Float,
    val restoredSize:PageSize,
)
data class ReaderState(
    val document:OpenDocument?=null,
    val loading:Boolean=false,
    val sharing:Boolean=false,
    val error:String?=null,
)
class ReaderViewModel(application: Application,private val saved:SavedStateHandle):AndroidViewModel(application) {
    private val history=HistoryRepository(application)
    private val access=DocumentAccess(application)
    val recents=history.documents.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _state=MutableStateFlow(ReaderState())
    val state:StateFlow<ReaderState> = _state.asStateFlow()
    private var openJob:Job?=null
    private var shareJob:Job?=null
    private var currentPage=0
    private var currentFraction=0f
    init {
        viewModelScope.launch { runCatching { ShareDocument.cleanup(application) } }
        saved.get<String>("uri")?.let { open(Uri.parse(it),false) }
    }
    fun open(uri:Uri,persistPermission:Boolean=true) {
        persistPosition()
        openJob?.cancel()
        shareJob?.cancel()
        val old=_state.value.document
        _state.value=ReaderState(loading=true)
        saved["uri"]=uri.toString()
        openJob=viewModelScope.launch {
            var candidate:PdfEngine?=null
            try {
                old?.engine?.close()
                if(persistPermission) access.retain(uri)
                val app=getApplication<Application>()
                val name=access.name(uri)
                val before=history.documents.first()
                val previous=before.firstOrNull { it.uri==uri.toString() }
                candidate=AndroidPdfEngine.open(app,uri)
                val (page,fraction)=ReadingRules.position(previous?.page?:0,previous?.fraction?:0f,candidate.pageCount)
                val size=candidate.pageSize(page)
                history.opened(RecentDocument(uri.toString(),name,System.currentTimeMillis(),page,fraction))
                val retained=history.documents.first().map {it.uri}.toSet()
                before.filterNot { it.uri in retained }.forEach { evicted ->
                    access.release(Uri.parse(evicted.uri))
                }
                currentPage=page;currentFraction=fraction
                _state.value=ReaderState(document=OpenDocument(uri,name,candidate,page,fraction,size))
                candidate=null
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                saved.remove<String>("uri")
                _state.value=ReaderState(error=message(e))
            } finally {
                withContext(NonCancellable) {
                    candidate?.close()
                    // Cancellation during close must not leak the previously active renderer.
                    old?.engine?.close()
                }
            }
        }
    }
    fun position(document:OpenDocument,page:Int,fraction:Float) {
        if(_state.value.document !== document)return
        val valid=ReadingRules.position(page,fraction,document.engine.pageCount)
        currentPage=valid.first;currentFraction=valid.second
    }
    fun persistPosition() {
        val document=_state.value.document?:return
        val page=currentPage;val fraction=currentFraction
        viewModelScope.launch {
            try { history.position(document.uri.toString(),page,fraction) }
            catch(e:Exception) { if(e is CancellationException) throw e;showError("Не удалось сохранить место чтения") }
        }
    }
    fun close() {
        persistPosition()
        openJob?.cancel();shareJob?.cancel()
        val doc=_state.value.document
        _state.value=ReaderState()
        saved.remove<String>("uri")
        viewModelScope.launch { doc?.engine?.close() }
    }
    fun remove(uri:String) { viewModelScope.launch {
        try {
            history.remove(uri)
            access.release(Uri.parse(uri))
        } catch(e:Exception) { if(e is CancellationException)throw e;showError("Не удалось обновить историю") }
    } }
    fun share(launch:(Intent)->Unit) {
        val document=_state.value.document?:return
        if(_state.value.sharing)return
        _state.value=_state.value.copy(sharing=true)
        shareJob=viewModelScope.launch {
            try {
                val intent=ShareDocument.prepare(getApplication(),document.uri,document.name)
                launch(Intent.createChooser(intent,"Поделиться PDF"))
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) { showError(message(e)) }
            finally { if(_state.value.document===document) _state.value=_state.value.copy(sharing=false) }
        }
    }
    fun dismissError() { _state.value=_state.value.copy(error=null) }
    fun showError(text:String) { _state.value=_state.value.copy(error=text) }
    override fun onCleared() {
        val doc=_state.value.document
        // viewModelScope has been cancelled; renderer still needs a deterministic close.
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { doc?.engine?.close() }
        super.onCleared()
    }
    private fun message(e:Exception):String = when(e) {
        is SecurityException -> "Нет доступа к файлу или PDF защищён паролем. Выберите другой документ."
        is java.io.FileNotFoundException -> "Файл не найден. Возможно, он перемещён или удалён. Выберите его заново."
        is IOException -> "Не удалось прочитать или подготовить PDF. Проверьте файл и свободное место."
        else -> "Не удалось открыть документ. Возможно, PDF повреждён или имеет неподдерживаемый формат."
    }
}
