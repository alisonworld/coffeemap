package hk.coffee.atlas.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class Cafe(
    val id: String, val name: String, val englishName: String = "", val district: String,
    val address: String = "", val latitude: Double? = null, val longitude: Double? = null,
    val hours: String = "", val phone: String = "", val website: String = "",
    val wifi: Boolean = false, val outdoor: Boolean = false, val custom: Boolean = false
)
data class Journal(val favorite: Boolean = false, val visited: Boolean = false, val note: String = "")
data class Snapshot(val cafes: List<Cafe>, val timestamp: String)

val districtGroups = linkedMapOf(
    "香港島" to listOf("中西區", "灣仔區", "東區", "南區"),
    "九龍" to listOf("油尖旺區", "深水埗區", "九龍城區", "黃大仙區", "觀塘區"),
    "新界" to listOf("葵青區", "荃灣區", "屯門區", "元朗區", "北區", "大埔區", "沙田區", "西貢區"),
    "離島" to listOf("離島區")
)
fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
fun filterCafes(cafes: List<Cafe>, query: String, district: String?, wifiOnly: Boolean, mode: Int, journal: Map<String, Journal>): List<Cafe> {
    val words = normalized(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return cafes.filter { cafe ->
        val entry = journal[cafe.id] ?: Journal()
        val haystack = normalized("${cafe.name} ${cafe.englishName} ${cafe.district} ${cafe.address}")
        (district == null || cafe.district == district) && (!wifiOnly || cafe.wifi) &&
            (mode != 1 || entry.favorite) && (mode != 2 || entry.visited) && words.all(haystack::contains)
    }.sortedWith(compareBy({ it.district }, { normalized(it.name) }))
}

/** OSM GeoJSON district polygons; ring[0] is exterior, remaining rings are holes. */
class DistrictIndex(json: String) {
    private data class District(val name: String, val polygons: List<List<List<Pair<Double, Double>>>>)
    private val districts: List<District>
    init {
        val array = JSONArray(json)
        districts = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val geometry = item.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            val multi = if (geometry.getString("type") == "Polygon") JSONArray().put(coordinates) else coordinates
            District(item.getString("name"), (0 until multi.length()).map { p ->
                val polygon = multi.getJSONArray(p)
                (0 until polygon.length()).map { r ->
                    val ring = polygon.getJSONArray(r)
                    (0 until ring.length()).map { k -> ring.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } }
                }
            })
        }
    }
    fun find(latitude: Double, longitude: Double): String? = districts.firstOrNull { d ->
        d.polygons.any { p -> p.isNotEmpty() && inside(longitude, latitude, p[0]) && p.drop(1).none { inside(longitude, latitude, it) } }
    }?.name
    private fun inside(x: Double, y: Double, ring: List<Pair<Double, Double>>): Boolean {
        var result = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val (xi, yi) = ring[i]; val (xj, yj) = ring[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) result = !result
            j = i
        }
        return result
    }
}

fun parseSnapshot(json: String, index: DistrictIndex): Snapshot {
    val root = JSONObject(json)
    require(!root.has("remark")) { "資料服務未能完成查詢，請稍後再試。" }
    val elements = root.getJSONArray("elements")
    require(elements.length() <= 50_000) { "檔案超出店舖數量上限。" }
    val cafes = (0 until elements.length()).mapNotNull { i ->
        val e = elements.getJSONObject(i)
        val tags = e.optJSONObject("tags") ?: return@mapNotNull null
        if (tags.optString("amenity") != "cafe") return@mapNotNull null
        val point = e.optJSONObject("center") ?: e
        val lat = point.optDouble("lat", Double.NaN); val lon = point.optDouble("lon", Double.NaN)
        if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
        val district = index.find(lat, lon) ?: return@mapNotNull null
        val type = e.optString("type"); val id = e.optLong("id", -1)
        if (type !in setOf("node", "way", "relation") || id < 0) return@mapNotNull null
        fun tag(vararg keys: String) = keys.firstNotNullOfOrNull { key -> tags.optString(key).trim().takeIf { it.isNotBlank() && it != "null" } }.orEmpty()
        val name = tag("name:zh", "name:zh-Hant", "name", "brand") .ifBlank { "未命名咖啡店" }
        val address = tag("addr:full", "addr:full:zh").ifBlank {
            listOf(tag("addr:street:zh", "addr:street"), tag("addr:housenumber"), tag("addr:building"), tag("addr:floor"), tag("addr:unit")).filter(String::isNotBlank).joinToString(" ")
        }
        Cafe("$type/$id", name, tag("name:en", "name"), district, address, lat, lon,
            tag("opening_hours"), tag("contact:phone", "phone"), tag("contact:website", "website"),
            tag("internet_access") in setOf("wlan", "yes") || tag("wifi") in setOf("yes", "free"), tag("outdoor_seating") == "yes")
    }.distinctBy(Cafe::id)
    require(cafes.isNotEmpty()) { "檔案內冇有效嘅香港咖啡店；原有資料已保留。" }
    return Snapshot(cafes, root.optJSONObject("osm3s")?.optString("timestamp_osm_base").orEmpty().ifBlank { "日期未提供" })
}

fun Cafe.toJson() = JSONObject().put("id", id).put("name", name).put("district", district).put("address", address)
    .put("latitude", latitude ?: JSONObject.NULL).put("longitude", longitude ?: JSONObject.NULL)
fun customCafeFromJson(j: JSONObject): Cafe {
    val district = j.getString("district")
    require(district in districtGroups.values.flatten())
    val name = j.getString("name").trim(); require(name.isNotEmpty())
    return Cafe(j.getString("id"), name, district = district, address = j.optString("address"),
        latitude = if (j.isNull("latitude")) null else j.getDouble("latitude"),
        longitude = if (j.isNull("longitude")) null else j.getDouble("longitude"), custom = true)
}
