# KeepG 0.7 除錯與測試手冊

## 工具鏈與重現環境

使用 JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.10.2。Kotlin 2.3.10／KSP 2.3.4／Room 2.8.4 與 LiteRT-LM 0.16.0 的 Kotlin metadata 配合；不要只降級其中一項或關閉 metadata 檢查。完整版本以 `build.gradle.kts`、`app/build.gradle.kts` 為準。

本專案使用 `gradle` 指令。Full 套件為 `com.yanagikh.keepg`，Lite 為 `com.yanagikh.keepg.lite`。以下操作應在測試裝置進行；先備份真實資料，不要以 `pm clear`／解除安裝作為通用除錯第一步。

```bash
python3 -m unittest discover -s scripts/tests -v
gradle :app:lintFullDebug :app:lintLiteDebug :app:testFullDebugUnitTest :app:testLiteDebugUnitTest --stacktrace
gradle :app:assembleFullDebug :app:assembleLiteDebug :app:bundleFullRelease :app:bundleLiteRelease --stacktrace
adb devices
gradle :app:installFullDebug
adb shell cmd package resolve-activity --brief com.yanagikh.keepg
adb shell am start -n com.yanagikh.keepg/.MainActivity
gradle :app:connectedFullDebugAndroidTest :app:connectedLiteDebugAndroidTest --stacktrace
```

多裝置時以 `adb -s SERIAL ...` 明確指定；分別在 API 26／35 執行 Android 測試。安裝失敗先確認 ABI、SDK、可用空間、舊版套件簽章，不要直接刪掉保險箱資料。

## 驗證層次

| 測試 | 應確認的結果 |
|---|---|
| Python guards | 字典必須在 Kotlin 字串內；四語系、格式參數及 XML 資源一致；未翻譯常數／數量標籤被攔截；發布產物缺漏／竄改／來源不符被拒絕 |
| JVM tests | 裁切範圍、復原狀態、GIF 編解碼／延遲、編輯參數、選取授權及既有安全／分類邏輯 |
| `WorkspaceUiTest` | 六類設定搜尋；特定相簿多選；圖片／GIF／影片畫面；AI 模型頁與聊天輸入／附件／送出在可視範圍，含鍵盤輸入 |
| `EditorExportTest` | 真正的 PNG/JPEG/WebP 編碼與解碼、透明 JPEG 填白、90ms GIF 尾幀／裁切、影片裁切／旋轉／靜音／可解碼、原檔 SHA 不變、寫入失敗／取消後無殘留 |
| 既有 Android tests | MediaStore 輸出、Android Keystore、Room、原生媒體等整合邊界 |

自動測試使用合成測試資料，不載入數 GB Gemma 權重，不替代 ARM64 實機推論、OEM 相機、GPU／HDR、長時間耗電與效能驗收。所有通過宣稱需對應同一個提交，不能沿用舊提交的綠燈。

## 日誌

設定 → 維護 → 除錯日誌，可開關、檢閱、匯出及清除。私有 `files/debug/keepg.log` 超過 2 MiB 後輪替至 `.1`。停用持久日誌不等於 Android Logcat 沒有錯誤。匯出透過文件選擇器，沒有自動上傳。

```bash
adb logcat -c
# 在裝置上重現一次，再擷取，而非長時間記錄私人活動。
adb logcat -d > keepg-logcat.txt
adb logcat -b crash -d > keepg-crash.txt
adb shell run-as com.yanagikh.keepg cat files/debug/keepg.log
```

`run-as` 適用可除錯建置。回報前遮蔽路徑、檔名、位置、人物名稱及任意 token；不要上傳密碼、金鑰、保險箱明文、整個資料庫或私人相簿。

## 常見問題

### 相簿為空、缺檔、多選範圍錯誤

在 Android 設定檢查照片／影片權限；Android 14+ 僅選取存取刻意只顯示授權媒體。回到 App 重新整理，確認檔案實際由 MediaStore 暴露。位置規則另外需要媒體位置資訊。格式可辨識不表示該手機有解碼器。

```bash
adb shell dumpsys package com.yanagikh.keepg
adb shell content query --uri content://media/external/images/media --projection _id:display_name:mime_type
```

輸出可能含私人檔名，公開前處理。多選重現應建立兩個測試相簿，進入其中一個長按 → 全選 → 清除 → 返回，確認不跨相簿、不切回總覽、不選到鎖定媒體。收藏集移除測試應確認原檔仍存在。

### 聊天輸入框或編輯儲存鈕被遮住

