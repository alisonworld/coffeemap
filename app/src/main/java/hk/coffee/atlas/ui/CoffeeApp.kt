@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package hk.coffee.atlas.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hk.coffee.atlas.*
import hk.coffee.atlas.data.*
import java.util.Locale

@Composable fun CoffeeApp(vm: CoffeeViewModel = viewModel()) = CoffeeTheme {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf<String?>(null) }
    var wifi by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var districtPicker by remember { mutableStateOf(false) }
    var addCafe by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer); if (n < 0) break
                    require(out.size() + n <= 12_000_000) { "檔案上限為 12 MB。" }; out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            } ?: error("未能讀取檔案。")
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    val selected = state.all.firstOrNull { it.id == selectedId }
    BackHandler(selectedId != null) { selectedId = null }
    Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (selected == null) NavigationBar(containerColor = Paper, tonalElevation = 0.dp, modifier = Modifier.border(width = 1.dp, color = Line)) {
            val tabs = listOf("探索" to Icons.Outlined.Explore, "收藏" to Icons.Outlined.BookmarkBorder, "足跡" to Icons.Outlined.CheckCircleOutline, "資料" to Icons.Outlined.Tune)
            tabs.forEachIndexed { index, (label, icon) -> NavigationBarItem(selected = tab == index, onClick = { tab = index },
                icon = { Icon(icon, label) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFDDE8DD))) }
        }
    }) { padding ->
        if (selected != null) {
            DetailScreen(selected, state.journal[selected.id] ?: Journal(), { selectedId = null },
                { vm.updateEntry(selected.id) { e -> e.copy(favorite = !e.favorite) } },
                { vm.updateEntry(selected.id) { e -> e.copy(visited = !e.visited) } },
                { note -> vm.updateEntry(selected.id) { e -> e.copy(note = note) } },
                { vm.deleteCafe(selected.id); selectedId = null }, Modifier.padding(padding))
        } else Column(Modifier.padding(padding).fillMaxSize()) {
            BrandHeader { addCafe = true }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                tab == 3 -> DataScreen(state, vm::refresh, { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f))
                else -> {
                    if (tab == 0) Hero(state.all.size) else CollectionTitle(tab, state.journal.values.count { if (tab == 1) it.favorite else it.visited })
                    OutlinedTextField(query, { query = it }, placeholder = { Text("搜尋店名、地址、地區") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除搜尋") }
                        }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = district != null, onClick = { districtPicker = true }, label = { Text(district ?: "全港 18 區") }, trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp)) })
                        FilterChip(selected = wifi, onClick = { wifi = !wifi }, label = { Text("有 Wi-Fi") }, leadingIcon = { Icon(Icons.Outlined.Wifi, null, Modifier.size(17.dp)) })
                        if (district != null || wifi || query.isNotEmpty()) TextButton(onClick = { district = null; wifi = false; query = "" }) { Text("重設") }
                    }
                    val list = remember(state.all, state.journal, query, district, wifi, tab) { filterCafes(state.all, query, district, wifi, tab, state.journal) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (tab == 0) "慢慢探索" else if (tab == 1) "想去嘅清單" else "飲過嘅好咖啡", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${list.size} 間 · 按地區排序", fontSize = 12.sp, color = Muted)
                    }
                    if (list.isEmpty()) EmptyState(tab, query.isNotEmpty() || district != null || wifi, { district = null; wifi = false; query = "" }, Modifier.weight(1f))
                    else LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(list, key = Cafe::id) { cafe -> CafeCard(cafe, state.journal[cafe.id] ?: Journal(), { selectedId = cafe.id }, { vm.updateEntry(cafe.id) { e -> e.copy(favorite = !e.favorite) } }) }
                    }
                    Text("店舖資料 © OpenStreetMap contributors · ODbL", fontSize = 10.sp, color = Muted,
                        modifier = Modifier.fillMaxWidth().clickable { openLink(context, "https://www.openstreetmap.org/copyright") }.padding(horizontal = 24.dp, vertical = 5.dp))
                }
            }
        }
    }
    if (districtPicker) ModalBottomSheet(onDismissRequest = { districtPicker = false }, containerColor = Paper) {
        Text("今日想去邊區？", fontWeight = FontWeight.Bold, fontSize = 23.sp, modifier = Modifier.padding(24.dp, 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { DistrictRow("全港 18 區", state.all.size, district == null) { district = null; districtPicker = false } }
            districtGroups.forEach { (group, names) ->
                item { Text(group, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(24.dp, 14.dp, 24.dp, 4.dp)) }
                items(names) { name -> DistrictRow(name, state.all.count { it.district == name }, district == name) { district = name; districtPicker = false } }
            }
        }
    }
    if (addCafe) AddCafeDialog({ addCafe = false }) { name, area, address -> vm.addCafe(name, area, address); addCafe = false }
}


