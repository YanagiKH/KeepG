# Changelog

## 0.7.0 — 工作區、AI 與驗證發布

### 新增

- 六分類設定與跨分類搜尋、外觀／瀏覽／匯出／AI／安全／維護自訂項目。
- 圖片手勢模式、裁切／文字圖層、復原重做及格式品質控制；GIF 動畫範圍／速度／反向／循環；Media3 影片轉碼工作區。
- 全域本機 AI 對話、Gemma 4 E2B/E4B 預設、HF 搜尋／固定修訂下載、相容模型匯入、受限附件與需確認的 Skills／相簿操作。
- 完整 Full/Lite 安裝矩陣與 main 成功 push CI 產物的自動校驗發布；8 APK、2 AAB 與 3 份校驗／來源資料。
- 真實模擬器截圖、四語系防漏檢查及安全／AI／編輯／除錯手冊。

### 修正

- 相簿內多選及批次操作不再跳出目前範圍。
- 全螢幕聊天／編輯對話框的 Android 35 系統列和鍵盤邊界。
- GIF 短區間尾幀延遲超出所選時長。
- 舊 UI 數量／修復／保險箱標籤未經本地化。
- 文件中過時的無 Internet、僅靜態 GIF、僅前五秒影片及重建發布敘述。

### 驗證與限制

- 新增真實圖片／GIF／影片輸出、原檔 SHA 保留、失敗／取消清理及聊天可見性測試，納入 API 26/35 Full/Lite。
- 發布仍是臨時 debug 簽署 prerelease，AAB 尚未正式簽署；不得以簽章衝突為由未備份就解除安裝。
- ARM64 的實際 Gemma 下載／推論、GPU/OEM 編碼器及長時間效能不由 x86_64 UI 測試證明，依手冊另行實機驗收。

## 0.6.0 - 2026-08-28

### Added

- On-device Latin, Chinese, Japanese, and Korean image-text indexing with album-scoped search.
- Photo and video capture with selectable quality, optional audio, pause/resume, timer, focus, zoom, exposure, flash, torch, lens switching, and a composition grid.
- Configurable grid columns, tile proportions, thumbnail framing, preview framing, badges, and interface animations.
- Direct manipulation for crop and text layers, image color controls, and arbitrary-range video trimming.
- Collection rename, clear, delete, and individual-item removal actions.
- Complete English, Traditional Chinese, Japanese, and Korean UI/widget localization coverage tests.

### Fixed

- Full-screen navigation now stays within the active album, Collection, Favorites list, or filtered result order.
- Partial MediaStore query failures no longer erase the cached library.
- MediaStore output publishing and video/image temporary files are cleaned up atomically after failures or cancellation.
- Large image analysis, OCR, sensitive-content classification, and video samples use bounded memory.
- Orphaned photo locks, face observations, OCR records, and Collection links are pruned after a verified complete scan.
- Camera and media-player lifecycle handling prevents background work after the screen stops.
- Batch selection and favorite operations avoid repeated full-state rewrites.

### Verification

- Full and Lite lint, JVM tests, assembly, and Android instrumentation run in GitHub Actions.
- Instrumentation covers Android API 26 and API 35.
- Releases are created only from the current `main` commit after same-repository push CI succeeds.
