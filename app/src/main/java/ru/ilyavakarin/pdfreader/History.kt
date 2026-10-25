package ru.ilyavakarin.pdfreader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyStore by preferencesDataStore("history")
data class RecentDocument(
    val uri: String,
    val name: String,
    val openedAt: Long,
    val page: Int = 0,
    val fraction: Float = 0f,
)
object ReadingRules {
    fun pageIndex(input: String, pageCount: Int): Int? = input.trim().toIntOrNull()
        ?.takeIf { it in 1..pageCount }?.minus(1)
    fun recent(items: List<RecentDocument>): List<RecentDocument> = items
        .sortedByDescending { it.openedAt }.distinctBy { it.uri }.take(20)
    fun position(page: Int, fraction: Float, count: Int): Pair<Int,Float> =
        page.coerceIn(0,(count-1).coerceAtLeast(0)) to
            (if (fraction.isFinite()) fraction.coerceIn(0f,0.999f) else 0f)
}
class HistoryRepository(context: Context) {
    private val store=context.applicationContext.historyStore
    private val key=stringPreferencesKey("documents")
    val documents: Flow<List<RecentDocument>> = store.data.map { decode(it[key]) }
    suspend fun opened(document: RecentDocument) = store.edit { prefs ->
        prefs[key]=encode(ReadingRules.recent(listOf(document)+decode(prefs[key])))
    }.let { }
    suspend fun position(uri:String,page:Int,fraction:Float) = store.edit { prefs ->
        prefs[key]=encode(decode(prefs[key]).map {
            if(it.uri==uri) it.copy(page=page.coerceAtLeast(0),fraction=fraction.coerceIn(0f,0.999f)) else it
        })
    }.let { }
    suspend fun remove(uri:String) = store.edit { prefs ->
        prefs[key]=encode(decode(prefs[key]).filterNot { it.uri==uri })
    }.let { }
    private fun encode(items:List<RecentDocument>):String = JSONArray().apply {
        items.forEach { d -> put(JSONObject().apply {
            put("uri",d.uri);put("name",d.name);put("openedAt",d.openedAt);put("page",d.page);put("fraction",d.fraction)
        }) }
    }.toString()
    private fun decode(raw:String?):List<RecentDocument> = runCatching {
        val a=JSONArray(raw?:"[]")
        ReadingRules.recent((0 until a.length()).map { i ->
            val d=a.getJSONObject(i)
            RecentDocument(d.getString("uri"),d.getString("name"),d.getLong("openedAt"),d.optInt("page",0),d.optDouble("fraction",0.0).toFloat())
        })
    }.getOrDefault(emptyList())
}
