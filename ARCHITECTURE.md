# KeepG 0.7 架構

## 版本與入口

單一 Android app module 使用 `edition` flavor：Full 為 `com.yanagikh.keepg`，Lite 為 `com.yanagikh.keepg.lite`。MainActivity 的現行主介面為 `KeepGAppV2`，預覽使用 `MediaPreviewScreenV2`。舊 UI 檔案仍在共用 source set，翻譯／編譯檢查同樣涵蓋，不以「舊路徑」排除問題。

共享層包含 MediaStore、Room、設定、本機 AI、模型下載與 Skills。Full 的 `AdvancedFeatureTools` 提供 OCR、條碼、去背、修復及既有快捷編輯；Lite 保留 no-op flavor 邊界，不含 Full 專用新增辨識依賴。`BuildConfig.FULL_FEATURES` 控制 UI 與操作支援範圍。

## 相簿及選取

`MediaStoreRepository → Room/StateFlow → MainViewModel → Compose`。裝置相簿是真實 MediaStore 分組；收藏集是 Room 參照，移除參照不刪除原檔。顯示集合先經權限、篩選及鎖定檢查；選取集合限目前可見、已授權媒體。相簿內操作列共用原批次流程，返回先清選取再導覽，不切換到全庫後才執行。

## 編輯流程

```text
媒體唯讀 URI
  ├─ ProfessionalImageEditor → AdvancedEditRequest → ImageRenderer → ImageExport
  ├─ GifAnimationEditor → GifSafety / GifTiming → GifEditRepository → GifEncoder
  └─ ProfessionalVideoEditor → VideoEditSpec → Media3 Transformer → VideoExporter
        ↓
新 MediaStore 副本 → 完成才公開 → 重新索引
```

圖片／GIF 共用標準化裁切與圖層繪製。GIF 在原生 Movie 解碼前檢查尺寸、幀數、區塊與配置預算；總延遲按所選時間量化，不讓最後一幀額外延長。影片工作區以 Media3 做精準時間裁切及空間／速度變換，與舊有 remux 快捷工具及修復路徑不同。

原檔不以寫入模式開啟；新檔在失敗／取消時回滾。Android 10+ 使用 IS_PENDING；影片取消先在正確 dispatcher 停止 Transformer 再清理暫存。原生解碼器、硬體與強制程序終止仍是外部可靠性邊界。

## 本機代理流程

```text
全域 AgentEntryButton → AgentHost / AgentViewModel
  ├─ ModelRepository → HF 搜尋與固定修訂資訊
  ├─ ModelDownloadWorker → 受控 HTTPS / 續傳 / SHA-256 → 私有 .litertlm
  ├─ AttachmentReader → 有限文字／視覺或中繼資料 → 私有暫存
  ├─ SkillStore → 有界 YAML/Markdown → 檢閱後安裝、預設停用
  └─ LiteRT-LM 本機對話 → AgentActionParser → 一次性確認 → MainViewModel 操作
```

模型輸出不是執行權限或成功證明。媒體 ID 必須在本輪授權可見集合；執行再次檢查鎖／版本／設定，保留 Android 與密碼確認。沒有 shell、網頁代理或可執行 Skills。CPU 文字為預設，GPU／視覺依模型與裝置。聊天在 RAM；背景／鎖／權限變動會作廢上下文與待執行建議。模型下載有 Internet 權限，但推論無雲端媒體上傳端點。

## 安全、設定及本地化

保險箱使用 Android Keystore AES-GCM；App 內鎖與 MediaStore 原檔加密是不同邊界。`KeepGLog` 僅寫私有日誌並經使用者文件選擇器匯出。分類、文字索引、人臉描述與規則均在本機，人物分群不是身分驗證。

`GallerySettingsScreen` 以六分類加跨分類搜尋顯示保存設定。`Localization.kt` 的四語系字典與 Android widget XML 資源提供自有 UI 翻譯；Python guard 與 JVM tests 檢查字串結構、鍵、參數及已知動態／常數標籤。使用者資料、模型回答及外部系統 UI 不翻譯成固定資源。

## 驗證與發布邊界

Android CI 對 Full／Lite 做 Lint、JVM、安裝產物與 API 26/35 整合測試；真實輸出測試驗證解碼、時長、原檔雜湊及失敗清理。UI 截圖來自合成素材的實際 Compose 畫面。Source audit 提供固定 checkout 來源。

main 的成功 push CI 才能觸發發布。發布使用同次 CI 位元組，驗證目前 main、來源、完整矩陣與 GitHub asset digest，草稿完整後才公開。自動 APK 使用 CI 臨時 debug 憑證；未簽署 AAB 和測試版發布不等於正式商店簽署。詳見 [除錯手冊](DEBUGGING.md)、[安全手冊](SECURITY.md)。
