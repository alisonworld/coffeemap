package hk.coffee.atlas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hk.coffee.atlas.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CoffeeState(
    val cafes: List<Cafe> = emptyList(), val custom: List<Cafe> = emptyList(),
    val journal: Map<String, Journal> = emptyMap(), val timestamp: String = "",
    val loading: Boolean = true, val updating: Boolean = false, val message: String? = null
) { val all get() = cafes + custom }
class CoffeeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CafeRepository(app)
    private val mutable = MutableStateFlow(CoffeeState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch {
        runCatching {
            val snapshot = repository.load()
            val personal = withContext(Dispatchers.IO) { repository.journal() to repository.customCafes() }
            mutable.value = CoffeeState(snapshot.cafes, personal.second, personal.first, snapshot.timestamp, loading = false)
        }.onFailure { mutable.value = mutable.value.copy(loading = false, message = "未能載入資料：${it.message}") }
    } }
    fun updateEntry(id: String, change: (Journal) -> Journal) {
        val entries = mutable.value.journal.toMutableMap()
        entries[id] = change(entries[id] ?: Journal())
        repository.saveJournal(entries); mutable.value = mutable.value.copy(journal = entries)
    }
    fun addCafe(name: String, district: String, address: String) {
        val cafe = Cafe("local/${UUID.randomUUID()}", name.trim(), district = district, address = address.trim(), custom = true)
        val list = mutable.value.custom + cafe
        repository.saveCustom(list); mutable.value = mutable.value.copy(custom = list, message = "已加入私人咖啡店紀錄。")
    }
    fun deleteCafe(id: String) {
        val list = mutable.value.custom.filterNot { it.id == id }
        repository.saveCustom(list)
        val journal = mutable.value.journal - id; repository.saveJournal(journal)
        mutable.value = mutable.value.copy(custom = list, journal = journal, message = "已刪除私人店舖。")
    }
    fun refresh() {
        if (mutable.value.updating) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(updating = true)
            runCatching { repository.refresh() }.onSuccess { snapshot ->
                mutable.value = mutable.value.copy(cafes = snapshot.cafes, timestamp = snapshot.timestamp, message = "已更新 ${snapshot.cafes.size} 間咖啡店。")
            }.onFailure { mutable.value = mutable.value.copy(message = it.message ?: "更新失敗，原有資料已保留。") }
            mutable.value = mutable.value.copy(updating = false)
        }
    }
    fun importFile(read: suspend () -> String) {
        if (mutable.value.updating) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(updating = true)
            runCatching { repository.importSnapshot(withContext(Dispatchers.IO) { read() }) }
                .onSuccess { mutable.value = mutable.value.copy(cafes = it.cafes, timestamp = it.timestamp, message = "已匯入 ${it.cafes.size} 間咖啡店。") }
                .onFailure { mutable.value = mutable.value.copy(message = "匯入失敗：${it.message}") }
            mutable.value = mutable.value.copy(updating = false)
        }
    }
    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }
}
