<p align="center"><img src="docs/images/keepg-logo.svg" alt="KeepG 標誌" width="760" /></p>
<p align="center"><strong>Android 相簿管理、本機 AI 助理與非破壞式媒體編輯</strong></p>
<p align="center"><a href="https://github.com/YanagiKH/KeepG/actions/workflows/android.yml"><img alt="Android CI" src="https://github.com/YanagiKH/KeepG/actions/workflows/android.yml/badge.svg" /></a> · Android 8.0+ · Kotlin / Jetpack Compose · 本機推論</p>

## 下載與開始使用

從 [Releases](https://github.com/YanagiKH/KeepG/releases) 下載 **APK**。一般 64 位元 Android 手機選 `arm64-v8a`；不確定架構時選 `universal`。`armeabi-v7a` 供 32 位元裝置瀏覽相簿，不能執行本機 AI。`x86_64` 主要用於相容裝置／模擬器。**AAB 是商店建置產物，不能直接安裝。**

| 版本 | 用途 | 套件名稱 |
|---|---|---|
| Full | 完整相簿、圖片／GIF／影片編輯、智慧整理、保護、加密保險箱及 AI | `com.yanagikh.keepg` |
| Lite | 相簿、原生播放、搜尋、收藏集、設定、日誌與本機 AI；保留精簡版功能限制 | `com.yanagikh.keepg.lite` |

兩版可同時安裝。首次啟動依需求授予照片／影片權限；Android 14+ 的「僅選取照片」會刻意限制可見範圍。模型不隨 APK 內建，首次 AI 使用需另行確認下載。

**自動發布為測試版（prerelease）：** APK 以 CI 臨時 debug 憑證簽署，不是正式商店簽署版。不同 CI 的簽章可能不同。遇到更新簽章衝突時，**不要直接解除安裝或清除資料**；先匯出保險箱內容、確認獨立備份可讀，再處理測試版更換。失去 Android Keystore 金鑰後，單獨備份 `.kgv` 無法還原。正式穩定升級需要維護者提供固定、妥善保管的簽署金鑰。

## 0.7 功能總覽

設定分為 **外觀、瀏覽、編輯、AI 助理、安全、維護**，搜尋可跨分類比對目前語言。可自訂主題、桌布配色、2–8 欄、縮圖比例、預覽構圖、動畫、匯出品質與格式、裁切輔助線、AI 捷徑及下載策略。

Full 的圖片、GIF、影片使用專屬編輯工作區，支援直接拖曳與精準數值；所有匯出另存副本。裝置相簿、最愛及收藏集內長按可直接多選，操作列留在目前範圍，全選不跨相簿。

兩版均可從支援的應用程式畫面開啟 AI 對話、安裝 Gemma 4 E2B-it／E4B-it 相容模型、搜尋 Hugging Face、匯入 `.litertlm`、安裝受限 Skills。模型可聊天、閱讀支援的附件內容，並提出需確認的相簿操作。**下載模型不等於所有手機都已驗證可執行；檔案選擇器接受任意類型不等於模型能理解所有格式。**

介面與小工具提供 **繁體中文、English、日本語、한국어**。應用程式自有文字、錯誤、分類、數量與操作標籤納入檢查；使用者檔名、模型回答、技能原文和 Android 系統對話框不是應用程式翻譯字典。

## 真實介面示範

以下 PNG 是 Android API 35 模擬器執行 `WorkspaceUiTest` 時，使用合成素材擷取的 **Full 真實介面**，不是 Figma／Canva 設計稿或 AI 生成畫面。來源 CI、提交與檔案校驗記錄於 [截圖來源](docs/screenshots/PROVENANCE.json)。螢幕大小、語言、主題與系統列可因裝置不同。

### 分類設定與相簿內多選

<p><img src="docs/screenshots/settings.png" alt="設定頁：分類標籤、搜尋與自訂項目" width="320" /> <img src="docs/screenshots/album-selection.png" alt="特定相簿長按後的多檔選取與批次操作列" width="320" /></p>

開啟設定後選分類或搜尋關鍵字。開啟裝置相簿／收藏集後長按一個項目，再點選其他檔案；可全選、分享、加入最愛、加入收藏集等，Full 另有保護、分析及保險箱操作。清除選取仍停留在相簿；返回鍵先離開選取模式。

### 圖片、GIF 與影片編輯

<p><img src="docs/screenshots/image-editor.png" alt="圖片編輯：手勢模式、裁切、色彩與文字圖層" width="270" /> <img src="docs/screenshots/gif-editor.png" alt="GIF 動畫編輯：起訖範圍、速度、幀率與循環" width="270" /> <img src="docs/screenshots/video-editor.png" alt="影片編輯：預覽、拖曳裁切與時間範圍" width="270" /></p>

圖片先選「移動圖片／裁切／圖層」再拖曳，避免同一手勢改到錯誤物件；支援縮放、旋轉、翻轉、亮度／對比／飽和度、文字顏色／大小／順序及復原／重做。PNG 保留透明；JPEG 填白；WebP 為有損輸出。Full 的既有背景移除與修復工具仍保留。

GIF 可選「編輯動畫」或「編輯單一影格」。動畫可裁切時間、調速度／FPS、反向、循環，將裁切／色彩／圖層套到每幀；輸出限 30 秒、360 幀、640px 長邊、256 色，透明填白。GIF 延遲以 10ms 量化且每幀至少 20ms；短片尾幀會按總時長截斷，不額外延長到下一個完整 FPS 週期。

影片可拖曳裁切框及時間軸、輸入起訖毫秒、旋轉、翻轉、變速、靜音及選擇高度，透過 Media3 Transformer 編碼成新的 H.264/AAC MP4。來源可播放不保證裝置也有相容的輸出編碼器。完整限制與操作見 [編輯手冊](docs/EDITING.md)。

### 本機 AI 模型與全域對話

<p><img src="docs/screenshots/ai-models.png" alt="AI 模型頁：Gemma E2B/E4B、搜尋、匯入與下載管理" width="320" /> <img src="docs/screenshots/ai-chat.png" alt="AI 對話頁：附件、中繼資料選項與訊息輸入列" width="320" /></p>

按星光圖示 → 模型 → 選 Gemma E2B（輕量）或 E4B（普通）→ 檢閱發布者、授權、大小、修訂與 SHA-256 → 確認下載 → 選取模型 → 對話。預設 CPU 文字推論及不計量網路下載；GPU／視覺需自行啟用且受裝置支援限制。截圖不包含真實模型回答，不表示已完成 ARM 手機推論驗收。

要整理相簿，先啟用操作建議，並勾選「本輪提供可見媒體中繼資料」。模型的操作卡必須由使用者檢閱目標後執行，仍會經原本的密碼、Android 刪除／分享等確認。Skill 只是經審閱的 Markdown 指令，不執行其附帶腳本。附件支援範圍、範例與操作白名單見 [AI 手冊](AI_MANUAL.md)。

## 相簿、保護及智慧整理

KeepG 從 Android MediaStore 索引圖片與影片，提供檔名／相簿／MIME／本機 OCR 搜尋、原生播放、相機、最愛、裝置相簿與非破壞式收藏集。收藏集新增、移除、重新命名、清空、刪除只影響參照，不直接刪除媒體原檔。格式辨識與裝置解碼能力分開；特殊 HEIF、AVIF、RAW、TIFF 或影片編碼依系統供應器／解碼器而定。

Full 使用 ML Kit 進行裝置端人臉、表情、文字、條碼／網址與背景分析，可設定人物、時間、位置、表情智慧規則。人物分群是可修正的便利分類，不是身分驗證。掃描後開啟 HTTP(S) 網址會離開 KeepG 到外部瀏覽器，網址本身仍可能有詐騙風險。

**應用程式內保護鎖不會加密 MediaStore 原檔，也不阻止其他相簿讀取原檔。** Full 的保險箱另外建立 Android Keystore AES-GCM 加密副本；確認可解密及已做獨立備份後，才考慮透過 Android 刪除原檔。編輯、修復及分享輸出的副本預設不是保險箱加密檔。

## 手冊索引

| 文件 | 內容 |
|---|---|
| [編輯與多選](docs/EDITING.md) | 各媒體操作、時間／記憶體上限、透明度、輸出與選取範圍 |
| [AI 手冊](AI_MANUAL.md) | 模型、Hugging Face、附件、Skills、操作確認、實機驗收 |
| [安全手冊](SECURITY.md) | 保護鎖、金鑰遺失、下載／提示注入邊界及安全使用 |
| [隱私說明](PRIVACY.md) | 本機資料、網路流量、聊天／暫存生命週期及分享 |
| [除錯與測試](DEBUGGING.md) | 建置指令、日誌、API 26/35、CI 產物、錯誤排查 |
| [架構](ARCHITECTURE.md) | 版本分層、媒體／代理流程與發布信任邊界 |
| [變更記錄](CHANGELOG.md) | 版本新增、修正與驗證範圍 |

## 權限與網路

0.7 **具有 `INTERNET` 權限**，用於使用者要求的模型搜尋、模型資訊及下載。模型推論、相簿分析與編輯在裝置上執行，沒有內建雲端聊天或媒體上傳端點。Hugging Face／下載主機仍能看到連線 IP、模型查詢及請求路徑；外部分享與瀏覽器由使用者另行決定。

媒體讀取、媒體位置、舊版儲存、相機、麥克風、生物辨識、網路狀態、前景下載服務及通知依系統版本／功能需要使用。無障礙服務、root、系統懸浮窗不是 AI 對話的必要條件；系統文件選擇器／密碼驗證等外部畫面不會被 AI 覆蓋或控制。

## 開發與完整驗證

工具鏈：JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.10.2；Kotlin／KSP／Room 版本以專案 Gradle 檔案為準。專案目前以 `gradle` 執行，不以不存在的 wrapper 作為前提。

```bash
python3 -m unittest discover -s scripts/tests -v
gradle :app:lintFullDebug :app:lintLiteDebug :app:testFullDebugUnitTest :app:testLiteDebugUnitTest --stacktrace
gradle :app:assembleFullDebug :app:assembleLiteDebug :app:bundleFullRelease :app:bundleLiteRelease --stacktrace
adb devices
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest --stacktrace
```

CI 在 API 26 與 API 35 上分別執行 Full／Lite 的 Android 測試；包含 Compose 操作、聊天輸入區、原始檔保留、PNG／JPEG／WebP 解碼、GIF 短區間、影片裁切／靜音和失敗輸出清理。語系檢查涵蓋字典結構、540 個四語系鍵、格式參數、資源一致性及直接寫死的英文標籤；不是僅比對鍵數量。

`keepg-build-reports` 是 Lint／單元測試／建置日誌；`keepg-device-api-26/35` 是裝置測試／截圖／Logcat；`keepg-installers` 才是完整安裝產物。以該次執行的 SHA 對照，不把舊綠燈當作新提交驗收。

## 自動 Releases

`main` 的同儲存庫 **push Android CI 全部通過** 後，發布工作才下載該次 CI 的原始產物，**不重新建置**。再次核對目前 `main`、來源提交、完整檔名集合、大小與 SHA-256；先上傳草稿，逐件驗證 GitHub 回報的 digest，完整才公開 prerelease。

每次預期 **13 個資產**：Full／Lite 各 4 APK（共 8）、2 個未簽署 AAB、`BUILD-INFO.json`、`SIGNATURES.txt`、`SHA256SUMS.txt`。下載後可執行 `sha256sum -c SHA256SUMS.txt`（需同資料夾全部資產），或核對所下載檔案的單獨雜湊。詳細狀態見 [Actions](https://github.com/YanagiKH/KeepG/actions)。

自動測試可以驗證已覆蓋案例，不能保證所有 Android 裝置、模型／編碼器組合與未知輸入沒有缺陷。ARM64 手機的 E2B／E4B 實際下載、離線推論、GPU 與長時間效能驗收須另依 AI 手冊完成，不以模擬器 UI 截圖代替。