@Composable private fun BrandHeader(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocalCafe, null, tint = Peach, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text("港啡", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp); Text("HONG KONG COFFEE ATLAS", fontSize = 8.sp, letterSpacing = 1.5.sp, color = Muted) }
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "新增私人咖啡店") }
    }
}
@Composable private fun Hero(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(24.dp)).background(Ink).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("一區一杯，慢慢發現。", color = Peach, fontSize = 12.sp, letterSpacing = 1.sp)
            Text("香港，\n慢慢飲。", color = Paper, fontWeight = FontWeight.Bold, fontSize = 33.sp, lineHeight = 42.sp, modifier = Modifier.padding(vertical = 10.dp))
            Text("$count 間咖啡店，等你探索", color = Color(0xFFD5DED5), fontSize = 12.sp)
        }
        Box(Modifier.size(108.dp).clip(CircleShape).background(Color(0xFF2B5143)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(Peach), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocalCafe, null, tint = Ink, modifier = Modifier.size(48.dp)) }
        }
    }
    Text("收錄開放資料中的咖啡店，未涵蓋全港所有店舖。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(26.dp, 8.dp, 24.dp, 0.dp))
}
@Composable private fun CollectionTitle(tab: Int, count: Int) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(if (tab == 1) "留低，下一杯。" else "每杯都有故事。", fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Text(if (tab == 1) "$count 間已收藏 · 你嘅咖啡願望清單" else "$count 間已到訪 · 記低你嘅咖啡足跡", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable private fun CafeCard(cafe: Cafe, entry: Journal, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                val colors = listOf(Color(0xFFEDE1D0), Color(0xFFDEE7DE), Color(0xFFE7E1D9), Color(0xFFF0E3DB))
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors[(cafe.id.hashCode() and Int.MAX_VALUE) % colors.size]), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.LocalCafe, null, tint = Ink, modifier = Modifier.size(29.dp))
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(cafe.district + if (cafe.custom) " · 私人新增" else "", color = Muted, fontSize = 11.sp)
                    Text(cafe.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    if (cafe.englishName.isNotBlank() && cafe.englishName != cafe.name) Text(cafe.englishName, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconToggleButton(checked = entry.favorite, onCheckedChange = { onFavorite() }, modifier = Modifier.size(48.dp)) {
                    Icon(if (entry.favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (entry.favorite) "取消收藏 ${cafe.name}" else "收藏 ${cafe.name}", tint = Ink)
                }
            }
            Text(cafe.address.ifBlank { "地址未提供 · 可於詳情開啟地圖" }, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            if (cafe.wifi || cafe.outdoor || entry.visited) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cafe.wifi) SmallTag("Wi-Fi", Icons.Outlined.Wifi)
                if (cafe.outdoor) SmallTag("戶外座位", Icons.Outlined.Deck)
                if (entry.visited) SmallTag("已到訪", Icons.Outlined.CheckCircleOutline)
            }
        }
    }
}
@Composable private fun SmallTag(text: String, icon: ImageVector) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Paper).padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 10.sp)
    }
}
@Composable private fun DistrictRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text("$count", color = Muted, fontSize = 13.sp)
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.padding(start = 10.dp).size(18.dp))
    }
}
@Composable private fun EmptyState(tab: Int, filtered: Boolean, reset: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (tab == 1) Icons.Outlined.BookmarkBorder else Icons.Outlined.LocalCafe, null, Modifier.size(44.dp), tint = Muted)
        Text(if (filtered) "暫時搵唔到符合嘅店舖" else if (tab == 1) "將想去嘅咖啡店收藏喺度" else "第一杯，由今日開始", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(if (filtered) "試下其他關鍵字或地區。" else "開啟店舖詳情，就可以收藏同記錄到訪。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (filtered) TextButton(onClick = reset) { Text("重設篩選") }
    }
}

