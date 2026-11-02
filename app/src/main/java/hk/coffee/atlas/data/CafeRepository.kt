package hk.coffee.atlas.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CafeRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("coffee_journal", Context.MODE_PRIVATE)
    private val cache = AtomicFile(File(context.filesDir, "cafes.json"))
    val index by lazy { DistrictIndex(context.assets.open("districts.json").bufferedReader().use { it.readText() }) }
    suspend fun load(): Snapshot = withContext(Dispatchers.IO) {
        runCatching { parseSnapshot(cache.openRead().bufferedReader().use { it.readText() }, index) }
            .getOrElse { parseSnapshot(context.assets.open("cafes.json").bufferedReader().use { it.readText() }, index) }
    }
    fun journal(): Map<String, Journal> {
        val j = runCatching { JSONObject(preferences.getString("journal", "{}")!!) }.getOrDefault(JSONObject())
        return j.keys().asSequence().associateWith { id -> j.getJSONObject(id).let { Journal(it.optBoolean("favorite"), it.optBoolean("visited"), it.optString("note")) } }
    }
    fun saveJournal(entries: Map<String, Journal>) {
        val j = JSONObject()
        entries.forEach { (id, e) -> j.put(id, JSONObject().put("favorite", e.favorite).put("visited", e.visited).put("note", e.note)) }
        preferences.edit().putString("journal", j.toString()).apply()
    }
    fun customCafes(): List<Cafe> = runCatching {
        val j = JSONArray(preferences.getString("custom", "[]"))
        (0 until j.length()).map { customCafeFromJson(j.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun saveCustom(cafes: List<Cafe>) { preferences.edit().putString("custom", JSONArray().apply { cafes.forEach { put(it.toJson()) } }.toString()).apply() }
    suspend fun importSnapshot(raw: String): Snapshot = withContext(Dispatchers.IO) {
        require(raw.length <= 12_000_000) { "檔案太大，上限為 12 MB。" }
        val result = parseSnapshot(raw, index)
        val stream = cache.startWrite()
        try { stream.write(raw.toByteArray(Charsets.UTF_8)); cache.finishWrite(stream) }
        catch (e: Exception) { cache.failWrite(stream); throw e }
        result
    }
    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val last = preferences.getLong("lastAttempt", 0)
        require(System.currentTimeMillis() - last >= 60_000) { "請等一分鐘再更新，減輕公共資料服務負擔。" }
        preferences.edit().putLong("lastAttempt", System.currentTimeMillis()).apply()
        val query = "[out:json][timeout:90];area[\"ISO3166-1\"=\"HK\"][\"boundary\"=\"administrative\"]->.hk;nwr[\"amenity\"=\"cafe\"](area.hk);out center tags;"
        val url = URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000; connection.readTimeout = 110_000
            connection.setRequestProperty("User-Agent", "HKCoffeeAtlas/1.0 Android")
            require(connection.responseCode == 200) { "資料服務暫時繁忙（${connection.responseCode}），離線資料仍然可用。" }
            val raw = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer); if (n < 0) break
                    require(out.size() + n <= 12_000_000) { "資料超出大小上限。" }
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
            importSnapshot(raw)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("未能連接資料服務。請檢查網絡或稍後再試；離線資料已保留。", e)
        } finally { connection.disconnect() }
    }
}
