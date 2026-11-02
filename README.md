# 港啡 HK Coffee

可直接用 **Android Studio** 開啟嘅原生 Android 專案，使用 **Kotlin + Jetpack Compose / Material 3**。繁體中文介面，暖米白與咖啡綠配色。

## 用 Android Studio 開啟

1. Clone 此 repository，或在 Android Studio 選 **Get from VCS**，輸入 `https://github.com/alisonworld/coffeemap.git`。
2. Android Studio → **Open** → 選取 clone 後的 `coffeemap` 專案根目錄（內有 `settings.gradle.kts`），毋須另建專案。
3. 等待 Gradle Sync；如提示下載 SDK，安裝 **Android SDK Platform 35** 和 Build Tools。
4. Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK，選 **JDK 17** 或相容的 Android Studio 內置 JDK。
5. 選擇 Android 8.0 / API 26 或以上嘅模擬器或實機，按 **Run ▶**。

首次 Gradle Sync 需要網絡下載依賴。App 首次啟動可離線讀取隨附店舖資料，無須 API key。Repository 不包含電腦專用 `local.properties`，Android Studio 會設定本機 SDK 路徑。

可使用 Android Studio 編譯 APK；Google Play 上架仍需正式簽署及發佈設定。

## 已實作

- 香港咖啡店圖鑑：**670 個真實 OSM cafe 紀錄**，並非示範店舖。
- 按 **香港 18 區** 篩選；利用 OSM 行政邊界多邊形判定區域。
- 搜尋中英文店名、地址、地區；支援多關鍵字及全形英文字搜尋。
- 有 Wi-Fi 篩選：只顯示來源明確提供相關標記嘅紀錄。
- 收藏、已到訪清單、每間店舖私人筆記，關閉及重開 app 後保留。
- 店舖詳情：來源提供嘅地址、營業時間、電話、網站、戶外座位。
- 開啟手機地圖 / 導航、撥號器、網站；未安裝地圖 app 時以瀏覽器開啟地圖。
- 新增私人咖啡店（店名、18 區、地址），可收藏、記筆記、刪除。
- 手動從 Overpass 更新及匯入 Overpass JSON；更新失敗或匯入無效檔案會保留原有目錄。
- 離線隨附目錄、原子寫入更新快取、空結果提示與公共 API 節流。
- 手機及平板自適應列表；只需 INTERNET 權限，無須定位權限。

## 資料覆蓋與限制

**目標係逐步收錄全香港；目前未能宣稱收錄晒全港所有 coffee shops。**

隨附快照來自 [Geofabrik 香港 OSM 區域資料](https://download.geofabrik.de/asia/china/hong-kong.html)，資料庫時間為 **2026-10-25 21:21:21 UTC（香港時間 2026-10-26 05:21:21）**。擷取 `amenity=cafe` 節點、路徑及咖啡店多邊形關係，並以香港 18 區邊界篩選。紀錄以 OSM 類型及 ID 去重；同一實體如被社群以不同 ID 重複標註，仍可能有重複。

OpenStreetMap 係社群資料，可能有漏收、位置誤差、已結業店舖或缺少電話／營業時間；未經逐店核實。「Cafe」亦可能包括甜品店等較廣義咖啡館。未提供設施標記不等於沒有該設施；營業時間保留 OSM 原始格式，沒有假設「現在營業」。卡片圖示是裝飾圖示，並非店舖照片。

此版本使用外部地圖 app，未內置互動地圖、帳戶、雲端同步、用戶評論、相片或備份。私人資料只儲存喺此裝置；解除安裝或清除 app 資料會刪除。目錄更新不會刪除私人資料；來源移除嘅店舖其私人筆記仍保留於儲存檔，但暫時不會顯示在清單，除非相同 ID 再被收錄。

若要營運完整名冊，下一步應建立可維護嘅後端、店舖投稿與核實機制，並引入經授權嘅額外資料來源。

## 更新與匯入

「資料」頁 →「從 OpenStreetMap 更新」會對公開 Overpass 服務發出一次香港範圍查詢；不會在啟動時自動查詢，兩次嘗試至少相隔一分鐘。服務可能繁忙或超時，現有離線資料仍可用。大量正式使用者應接自家快取後端，避免每部手機直接查詢公共服務。

可以喺 [Overpass Turbo](https://overpass-turbo.eu/) 執行以下查詢，匯出原始 JSON，再於 app 匯入。匯入會更換開放資料目錄，私人新增及筆記保留。只接受 12 MB 以下、有有效香港 cafe 座標嘅 Overpass JSON。

```overpass
[out:json][timeout:90];
area["ISO3166-1"="HK"]["boundary"="administrative"]->.hk;
nwr["amenity"="cafe"](area.hk);
out center tags;
```

## 專案結構

```text
app/src/main/java/hk/coffee/atlas/
  MainActivity.kt               Android 入口
  CoffeeViewModel.kt            UI 狀態、收藏、到訪及資料操作
  data/Cafe.kt                 資料模型、搜尋、OSM 解析、分區判定
  data/CafeRepository.kt        離線快照、原子快取、私人資料、API
  ui/CoffeeApp.kt              探索、收藏、足跡、詳情及資料畫面
  ui/Theme.kt                  顏色與 Material 3 主題
app/src/main/assets/
  cafes.json                   OSM 咖啡店快照（ODbL）
  districts.json               OSM 香港 18 區多邊形（ODbL）
app/src/test/                  資料、地區、邊界洞、多條件搜尋及錯誤回應測試
tools/build_snapshot.py         從 Geofabrik PBF 重建隨附目錄
```

SDK：min 26、compile / target 35；Kotlin 2.0.21、AGP 8.7.3、Gradle 8.9、Compose BOM 2024.12.01、Java 17。依賴版本固定，方便重現編譯。

## 編譯與檢查

macOS / Linux：

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows：

```bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK 輸出：`app/build/outputs/apk/debug/app-debug.apk`。

重建資料快照（開發工具，不影響 Android 執行）：

```sh
python3 -m venv .data-venv
.data-venv/bin/pip install osmium shapely
# 先下載 Geofabrik 香港 .osm.pbf 到自行選擇的位置
.data-venv/bin/python tools/build_snapshot.py /path/to/hong-kong.osm.pbf
```

重建後請同步更新資料時間及測試中預期嘅筆數，並重新檢查授權和資料品質。

## 授權與鳴謝

店舖目錄及行政邊界：**© OpenStreetMap contributors**，以 **Open Database License (ODbL) 1.0** 提供。資料衍生檔案隨原始碼完整提供，授權與 app 程式碼分開。

- [OpenStreetMap copyright / attribution](https://www.openstreetmap.org/copyright)
- [ODbL 1.0 全文](https://opendatacommons.org/licenses/odbl/1-0/)；另附於 `DATA_LICENSE.html`。
- [Geofabrik 香港下載頁](https://download.geofabrik.de/asia/china/hong-kong.html)
- [公共 Overpass API 說明及使用限制](https://wiki.openstreetmap.org/wiki/Overpass_API)

AndroidX / Jetpack Compose（Apache 2.0）、Kotlin（Apache 2.0）、Gradle Wrapper（Apache 2.0）。本專案沒有抓取 Google Maps 或 OpenRice 店舖資料。
