package hk.coffee.atlas

import hk.coffee.atlas.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CafeDataTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()
    private val index by lazy { DistrictIndex(asset("districts.json")) }
    @Test fun realSnapshotHasUniqueHongKongRecordsAndAllDistricts() {
        val snapshot = parseSnapshot(asset("cafes.json"), index)
        assertEquals(670, snapshot.cafes.size)
        assertEquals(snapshot.cafes.size, snapshot.cafes.map { it.id }.toSet().size)
        assertEquals(districtGroups.values.flatten().toSet(), snapshot.cafes.map { it.district }.toSet())
        assertTrue(snapshot.cafes.all { it.latitude != null && it.longitude != null })
        assertEquals("2026-10-25T21:21:21Z", snapshot.timestamp)
    }
    @Test fun districtGeometryRecognizesHongKongAndExcludesShenzhen() {
        assertEquals("中西區", index.find(22.2819, 114.1580))
        assertEquals("油尖旺區", index.find(22.2988, 114.1722))
        assertEquals("離島區", index.find(22.2097, 114.0287))
        assertNull(index.find(22.5431, 114.0579))
    }
    @Test fun polygonHolesDoNotCountAsDistrict() {
        val test = DistrictIndex("""[{"name":"test","geometry":{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[3,3],[7,3],[7,7],[3,7],[3,3]]]}}]""")
        assertEquals("test", test.find(1.0, 1.0)); assertNull(test.find(5.0, 5.0))
    }
    @Test fun parserRejectsPartialServerResponses() {
        assertThrows(IllegalArgumentException::class.java) { parseSnapshot("""{"remark":"timeout","elements":[]}""", index) }
        assertThrows(IllegalArgumentException::class.java) { parseSnapshot("""{"elements":[]}""", index) }
    }
    @Test fun parserHandlesMissingDetailsDeduplicationAndForeignCoordinates() {
        val raw = """{"elements":[
          {"type":"node","id":1,"lat":22.2819,"lon":114.158,"tags":{"amenity":"cafe"}},
          {"type":"node","id":1,"lat":22.2819,"lon":114.158,"tags":{"amenity":"cafe"}},
          {"type":"node","id":2,"lat":22.5431,"lon":114.0579,"tags":{"amenity":"cafe"}},
          {"type":"node","id":3,"lat":22.2819,"lon":114.158,"tags":{"amenity":"restaurant"}},
          {"type":"way","id":4,"center":{"lat":22.2819,"lon":114.158},"tags":{"amenity":"cafe","name:zh":"咖啡","name:en":"Coffee","internet_access":"wlan"}}
        ]}"""
        val snapshot = parseSnapshot(raw, index)
        assertEquals(2, snapshot.cafes.size)
        assertEquals("未命名咖啡店", snapshot.cafes[0].name)
        assertEquals("", snapshot.cafes[0].hours)
        assertTrue(snapshot.cafes[1].wifi)
    }
    @Test fun searchCombinesNameDistrictFeaturesAndPersonalFilters() {
        val cafes = listOf(Cafe("1", "咖啡", "FULL Width", "中西區", wifi = true), Cafe("2", "另一杯", "Coffee", "灣仔區"))
        val journal = mapOf("1" to Journal(favorite = true), "2" to Journal(visited = true))
        assertEquals(listOf("1"), filterCafes(cafes, "ｆｕｌｌ 中西", "中西區", true, 1, journal).map { it.id })
        assertTrue(filterCafes(cafes, "", null, true, 2, journal).isEmpty())
        assertEquals(listOf("2"), filterCafes(cafes, "", null, false, 2, journal).map { it.id })
    }
}