@Composable private fun DetailScreen(cafe: Cafe, entry: Journal, onBack: () -> Unit, onFavorite: () -> Unit, onVisited: () -> Unit, onNote: (String) -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var note by rememberSaveable(cafe.id) { mutableStateOf(entry.note) }
    var deleteConfirm by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回列表") }
            Text("COFFEE / ${cafe.district}", fontSize = 11.sp, letterSpacing = 1.sp)
            IconButton(onClick = onFavorite) { Icon(if (entry.favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (entry.favorite) "取消收藏" else "收藏店舖") }
        } }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).padding(26.dp)) {
                Icon(Icons.Outlined.LocalCafe, null, Modifier.size(46.dp), tint = Peach)
                Text(cafe.district, color = Peach, fontSize = 13.sp, modifier = Modifier.padding(top = 22.dp))
                Text(cafe.name, fontSize = 29.sp, lineHeight = 38.sp, color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (cafe.englishName.isNotBlank() && cafe.englishName != cafe.name) Text(cafe.englishName, color = Color(0xFFD5DED5), modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onVisited, modifier = Modifier.weight(1f)) { Icon(if (entry.visited) Icons.Outlined.CheckCircle else Icons.Outlined.CheckCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (entry.visited) "已到訪 ✓" else "標記已到訪") }
            OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f)) { Text(if (entry.favorite) "已收藏" else "加入收藏") }
        } }
        item { InfoLine(Icons.Outlined.Place, "地址", cafe.address.ifBlank { "未提供詳細地址；地圖可顯示資料中的座標。" }) }
        item {
            Button(onClick = { openMap(context, cafe) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                Icon(Icons.Outlined.Map, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("用地圖開啟 / 導航")
            }
        }
        item { InfoLine(Icons.Outlined.Schedule, "營業時間", cafe.hours.ifBlank { "未提供 · 出發前請向店舖確認" }) }
        if (cafe.phone.isNotBlank()) item { InfoLine(Icons.Outlined.Call, "電話", cafe.phone); TextButton(onClick = { openLink(context, "tel:${Uri.encode(cafe.phone)}", Intent.ACTION_DIAL) }) { Text("致電店舖") } }
        if (cafe.website.isNotBlank()) item { TextButton(onClick = {
            val url = if (cafe.website.startsWith("https://") || cafe.website.startsWith("http://")) cafe.website else "https://${cafe.website}"
            openLink(context, url)
        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("瀏覽店舖網站") } }
        if (cafe.wifi || cafe.outdoor) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (cafe.wifi) SmallTag("Wi-Fi", Icons.Outlined.Wifi); if (cafe.outdoor) SmallTag("戶外座位", Icons.Outlined.Deck) } }
        item {
            HorizontalDivider(color = Line)
            Text("我嘅咖啡筆記", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            Text("記低飲過嘅豆、風味，同下次想試嘅一杯。", color = Muted, fontSize = 12.sp)
            OutlinedTextField(note, { if (it.length <= 4000) note = it }, placeholder = { Text("例如：下次想試手沖，靠窗位置好舒服。") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), minLines = 4, shape = RoundedCornerShape(16.dp))
            Button(onClick = { onNote(note) }, enabled = note != entry.note, modifier = Modifier.padding(top = 10.dp)) { Text(if (note == entry.note) "筆記已儲存" else "儲存筆記") }
            Text("筆記只儲存喺此裝置；解除安裝會刪除。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
        item {
            Text(if (cafe.custom) "私人新增店舖 · 只儲存於此裝置" else "資料來源：© OpenStreetMap contributors · ODbL\n設施與營業時間屬社群提供，未列出不代表沒有。", color = Muted, fontSize = 11.sp)
            if (!cafe.custom) TextButton(onClick = { openLink(context, "https://www.openstreetmap.org/${cafe.id}") }) { Text("查看 / 建議更新 OpenStreetMap 資料") }
            else TextButton(onClick = { deleteConfirm = true }) { Text("刪除此私人店舖", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("刪除私人店舖？") }, text = { Text("此店舖嘅收藏、到訪紀錄及筆記亦會刪除。") }, confirmButton = { TextButton(onClick = onDelete) { Text("刪除") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } })
}
@Composable private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(21.dp), tint = Muted)
        Column { Text(label, color = Muted, fontSize = 11.sp); Text(value, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 5.dp)) }
    }
}