先測 API 35，分別在鍵盤隱藏／顯示及小螢幕狀態重現。全螢幕 Compose Dialog 使用 `usePlatformDefaultWidth=false, decorFitsSystemWindows=false`，內容消耗 `safeDrawingPadding()`／`imePadding()`。不要只縮小字體來掩蓋錯誤，也不要刪掉 `assertIsDisplayed` 測試讓 CI 假通過。

```bash
adb exec-out uiautomator dump /dev/tty > keepg-ui.xml
adb exec-out screencap -p > keepg-screen.png
```

UI 操作座標從 XML node bounds 取得，滾動後重新擷取；PNG 用於核查視覺，不用來猜測點擊座標。文件截圖使用合成素材，並保存來源提交與 CI。

### 圖片／GIF 匯出不符預覽或失敗

先用普通小 PNG/JPEG 測試移動、裁切、圖層，檢查原始檔 SHA 不變與新副本可解碼。PNG 保留 alpha；JPEG 明確填白；WebP 有損品質會改變像素。輸出尺寸與解碼均有記憶體上限，不應宣稱原始解析度無限制。

GIF 動畫與單一影格是兩種不同入口。動畫有 30 秒／360 幀／640px／256 色限制，透明填白，延遲量化為 centisecond，每幀至少 20ms。對短區間驗證總時長：例如 90ms、12 FPS 應為兩幀 70ms + 20ms，不應輸出 170ms。超出安全尺寸／幀數／檔案大小應拒絕。

失敗或取消要查新 MediaStore 項目及暫存是否清理，不只看提示文字。不得以覆寫原檔或跳過安全檢查解決錯誤。背景去除是分類／色彩近似，邊緣可能需要手動調整，不保證所有照片完美。

### 影片

新工作區使用 Media3 Transformer 重新編碼 H.264/AAC；既有快速編輯／修復仍可用 MediaExtractor／MediaMuxer。不要再以「只支援前五秒、不能轉碼」描述新工作區。

先使用測試 fixture `app/src/androidTest/assets/editor-sample.mp4`，檢查起訖時間、旋轉、裁切、0.25–4 倍速、靜音及選定高度。來源可預覽但匯出失敗時，可能是編碼器、GPU、特殊 HDR／容器、低記憶體或空間不足；記錄 MIME、尺寸、時長、選項和例外即可。取消先停止原生工作，再清暫存；原檔不以寫入模式開啟。

### 模型下載與推論

下載等候時先檢查不計量網路設定、通知／前景服務限制及剩餘磁碟。401/403 可能需要在 HF 接受授權和最小 read token；不要把 token 貼入 issue。SHA／大小不符不可忽略。重試相同固定修訂才可續傳。

先測 64 位元、CPU 文字、E2B，減少歷史／附件，關閉 GPU 和視覺。`.litertlm` 副檔名不是模型可執行保證；GGUF／Safetensors 不會自動轉換。原生載入失敗可能包含 ABI、格式、記憶體及硬體支援問題。詳見 [AI 手冊](AI_MANUAL.md) 的實機驗收清單。

### 鎖定與保險箱

裝置必須配置可用 PIN／圖形／密碼／生物辨識，沒有憑證的模擬器可能回報認證不可用。GCM 解密錯誤可能來自檔案竄改、損毀、金鑰失效或曾解除安裝；不可略過驗證標籤。不得用清除 App 資料「修復」唯一的保險箱內容。移機前在原裝置解密匯出並驗證獨立備份。

## CI 報告及發布

Android CI 的 `build-test-lint` 驗證兩版 Lint／JVM、8 個 APK 矩陣、2 AAB 及簽章／雜湊；`instrumentation (26)`、`instrumentation (35)` 各執行兩版。`Source audit` 保存實際 checkout 的來源封存；PR 的 synthetic merge SHA 與分支 SHA 可以不同，報告要清楚指出是哪一種。

產物：`keepg-build-reports` 為 Lint、JVM 和建置日誌；`keepg-device-api-26/35` 為 XML、Logcat、截圖；`keepg-installers` 為安裝檔；`keepg-package-manifest` 為來源／簽章／雜湊。執行中日誌下載暫時 404 不能當成測試通過或功能失敗，要讀取完成的工作及其產物。

`Publish verified installers` 只接受同儲存庫、目前 main、成功的 **push** Android CI。手動 dispatch CI 不會自動發布。發布下載同次 CI 的完整資產，不重建；草稿上傳並逐件比對 digest 後公開測試版。缺檔、額外檔、來源／大小／雜湊錯誤或 main 已前進都應停止，不得為追求綠燈繞過檢查。

確認發布完成需同時看到 main CI 全部成功、發布工作成功、Release 公開且 13 個資產完整。APK 為臨時 debug 簽章，AAB 不可直接安裝；簽章衝突時先保護及匯出資料，不要直接解除安裝。