@Composable private fun DataScreen(state: CoffeeState, onRefresh: () -> Unit, onImport: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("每間店，都有來處。", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("資料與設定", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Ink) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("你嘅香港咖啡圖鑑", color = Peach, fontSize = 13.sp)
                    Text("${state.all.size}", color = Paper, fontSize = 54.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(vertical = 8.dp))
                    Text("${state.cafes.size} 個開放資料紀錄 · ${state.custom.size} 間私人新增", color = Paper, fontSize = 12.sp)
                    Text("資料時間：${state.timestamp}", color = Color(0xFFD5DED5), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item {
            Text("涵蓋全港 18 區，逐間發掘", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("收錄 OpenStreetMap 標記為 cafe、並位於香港 18 區邊界內嘅紀錄，可能有漏收、重複或已結業店舖；未經逐店核實。這是可擴充嘅圖鑑，並非全港完整名冊。", color = Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 9.dp))
        }
        item {
            Button(onClick = onRefresh, enabled = !state.updating, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                if (state.updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Paper) else Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp)); Text(if (state.updating) "正在處理資料…" else "從 OpenStreetMap 更新")
            }
            Text("手動更新需要網絡；公共服務繁忙時會保留離線資料。更新只會更換店舖目錄，私人筆記及新增店舖會保留。", color = Muted, fontSize = 11.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 9.dp))
            OutlinedButton(onClick = onImport, enabled = !state.updating, modifier = Modifier.fillMaxWidth().padding(top = 13.dp)) { Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("匯入 Overpass JSON 目錄") }
            Text("匯入會取代開放資料目錄；格式見專案 README。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        }
        item { HorizontalDivider(color = Line) }
        item {
            Text("你嘅紀錄，留喺你部手機", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("收藏、到訪紀錄、私人筆記同新增店舖只儲存於此裝置。暫未有帳戶、雲端同步或備份；解除安裝或清除 app 資料會刪除私人紀錄。地圖與店舖網站會透過其他 app 開啟。", color = Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 9.dp))
        }
        item {
            Text("開放資料鳴謝", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("店舖與分區邊界：© OpenStreetMap contributors\n資料授權：Open Database License (ODbL) 1.0\n隨附資料擷取：Geofabrik 香港區域檔案", color = Muted, fontSize = 12.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 9.dp))
            TextButton(onClick = { openLink(context, "https://www.openstreetmap.org/copyright") }) { Text("OpenStreetMap 資料與授權") }
            Text("港啡 HK Coffee 1.0.0 · Kotlin + Jetpack Compose", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun AddCafeDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("中西區") }
    var address by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("留低一間新發現") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("新增至你嘅私人圖鑑，只儲存於此裝置。", color = Muted, fontSize = 12.sp)
            OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("店名 *") }, singleLine = true)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(area, {}, readOnly = true, label = { Text("地區") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    districtGroups.values.flatten().forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { area = item; expanded = false }) }
                }
            }
            OutlinedTextField(address, { if (it.length <= 500) address = it }, label = { Text("地址 *") }, minLines = 2)
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, area, address) }, enabled = name.isNotBlank() && address.isNotBlank()) { Text("加入圖鑑") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun openMap(context: Context, cafe: Cafe) {
    val query = if (cafe.latitude != null && cafe.longitude != null) "${cafe.latitude},${cafe.longitude}(${cafe.name})" else "香港 ${cafe.district} ${cafe.address} ${cafe.name}"
    val coordinates = if (cafe.latitude != null && cafe.longitude != null) "${cafe.latitude},${cafe.longitude}" else "0,0"
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=${Uri.encode(query)}"))) }
    catch (_: ActivityNotFoundException) { openLink(context, "https://www.google.com/maps/search/?api=1&query=${Uri.encode(if (cafe.latitude != null) "${cafe.latitude},${cafe.longitude}" else query)}") }
}
private fun openLink(context: Context, url: String, action: String = Intent.ACTION_VIEW) {
    val uri = Uri.parse(url)
    if ((action == Intent.ACTION_DIAL && uri.scheme != "tel") || (action == Intent.ACTION_VIEW && uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http"))) return
    try { context.startActivity(Intent(action, uri)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "未安裝可開啟此連結嘅應用程式。", Toast.LENGTH_SHORT).show() }
}
