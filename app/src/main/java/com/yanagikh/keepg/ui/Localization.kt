package com.yanagikh.keepg.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.yanagikh.keepg.data.AppLanguage
import java.util.Locale

internal val LocalAppLanguage = compositionLocalOf { AppLanguage.AUTO }

@Composable
internal fun tr(key: String): String = UiLocalizer.text(LocalAppLanguage.current, key)

@Composable
internal fun trf(key: String, vararg args: Any): String = String.format(Locale.ROOT, tr(key), *args)

internal object UiLocalizer {
    private const val ENGLISH = "en"

    private val dictionaries: Map<String, Map<String, String>> = parseBundle(
        """
        %s detected faces · heuristic group\t偵測到 %s 張臉孔 · 推測群組\t%s件の顔を検出 · 推定グループ\t얼굴 %s개 감지 · 추정 그룹
        %s items\t%s 個項目\t%s件\t항목 %s개
        %s items · Protected\t%s 個項目 · 已保護\t%s件 · 保護済み\t항목 %s개 · 보호됨
        %s items · restore or permanently delete\t%s 個項目 · 可還原或永久刪除\t%s件 · 復元または完全削除\t항목 %s개 · 복원 또는 영구 삭제
        %s recoverable items\t%s 個可復原項目\t復元可能な項目 %s件\t복구 가능한 항목 %s개
        %s · %s matches\t%s · %s 個相符項目\t%s · %s件一致\t%s · %s개 일치
        %s cancelled\t%s 已取消\t%sをキャンセルしました\t%s 취소됨
        %s complete\t%s 已完成\t%sが完了しました\t%s 완료
        %s is unavailable on this Android version\t此 Android 版本無法使用%s\tこのAndroidバージョンでは%sを使用できません\t이 Android 버전에서는 %s을(를) 사용할 수 없습니다
        Added %s items to collection\t已將 %s 個項目加入收藏集\t%s件をコレクションに追加しました\t항목 %s개를 컬렉션에 추가함
        Advanced repair is available in KeepG Full\t進階修復僅適用於 KeepG Full\t高度な修復はKeepG Fullで利用できます\t고급 복구는 KeepG Full에서 사용할 수 있습니다
        Analysis complete\t分析完成\t分析が完了しました\t분석 완료
        Analyzing %s/%s: %s\t分析中 %s／%s：%s\t分析中 %s／%s：%s\t분석 중 %s/%s: %s
        Dates must use YYYY-MM-DD\t日期格式必須為 YYYY-MM-DD\t日付はYYYY-MM-DD形式で入力してください\t날짜는 YYYY-MM-DD 형식이어야 합니다
        Debug log cleared\t除錯日誌已清除\tデバッグログを消去しました\t디버그 로그 지워짐
        Debug log exported\t除錯日誌已匯出\tデバッグログを書き出しました\t디버그 로그 내보냄
        Deletion password updated\t刪除密碼已更新\t削除パスワードを更新しました\t삭제 비밀번호 업데이트됨
        Edited copy created\t已建立編輯後副本\t編集済みコピーを作成しました\t편집된 복사본 생성됨
        Encrypted copy stored in Vault\t加密副本已存入保險庫\t暗号化コピーを保管庫に保存しました\t암호화된 복사본을 보관함에 저장함
        Face analysis is available for images in KeepG Full\t圖片臉孔分析僅適用於 KeepG Full\t画像の顔分析はKeepG Fullで利用できます\t이미지 얼굴 분석은 KeepG Full에서 사용할 수 있습니다
        Image text index cleared\t圖片文字索引已清除\t画像テキストのインデックスを消去しました\t이미지 텍스트 색인 지워짐
        Image text index is already up to date\t圖片文字索引已是最新狀態\t画像テキストのインデックスは最新です\t이미지 텍스트 색인이 최신 상태입니다
        Image text search is available in KeepG Full\t圖片文字搜尋僅適用於 KeepG Full\t画像内テキスト検索はKeepG Fullで利用できます\t이미지 텍스트 검색은 KeepG Full에서 사용할 수 있습니다
        Indexed %s/%s images\t已索引 %s／%s 張圖片\t画像を%s／%s件インデックス化しました\t이미지 %s/%s개 색인됨
        Library refreshed\t媒體庫已重新整理\tライブラリを更新しました\t라이브러리 새로고침됨
        Link scan complete\t連結掃描完成\tリンクのスキャンが完了しました\t링크 스캔 완료
        Media removed\t媒體已移除\tメディアを削除しました\t미디어 제거됨
        Media renamed\t媒體已重新命名\tメディアの名前を変更しました\t미디어 이름 변경됨
        Operation failed\t操作失敗\t操作に失敗しました\t작업 실패
        Protected %s items\t已保護 %s 個項目\t%s件を保護しました\t항목 %s개 보호됨
        Removed %s item(s)\t已移除 %s 個項目\t%s件を削除しました\t항목 %s개 제거됨
        Repair could not recover this file: %s\t修復無法復原此檔案：%s\tこのファイルを修復できませんでした：%s\t이 파일을 복구하지 못했습니다: %s
        Repair finished\t修復完成\t修復が完了しました\t복구 완료
        Recovered copy created: %s · MIME %s · date %s\t已建立復原副本：%s · MIME %s · 日期 %s\t復元コピーを作成しました：%s · MIME %s · 日付 %s\t복구된 복사본 생성됨: %s · MIME %s · 날짜 %s
        Smart analysis complete: %s faces processed\t智慧分析完成：已處理 %s 張臉孔\tスマート分析完了：%s件の顔を処理しました\t스마트 분석 완료: 얼굴 %s개 처리됨
        Smart analysis is available in KeepG Full\t智慧分析僅適用於 KeepG Full\tスマート分析はKeepG Fullで利用できます\t스마트 분석은 KeepG Full에서 사용할 수 있습니다
        Unable to create lock\t無法建立鎖定\tロックを作成できません\t잠금을 만들 수 없음
        Unable to protect selected media\t無法保護所選媒體\t選択したメディアを保護できません\t선택한 미디어를 보호할 수 없음
        Unable to share media\t無法分享媒體\tメディアを共有できません\t미디어를 공유할 수 없음
        Vault item removed\t保險庫項目已移除\t保管庫の項目を削除しました\t보관함 항목 제거됨
        A KeepG lock controls display inside KeepG. For confidentiality from other gallery apps, also create a Vault copy and remove the original through Android system controls. See SECURITY.md.\tKeepG 鎖定只控制 KeepG 內的顯示。若要避免其他相簿應用程式存取，請另外建立保險庫副本，並透過 Android 系統控制項移除原始檔。詳見 SECURITY.md。\tKeepGのロックはKeepG内の表示だけを制御します。他のギャラリーアプリからも見えないようにするには、保管庫へコピーし、Androidのシステム操作で元のファイルを削除してください。詳しくはSECURITY.mdを参照してください。\tKeepG 잠금은 KeepG 내부 표시만 제어합니다. 다른 갤러리 앱에서도 보이지 않게 하려면 보관함 복사본을 만들고 Android 시스템 제어 기능으로 원본을 제거하세요. SECURITY.md를 참고하세요.
        Add text layer\t新增文字圖層\tテキストレイヤーを追加\t텍스트 레이어 추가
        Add to collection\t加入收藏集\tコレクションに追加\t컬렉션에 추가
        Added %s items to %s\t已將 %s 個項目加入 %s\t%s件を%sに追加しました\t항목 %s개를 %s에 추가함
        All items will be removed from this collection. Media files stay on your device.\t所有項目都會從此收藏集中移除，媒體檔案仍會保留在裝置上。\tすべての項目をこのコレクションから外します。メディアファイルは端末に残ります。\t모든 항목이 이 컬렉션에서 제거됩니다. 미디어 파일은 기기에 유지됩니다.
        Adaptive\t自動調整\t自動調整\t자동 조절
        Album\t相簿\tアルバム\t앨범
        Albums\t相簿\tアルバム\t앨범
        All\t全部\tすべて\t전체
        Analyze\t分析\t分析\t분석
        Analyze library\t分析媒體庫\tライブラリを分析\t라이브러리 분석
        Analyzed %s/%s selected images\t已分析 %s／%s 張所選圖片\t選択画像%s／%s件を分析しました\t선택한 이미지 %s/%s개 분석됨
        Analyzing…\t分析中…\t分析中…\t분석 중…
        Animate tab, grid, and preview transitions.\t顯示分頁、格狀清單與預覽的轉場動畫。\tタブ、グリッド、プレビューの切り替えをアニメーション化します。\t탭, 격자 및 미리보기 전환에 애니메이션을 적용합니다.
        Any size\t任何大小\tすべてのサイズ\t모든 크기
        Apply edit\t套用編輯\t編集を適用\t편집 적용
        Ascending\t遞增\t昇順\t오름차순
        Auto quality\t自動畫質\t自動画質\t자동 화질
        Auto remove\t自動去背\t背景を自動削除\t배경 자동 제거
        Automatic person background removal\t自動移除人物背景\t人物の背景を自動削除\t인물 배경 자동 제거
        Automatic\t自動\t自動\t자동
        Back\t返回\t戻る\t뒤로
        Background\t背景\t背景\t배경
        Brightness: %s\t亮度：%s\t明るさ：%s\t밝기: %s
        Broad media support\t廣泛媒體格式支援\t幅広いメディア形式に対応\t폭넓은 미디어 형식 지원
        Camera\t相機\tカメラ\t카메라
        Camera initialization failed: %s\t相機初始化失敗：%s\tカメラの初期化に失敗しました：%s\t카메라 초기화 실패: %s
        Camera permission request failed: %s\t要求相機權限失敗：%s\tカメラ権限の要求に失敗しました：%s\t카메라 권한 요청 실패: %s
        Camera unavailable: %s\t相機無法使用：%s\tカメラを使用できません：%s\t카메라를 사용할 수 없음: %s
        Camera composition grid\t相機構圖格線\tカメラ構図グリッド\t카메라 구도 격자
        Center square crop\t置中正方形裁切\t中央を正方形に切り抜く\t가운데 정사각형으로 자르기
        Cancel\t取消\tキャンセル\t취소
        Chinese\t中文\t中国語\t중국어
        Choose a KeepG unlock method.\t選擇 KeepG 解鎖方式。\tKeepGのロック解除方法を選択してください。\tKeepG 잠금 해제 방법을 선택하세요.
        Clear\t清除\t消去\t지우기
        Clear collection\t清空收藏集\tコレクションを空にする\t컬렉션 비우기
        Clear index\t清除索引\tインデックスを消去\t색인 지우기
        Clear selection\t清除選取\t選択を解除\t선택 해제
        Close\t關閉\t閉じる\t닫기
        Confirm device lock\t確認裝置鎖定\t端末ロックを確認\t기기 잠금 확인
        Collection\t收藏集\tコレクション\t컬렉션
        Collections\t收藏集\tコレクション\t컬렉션
        Color adjustments\t色彩調整\t色調整\t색상 조정
        Contrast: %s\t對比：%s\tコントラスト：%s\t대비: %s
        Copy album to Vault\t將相簿複製到保險庫\tアルバムを保管庫へコピー\t앨범을 보관함에 복사
        Create\t建立\t作成\t만들기
        Create deletion password\t建立刪除密碼\t削除パスワードを作成\t삭제 비밀번호 만들기
        Create muted copy\t建立靜音副本\t音声なしのコピーを作成\t음소거 복사본 만들기
        Create person rule\t建立人物規則\t人物ルールを作成\t인물 규칙 만들기
        Create rules for time, location, expression, or a named person group.\t依時間、地點、表情或已命名人物群組建立規則。\t時間、場所、表情、または名前付き人物グループのルールを作成します。\t시간, 위치, 표정 또는 이름이 지정된 인물 그룹 규칙을 만듭니다.
        Create smart album rule\t建立智慧相簿規則\tスマートアルバムルールを作成\t스마트 앨범 규칙 만들기
        Crop\t裁切\t切り抜き\t자르기
        Capture failed: %s\t拍攝失敗：%s\t撮影に失敗しました：%s\t촬영 실패: %s
        Date\t日期\t日付\t날짜
        Debug logging\t除錯日誌\tデバッグログ\t디버그 로그
        Delete\t刪除\t削除\t삭제
        Delete collection\t刪除收藏集\tコレクションを削除\t컬렉션 삭제
        Delete media\t刪除媒體\tメディアを削除\t미디어 삭제
        Delete media from device\t從裝置刪除媒體\t端末からメディアを削除\t기기에서 미디어 삭제
        Delete all permanently\t全部永久刪除\tすべて完全に削除\t모두 영구 삭제
        Delete permanently\t永久刪除\t完全に削除\t영구 삭제
        Delete selected\t刪除已選項目\t選択項目を削除\t선택 항목 삭제
        Delete to recoverable trash\t刪除時移到可復原垃圾桶\t復元可能なゴミ箱へ移動\t복구 가능한 휴지통으로 이동
        Deletion password\t刪除密碼\t削除パスワード\t삭제 비밀번호
        Descending\t遞減\t降順\t내림차순
        Details\t詳細資訊\t詳細\t세부 정보
        Detected faces\t偵測到的臉孔\t検出した顔\t감지된 얼굴
        Detect faces, extract local features, group likely matches, and organize by people, time, location, or expression.\t偵測臉孔、擷取本機特徵、分組相似人物，並依人物、時間、地點或表情整理。\t顔を検出して端末内で特徴を抽出し、似た人物をまとめ、人物・時間・場所・表情で整理します。\t얼굴을 감지하고 기기에서 특징을 추출하여 비슷한 인물을 그룹화하고 인물, 시간, 위치 또는 표정별로 정리합니다.
        Device albums\t裝置相簿\t端末アルバム\t기기 앨범
        Device credential\t裝置驗證\t端末の認証\t기기 인증
        Drag with one finger; pinch with two fingers to move and scale.\t單指拖移，雙指縮放以移動與調整大小。\t1本指でドラッグし、2本指で移動と拡大縮小を行います。\t한 손가락으로 끌고 두 손가락으로 이동하거나 크기를 조절하세요.
        Drag, pinch, or rotate the image, crop box, and text layers directly on the preview.\t直接在預覽上拖移、縮放或旋轉圖片、裁切框與文字圖層。\tプレビュー上で画像、切り抜き枠、テキストレイヤーを直接ドラッグ、ピンチ、回転できます。\t미리보기에서 이미지, 자르기 상자 및 텍스트 레이어를 직접 끌거나 확대·축소하고 회전하세요.
        Duration\t長度\t長さ\t길이
        Edit\t編輯\t編集\t편집
        Edits are non-destructive: KeepG writes a new media item.\t編輯不會破壞原始檔：KeepG 會建立新的媒體項目。\t編集は非破壊です。KeepGは新しいメディア項目として保存します。\t편집은 비파괴 방식입니다. KeepG가 새 미디어 항목을 만듭니다.
        End YYYY-MM-DD\t結束日期 YYYY-MM-DD\t終了日 YYYY-MM-DD\t종료일 YYYY-MM-DD
        English\t英文\t英語\t영어
        Entire library\t整個媒體庫\tライブラリ全体\t전체 라이브러리
        Enter deletion password\t輸入刪除密碼\t削除パスワードを入力\t삭제 비밀번호 입력
        Encrypted %s\t已加密 %s\t暗号化済み %s\t암호화됨 %s
        Encrypted app-private storage\t加密的應用程式私人儲存空間\t暗号化されたアプリ専用ストレージ\t암호화된 앱 전용 저장소
        Export log\t匯出日誌\tログを書き出す\t로그 내보내기
        Extract GIF first frame as PNG\t將 GIF 第一格擷取為 PNG\tGIFの最初のフレームをPNGとして抽出\tGIF 첫 프레임을 PNG로 추출
        Expression\t表情\t表情\t표정
        Extension\t副檔名\t拡張子\t확장자
        Favorite\t加入最愛\tお気に入りに追加\t즐겨찾기에 추가
        Favorite all\t全部加入最愛\tすべてお気に入りに追加\t모두 즐겨찾기에 추가
        Favorite selected\t將已選項目加入最愛\t選択項目をお気に入りに追加\t선택 항목을 즐겨찾기에 추가
        Favorites\t我的最愛\tお気に入り\t즐겨찾기
        File information\t檔案資訊\tファイル情報\t파일 정보
        File size\t檔案大小\tファイルサイズ\t파일 크기
        Fill\t填滿\t画面いっぱい\t채우기
        Filters\t篩選\tフィルター\t필터
        Fit\t完整顯示\t全体表示\t맞춤
        Flip\t翻轉\t反転\t뒤집기
        Flip horizontally\t水平翻轉\t左右反転\t좌우 뒤집기
        Free\t自由\t自由\t자유
        Full-screen preview framing\t全螢幕預覽顯示方式\t全画面プレビューの表示方法\t전체 화면 미리보기 표시 방식
        GIFs\tGIF 動畫\tGIF画像\tGIF 이미지
        Grant\t授予\t許可\t허용
        Grant or review Android photo, video and media-location access. Camera access is requested only when opening KeepG Camera.\t授予或檢視 Android 相片、影片與媒體位置存取權。只有開啟 KeepG 相機時才會要求相機權限。\tAndroidの写真、動画、メディア位置情報へのアクセスを許可または確認します。カメラ権限はKeepGカメラを開くときだけ要求されます。\tAndroid 사진, 동영상 및 미디어 위치 접근 권한을 허용하거나 검토합니다. 카메라 권한은 KeepG 카메라를 열 때만 요청됩니다.
        Grant or review Android photo, video and media-location access.\t授予或檢視 Android 相片、影片與媒體位置存取權。\tAndroidの写真、動画、メディア位置情報へのアクセスを許可または確認します。\tAndroid 사진, 동영상 및 미디어 위치 접근 권한을 허용하거나 검토합니다.
        Grant photo and video access, then refresh your library.\t授予相片與影片存取權後重新整理媒體庫。\t写真と動画へのアクセスを許可してからライブラリを更新してください。\t사진 및 동영상 접근을 허용한 다음 라이브러리를 새로고침하세요.
        Grayscale\t灰階\tグレースケール\t회색조
        Grid columns\t格狀欄數\tグリッド列数\t격자 열 수
        Grid layout\t格狀版面\tグリッドレイアウト\t격자 레이아웃
        Hide likely NSFW / graphic content\t隱藏疑似成人或血腥內容\t成人向け・刺激の強い内容を非表示\t성인용 또는 자극적인 콘텐츠 숨기기
        Image editor\t圖片編輯器\t画像エディター\t이미지 편집기
        Image / GIF editor\t圖片／GIF 編輯器\t画像・GIFエディター\t이미지/GIF 편집기
        Image text\t圖片文字\t画像内テキスト\t이미지 텍스트
        Index image text\t建立圖片文字索引\t画像内テキストをインデックス化\t이미지 텍스트 색인
        Images\t圖片\t画像\t이미지
        Include microphone audio in new videos after permission is granted.\t取得權限後，在新錄製的影片中加入麥克風音訊。\t権限の許可後、新しい動画にマイク音声を含めます。\t권한이 허용되면 새 동영상에 마이크 오디오를 포함합니다.
        Incorrect password\t密碼錯誤\tパスワードが違います\t비밀번호가 올바르지 않습니다
        Indexed media\t已建立索引的媒體\tインデックス済みメディア\t색인된 미디어
        Indexed %s of %s images · %s failed\t已索引 %s／%s 張圖片 · %s 張失敗\t%s／%s件の画像をインデックス化 · %s件失敗\t이미지 %s/%s개 색인됨 · %s개 실패
        Interface animations\t介面動畫\t画面アニメーション\t인터페이스 애니메이션
        Items moved to Android MediaStore trash appear here until restored or expired.\t移到 Android MediaStore 垃圾桶的項目會顯示在這裡，直到還原或到期為止。\tAndroid MediaStoreのゴミ箱へ移動した項目は、復元または期限切れになるまでここに表示されます。\tAndroid MediaStore 휴지통으로 이동한 항목은 복원되거나 만료될 때까지 여기에 표시됩니다.
        JPEG/JFIF, PNG, WebP, GIF, BMP, HEIC/HEIF, AVIF, DNG, TIFF, ICO, SVG and Android-indexed MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS media are recognized when a device decoder/provider exposes them.\t裝置解碼器或提供者支援時，可辨識 JPEG/JFIF、PNG、WebP、GIF、BMP、HEIC/HEIF、AVIF、DNG、TIFF、ICO、SVG，以及由 Android 建立索引的 MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS 媒體。\t端末のデコーダーまたはプロバイダーが対応している場合、JPEG/JFIF、PNG、WebP、GIF、BMP、HEIC/HEIF、AVIF、DNG、TIFF、ICO、SVGと、Androidでインデックス化されたMP4/MOV/3GP/MKV/WebM/AVI/MPEG/TSを認識します。\t기기 디코더 또는 제공자가 지원하면 JPEG/JFIF, PNG, WebP, GIF, BMP, HEIC/HEIF, AVIF, DNG, TIFF, ICO, SVG 및 Android에서 색인된 MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS 미디어를 인식합니다.
        Japanese\t日文\t日本語\t일본어
        KeepG Camera\tKeepG 相機\tKeepGカメラ\tKeepG 카메라
        KeepG detected these HTTP(S) links. Opening a link leaves KeepG and uses your external browser.\tKeepG 偵測到這些 HTTP(S) 連結。開啟連結會離開 KeepG，並使用外部瀏覽器。\tKeepGが次のHTTP(S)リンクを検出しました。リンクを開くとKeepGを離れ、外部ブラウザを使用します。\tKeepG가 다음 HTTP(S) 링크를 감지했습니다. 링크를 열면 KeepG를 벗어나 외부 브라우저를 사용합니다.
        KeepG password\tKeepG 密碼\tKeepGパスワード\tKeepG 비밀번호
        KeepG debug log\tKeepG 除錯日誌\tKeepGデバッグログ\tKeepG 디버그 로그
        KeepG creates a recovered copy instead of destructively rewriting the original. Choose whether to preserve the current indexed date or replace an invalid date with the current time.\tKeepG 會建立復原副本，不會破壞性地覆寫原始檔。請選擇保留目前索引日期，或以目前時間取代無效日期。\tKeepGは元のファイルを破壊的に書き換えず、復元済みコピーを作成します。現在のインデックス日付を保持するか、無効な日付を現在時刻に置き換えるか選択してください。\tKeepG는 원본을 파괴적으로 덮어쓰지 않고 복구된 복사본을 만듭니다. 현재 색인 날짜를 유지할지, 잘못된 날짜를 현재 시간으로 바꿀지 선택하세요.
        Keep background\t保留背景\t背景を保持\t배경 유지
        Korean\t韓文\t韓国語\t한국어
        Language\t語言\t言語\t언어
        Large > 10 MB\t大型（大於 10 MB）\t大（10 MB超）\t큼(10 MB 초과)
        Latitude\t緯度\t緯度\t위도
        Layers\t圖層\tレイヤー\t레이어
        Leave blank for normal sharing. A password creates an AES-encrypted ZIP.\t留空為一般分享；輸入密碼會建立 AES 加密 ZIP。\t空欄の場合は通常共有です。パスワードを設定するとAES暗号化ZIPを作成します。\t비워 두면 일반 공유를 사용합니다. 비밀번호를 입력하면 AES 암호화 ZIP이 생성됩니다.
        Lightweight edition: gallery, native playback, albums, search and collections.\t輕量版：提供相簿、原生播放、搜尋與收藏集功能。\t軽量版：ギャラリー、ネイティブ再生、アルバム、検索、コレクションを利用できます。\t경량판: 갤러리, 기본 재생, 앨범, 검색 및 컬렉션을 제공합니다.
        Lightweight edition: gallery, video browsing, albums and collections only.\t輕量版：僅提供相簿、影片瀏覽與收藏集功能。\t軽量版：ギャラリー、動画閲覧、アルバム、コレクションのみ利用できます。\t경량판: 갤러리, 동영상 탐색, 앨범 및 컬렉션만 제공합니다.
        Links\t連結\tリンク\t링크
        Links found\t找到連結\tリンクを検出\t링크 발견
        Locked\t已鎖定\tロック済み\t잠김
        Location\t地點\t場所\t위치
        Local smart analysis, secure Vault, editing, QR/URL detection and repair tools are enabled.\t已啟用本機智慧分析、安全保險庫、編輯、QR／網址偵測與修復工具。\t端末内スマート分析、安全な保管庫、編集、QR・URL検出、修復ツールが有効です。\t기기 내 스마트 분석, 보안 보관함, 편집, QR/URL 감지 및 복구 도구가 활성화되었습니다.
        Local smart analysis, secure Vault, native playback, editing, QR/URL detection and repair tools are enabled.\t已啟用本機智慧分析、安全保險庫、原生播放、編輯、QR／網址偵測與修復工具。\t端末内スマート分析、安全な保管庫、ネイティブ再生、編集、QR・URL検出、修復ツールが有効です。\t기기 내 스마트 분석, 보안 보관함, 기본 재생, 편집, QR/URL 감지 및 복구 도구가 활성화되었습니다.
        Long-press a URL or QR code in the image to open it.\t長按圖片中的網址或 QR Code 即可開啟。\t画像内のURLまたはQRコードを長押しすると開けます。\t이미지의 URL 또는 QR 코드를 길게 눌러 여세요.
        Longitude\t經度\t経度\t경도
        MIME type\tMIME 類型\tMIMEタイプ\tMIME 유형
        Manual remove\t手動去背\t背景を手動削除\t배경 수동 제거
        Manual background tolerance %s%%\t手動背景容差 %s%%\t手動背景許容値 %s%%\t수동 배경 허용 오차 %s%%
        Manage\t管理\t管理\t관리
        Manage album\t管理相簿\tアルバムを管理\t앨범 관리
        Media badges\t媒體標記\tメディアバッジ\t미디어 배지
        Media permissions\t媒體權限\tメディア権限\t미디어 권한
        Medium 1–10 MB\t中型（1–10 MB）\t中（1～10 MB）\t중간(1~10 MB)
        Modified\t修改日期\t更新日\t수정일
        Move to trash\t移到垃圾桶\tゴミ箱へ移動\t휴지통으로 이동
        Name\t名稱\t名前\t이름
        Name person\t命名人物\t人物に名前を付ける\t인물 이름 지정
        Name person group\t命名人物群組\t人物グループに名前を付ける\t인물 그룹 이름 지정
        New collection\t新增收藏集\t新しいコレクション\t새 컬렉션
        No media yet\t尚無媒體\tメディアがありません\t미디어가 없습니다
        No web links or QR URLs found\t未找到網頁連結或 QR Code 網址\tウェブリンクまたはQRコードのURLが見つかりません\t웹 링크 또는 QR 코드 URL을 찾지 못했습니다
        On-device smart organization\t裝置端智慧整理\t端末上のスマート整理\t기기 내 스마트 정리
        Optional password\t選用密碼\t任意のパスワード\t선택 비밀번호
        Original\t原始\tオリジナル\t원본
        Original quality\t原始畫質\t元の画質\t원본 화질
        Output name\t輸出名稱\t出力名\t출력 이름
        Open video player\t開啟影片播放器\t動画プレーヤーを開く\t동영상 플레이어 열기
        Password\t密碼\tパスワード\t비밀번호
        People\t人物\t人物\t인물
        Person\t人物\t人物\t인물
        Person %s\t人物 %s\t人物 %s\t인물 %s
        Photos\t相片\t写真\t사진
        Play muted looping video previews in the media grid.\t在媒體格狀清單中靜音循環播放影片預覽。\tメディアグリッドで動画プレビューを無音で繰り返し再生します。\t미디어 격자에서 동영상 미리보기를 음소거 상태로 반복 재생합니다.
        Playback speed\t播放速度\t再生速度\t재생 속도
        Portrait\t直向\t縦長\t세로형
        Preserve date\t保留日期\t日付を保持\t날짜 유지
        Preview\t預覽\tプレビュー\t미리보기
        Protect\t上鎖\tロック\t잠금
        Protect %s\t保護 %s\t%sを保護\t%s 보호
        Protect %s selected items\t保護 %s 個已選項目\t選択した%s件を保護\t선택한 항목 %s개 보호
        Protected targets\t受保護的項目\t保護対象\t보호 대상
        Protected\t已保護\t保護済み\t보호됨
        Quality\t畫質\t画質\t화질
        Quick edits\t快速編輯\tクイック編集\t빠른 편집
        Radius meters\t半徑（公尺）\t半径（メートル）\t반경(미터)
        Record camera audio\t錄製相機音訊\tカメラ音声を録音\t카메라 오디오 녹음
        Reindex image text\t重新建立圖片文字索引\t画像内テキストを再インデックス化\t이미지 텍스트 다시 색인
        Refresh\t重新整理\t更新\t새로고침
        Rename\t重新命名\t名前変更\t이름 바꾸기
        Rename collection\t重新命名收藏集\tコレクション名を変更\t컬렉션 이름 바꾸기
        Rename media\t重新命名媒體\tメディア名を変更\t미디어 이름 바꾸기
        Replace the password required before deleting media.\t更換刪除媒體前必須輸入的密碼。\tメディア削除前に必要なパスワードを変更します。\t미디어 삭제 전에 필요한 비밀번호를 변경합니다.
        Remove\t移除\t削除\t제거
        Remove %s from this collection? The media file stays on your device.\t要從此收藏集移除 %s 嗎？媒體檔案仍會保留在裝置上。\t%sをこのコレクションから外しますか？メディアファイルは端末に残ります。\t이 컬렉션에서 %s을(를) 제거할까요? 미디어 파일은 기기에 유지됩니다.
        Remove from collection\t從收藏集移除\tコレクションから外す\t컬렉션에서 제거
        Remove corner-sampled background\t移除以角落取樣的背景\t隅からサンプリングした背景を削除\t모서리에서 샘플링한 배경 제거
        Repair\t修復\t修復\t복구
        Repair media\t修復媒體\tメディアを修復\t미디어 복구
        Reset view\t重設檢視\t表示をリセット\t보기 초기화
        Restore\t還原\t復元\t복원
        Restore all\t全部還原\tすべて復元\t모두 복원
        Resolution\t解析度\t解像度\t해상도
        Rotate\t旋轉\t回転\t회전
        Rotate 90° right\t向右旋轉 90°\t右へ90度回転\t오른쪽으로 90도 회전
        Rule\t規則\tルール\t규칙
        Rule name\t規則名稱\tルール名\t규칙 이름
        Run analysis to create local person groups.\t執行分析以建立本機人物群組。\t分析を実行して端末内の人物グループを作成します。\t분석을 실행하여 기기 내 인물 그룹을 만듭니다.
        Saturation: %s\t飽和度：%s\t彩度：%s\t채도: %s
        Save\t儲存\t保存\t저장
        Search media and albums\t搜尋媒體與相簿\tメディアとアルバムを検索\t미디어 및 앨범 검색
        Search range\t搜尋範圍\t検索範囲\t검색 범위
        Search text inside images\t搜尋圖片中的文字\t画像内のテキストを検索\t이미지 속 텍스트 검색
        Selected: %s\t已選取：%s\t選択済み：%s\t선택됨: %s
        Selected album\t所選相簿\t選択中のアルバム\t선택한 앨범
        Select all\t全選\tすべて選択\t모두 선택
        Set or replace the password required before deleting media.\t設定或更換刪除媒體前必須輸入的密碼。\tメディア削除前に必要なパスワードを設定または変更します。\t미디어 삭제 전에 필요한 비밀번호를 설정하거나 변경합니다.
        Set password\t設定密碼\tパスワードを設定\t비밀번호 설정
        Set the password required before deleting media.\t設定刪除媒體前必須輸入的密碼。\tメディア削除前に必要なパスワードを設定します。\t미디어 삭제 전에 필요한 비밀번호를 설정합니다.
        Set password for %s\t為 %s 設定密碼\t%sのパスワードを設定\t%s 비밀번호 설정
        Settings\t設定\t設定\t설정
        Share\t分享\t共有\t공유
        Share media\t分享媒體\tメディアを共有\t미디어 공유
        Share selected\t分享已選項目\t選択項目を共有\t선택 항목 공유
        Show a rule-of-thirds grid when KeepG Camera opens.\t開啟 KeepG 相機時顯示三分構圖格線。\tKeepGカメラを開いたときに三分割グリッドを表示します。\tKeepG 카메라를 열 때 삼등분 격자를 표시합니다.
        Show video duration, GIF, favorite, and selection badges on thumbnails.\t在縮圖上顯示影片長度、GIF、最愛與選取標記。\tサムネイルに動画の長さ、GIF、お気に入り、選択バッジを表示します。\t썸네일에 동영상 길이, GIF, 즐겨찾기 및 선택 배지를 표시합니다.
        Size\t大小\tサイズ\t크기
        Small < 1 MB\t小型（小於 1 MB）\t小（1 MB未満）\t작음(1 MB 미만)
        Smart\t智慧\tスマート\t스마트
        Smart albums\t智慧相簿\tスマートアルバム\t스마트 앨범
        Sort\t排序\t並べ替え\t정렬
        Square\t正方形\t正方形\t정사각형
        Start YYYY-MM-DD\t開始日期 YYYY-MM-DD\t開始日 YYYY-MM-DD\t시작일 YYYY-MM-DD
        Swipe between previews\t滑動切換預覽\tスワイプでプレビューを切り替え\t밀어서 미리보기 전환
        Swipe left or right inside the current album or search order. Navigation is disabled while media is zoomed in.\t在目前相簿或搜尋結果順序中左右滑動；放大媒體時會停用切換。\t現在のアルバムまたは検索結果の順序で左右にスワイプします。メディア拡大中は切り替えできません。\t현재 앨범 또는 검색 결과 순서에서 좌우로 미세요. 미디어가 확대된 동안에는 이동할 수 없습니다.
        Text layer\t文字圖層\tテキストレイヤー\t텍스트 레이어
        Text recognition runs locally. Index only media you are comfortable storing as searchable text on this device.\t文字辨識會在裝置上執行。請只為您同意在此裝置上儲存為可搜尋文字的媒體建立索引。\t文字認識は端末内で実行されます。検索可能な文字としてこの端末に保存してもよいメディアだけをインデックス化してください。\t텍스트 인식은 기기에서 실행됩니다. 이 기기에 검색 가능한 텍스트로 저장해도 되는 미디어만 색인하세요.
        The collection will be deleted. Media files stay on your device.\t收藏集將被刪除，媒體檔案仍會保留在裝置上。\tコレクションを削除します。メディアファイルは端末に残ります。\t컬렉션이 삭제됩니다. 미디어 파일은 기기에 유지됩니다.
        The home-screen KeepG widget provides Photos, Albums, Camera, and Vault shortcuts when supported by the launcher.\t啟動器支援時，主畫面的 KeepG 小工具會提供相片、相簿、相機與保險庫捷徑。\tランチャーが対応している場合、ホーム画面のKeepGウィジェットから写真、アルバム、カメラ、保管庫を開けます。\t런처가 지원하면 홈 화면 KeepG 위젯에서 사진, 앨범, 카메라 및 보관함 바로가기를 제공합니다.
        Thumbnail framing\t縮圖顯示方式\tサムネイルの表示方法\t썸네일 표시 방식
        Threshold 0–1\t臨界值 0–1\tしきい値 0～1\t임곗값 0~1
        Time\t時間\t時間\t시간
        Trash\t垃圾桶\tゴミ箱\t휴지통
        Trash is empty\t垃圾桶是空的\tゴミ箱は空です\t휴지통이 비어 있습니다
        Trim first 5 seconds\t保留前 5 秒\t最初の5秒を残す\t처음 5초만 유지
        Trim to first 5 seconds\t裁切為前 5 秒\t最初の5秒にトリミング\t처음 5초로 자르기
        Unlock\t解鎖\tロック解除\t잠금 해제
        Unlock protected media\t解鎖受保護的媒體\t保護されたメディアのロックを解除\t보호된 미디어 잠금 해제
        Unfavorite\t移除最愛\tお気に入り解除\t즐겨찾기 해제
        Use Android MediaStore trash when available instead of immediate permanent deletion.\t可用時使用 Android MediaStore 垃圾桶，而非立即永久刪除。\t利用可能な場合は、すぐに完全削除せずAndroid MediaStoreのゴミ箱を使用します。\t사용 가능한 경우 즉시 영구 삭제하지 않고 Android MediaStore 휴지통을 사용합니다.
        Use current date\t使用目前日期\t現在の日付を使用\t현재 날짜 사용
        Use at least 6 characters\t請使用至少 6 個字元\t6文字以上を使用してください\t6자 이상 사용하세요
        Use Vault copy from a media item to create an AES-GCM encrypted private copy.\t從媒體項目使用「複製到保險庫」，建立 AES-GCM 加密的私人副本。\tメディア項目の「保管庫へコピー」を使うと、AES-GCMで暗号化された非公開コピーを作成できます。\t미디어 항목의 보관함 복사를 사용하여 AES-GCM으로 암호화된 비공개 복사본을 만드세요.
        Uses an on-device visual heuristic. It can make mistakes; hidden media is never deleted.\t使用裝置端視覺演算法，可能誤判；隱藏媒體不會被刪除。\t端末上の画像判定を使用します。誤判定する場合がありますが、非表示のメディアは削除されません。\t기기 내 시각적 추정을 사용합니다. 잘못 판단할 수 있지만 숨긴 미디어는 삭제되지 않습니다.
        Vault\t保險庫\t保管庫\t보관함
        Vault is empty\t保險庫是空的\t保管庫は空です\t보관함이 비어 있습니다
        Video\t影片\t動画\t동영상
        Video editor\t影片編輯器\t動画エディター\t동영상 편집기
        Video preview autoplay\t影片預覽自動播放\t動画プレビューを自動再生\t동영상 미리보기 자동 재생
        Videos\t影片\t動画\t동영상
        View log\t查看日誌\tログを見る\t로그 보기
        Write a rotating app-private KeepG log. Error lines are still sent to Logcat when disabled.\t寫入會輪替的 KeepG 應用程式私人日誌。停用時，錯誤記錄仍會傳送到 Logcat。\tローテーションするKeepGのアプリ専用ログを書き込みます。無効時もエラー行はLogcatへ送信されます。\t순환되는 KeepG 앱 전용 로그를 작성합니다. 비활성화해도 오류 줄은 Logcat으로 전송됩니다.
        %s images indexed on this device\t此裝置已為 %s 張圖片建立索引\tこの端末で%s件の画像をインデックス化済み\t이 기기에서 이미지 %s개가 색인됨
        Created edited copy\t已建立編輯後的副本\t編集済みコピーを作成しました\t편집된 복사본 생성됨
        Focus unavailable: %s\t無法對焦：%s\tフォーカスを使用できません：%s\t초점을 사용할 수 없음: %s
        Media captured\t媒體拍攝完成\tメディアを撮影しました\t미디어 촬영됨
        Microphone permission denied; recording without audio\t麥克風權限遭拒，將以無聲方式錄影\tマイク権限が拒否されたため、音声なしで録画します\t마이크 권한이 거부되어 오디오 없이 녹화합니다
        Microphone permission unavailable; recording without audio\t無法取得麥克風權限，將以無聲方式錄影\tマイク権限を利用できないため、音声なしで録画します\t마이크 권한을 사용할 수 없어 오디오 없이 녹화합니다
        No browser could open this link\t沒有瀏覽器可以開啟此連結\tこのリンクを開けるブラウザがありません\t이 링크를 열 수 있는 브라우저가 없습니다
        No compatible share target is available\t沒有可用的相容分享目標\t対応する共有先がありません\t호환되는 공유 대상이 없습니다
        Photo camera is not ready\t相片相機尚未就緒\t写真カメラの準備ができていません\t사진 카메라가 준비되지 않았습니다
        Protect selected media\t保護所選媒體\t選択したメディアを保護\t선택한 미디어 보호
        Recording\t錄影中\t録画中\t녹화 중
        Recording control failed: %s\t錄影控制失敗：%s\t録画操作に失敗しました：%s\t녹화 제어 실패: %s
        Recording could not start: %s\t無法開始錄影：%s\t録画を開始できませんでした：%s\t녹화를 시작할 수 없음: %s
        Recording ended early; partial video saved\t錄影提前結束，已儲存部分影片\t録画が途中で終了し、部分的な動画を保存しました\t녹화가 일찍 종료되어 일부 동영상을 저장했습니다
        Recording failed: %s\t錄影失敗：%s\t録画に失敗しました：%s\t녹화 실패: %s
        Saved %s\t已儲存 %s\t%sを保存しました\t%s 저장됨
        Stored %s/%s encrypted Vault copies\t已儲存 %s／%s 個加密保險庫副本\t暗号化された保管庫コピーを%s／%s件保存しました\t암호화된 보관함 복사본 %s/%s개 저장됨
        Torch unavailable: %s\t閃光燈無法使用：%s\tライトを使用できません：%s\t손전등을 사용할 수 없음: %s
        Unable to stop recording: %s\t無法停止錄影：%s\t録画を停止できません：%s\t녹화를 중지할 수 없음: %s
        Video camera is not ready\t錄影相機尚未就緒\t動画カメラの準備ができていません\t동영상 카메라가 준비되지 않았습니다
        Video edit failed\t影片編輯失敗\t動画の編集に失敗しました\t동영상 편집 실패
        error %s\t錯誤 %s\tエラー %s\t오류 %s
        %s-second timer\t%s 秒定時器\t%s秒タイマー\t%s초 타이머
        Camera permission is required to use the built-in camera.\t使用內建相機需要相機權限。\t内蔵カメラを使用するにはカメラ権限が必要です。\t내장 카메라를 사용하려면 카메라 권한이 필요합니다.
        Close camera\t關閉相機\tカメラを閉じる\t카메라 닫기
        Composition grid\t構圖格線\t構図グリッド\t구도 격자
        Grant camera access\t授予相機權限\tカメラへのアクセスを許可\t카메라 접근 허용
        KeepG creates a recovered copy instead of destructively rewriting the original.\tKeepG 會建立復原副本，不會破壞性地覆寫原始檔。\tKeepGは元のファイルを破壊的に書き換えず、復元済みコピーを作成します。\tKeepG는 원본을 파괴적으로 덮어쓰지 않고 복구된 복사본을 만듭니다.
        Next media\t下一個媒體\t次のメディア\t다음 미디어
        Paused · %s\t已暫停 · %s\t一時停止 · %s\t일시중지 · %s
        Photo\t相片\t写真\t사진
        Photo flash\t相片閃光燈\t写真フラッシュ\t사진 플래시
        Previous media\t上一個媒體\t前のメディア\t이전 미디어
        private • organized • on-device\t私密 • 有序 • 裝置端\tプライベート • 整理 • 端末内\t비공개 • 정리 • 기기 내
        Quality %s\t畫質 %s\t画質 %s\t화질 %s
        Remove audio\t移除音訊\t音声を削除\t오디오 제거
        Reset zoom\t重設縮放\tズームをリセット\t확대/축소 초기화
        Start recording\t開始錄影\t録画を開始\t녹화 시작
        Starting video…\t正在開始錄影…\t動画を開始しています…\t동영상 시작 중…
        Stop recording\t停止錄影\t録画を停止\t녹화 중지
        Take photo\t拍照\t写真を撮る\t사진 촬영
        Timer off\t關閉定時器\tタイマーなし\t타이머 끄기
        Torch\t補光燈\tライト\t손전등
        Trim by dragging the range handles. KeepG writes a new MP4 and leaves the source untouched.\t拖移範圍控制點進行裁切。KeepG 會建立新的 MP4，原始檔保持不變。\t範囲ハンドルをドラッグしてトリミングします。KeepGは新しいMP4を作成し、元のファイルは変更しません。\t범위 핸들을 끌어 자르세요. KeepG는 새 MP4를 만들고 원본은 변경하지 않습니다.
        smiling\t微笑\t笑顔\t미소
        neutral\t自然表情\t自然な表情\t자연스러운 표정
        eyes closed\t閉眼\t目を閉じている\t눈 감음
        4:3\t4:3\t4:3\t4:3
        16:9\t16:9\t16:9\t16:9
        """
    )

    val supportedKeys: Set<String>
        get() = dictionaries.getValue(ENGLISH).keys

    fun translationKeys(language: AppLanguage): Set<String> = dictionaries[languageCode(language)].orEmpty().keys

    fun missingKeys(language: AppLanguage): Set<String> = supportedKeys - translationKeys(language)

    fun text(language: AppLanguage, key: String): String =
        dictionaries[languageCode(language)]?.get(key) ?: dictionaries[ENGLISH]?.get(key) ?: key

    fun message(language: AppLanguage, raw: String): String {
        if (raw in supportedKeys) return text(language, raw)
        fun formatted(key: String, vararg args: Any): String = String.format(Locale.ROOT, text(language, key), *args)
        fun match(pattern: String): MatchResult? = Regex(pattern).matchEntire(raw)

        match("^Indexed (\\d+)/(\\d+) images$")?.let { return formatted("Indexed %s/%s images", it.groupValues[1], it.groupValues[2]) }
        match("^Protected (\\d+) items$")?.let { return formatted("Protected %s items", it.groupValues[1]) }
        match("^Removed (\\d+) item\\(s\\)$")?.let { return formatted("Removed %s item(s)", it.groupValues[1]) }
        match("^Added (\\d+) items to collection$")?.let { return formatted("Added %s items to collection", it.groupValues[1]) }
        match("^Analyzing (\\d+)/(\\d+): (.*)$")?.let { return formatted("Analyzing %s/%s: %s", it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        match("^Smart analysis complete: (\\d+) faces processed$")?.let { return formatted("Smart analysis complete: %s faces processed", it.groupValues[1]) }
        match("^Recovered copy created: (.*?) · MIME (.*?) · date (.*)$")?.let {
            return formatted("Recovered copy created: %s · MIME %s · date %s", it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        match("^Repair could not recover this file: (.*)$")?.let { return formatted("Repair could not recover this file: %s", it.groupValues[1]) }
        match("^(.*) is unavailable on this Android version$")?.let {
            return formatted("%s is unavailable on this Android version", text(language, it.groupValues[1]))
        }
        return raw
    }

    private fun languageCode(language: AppLanguage): String = when (language) {
        AppLanguage.AUTO -> when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "zh" -> "zh"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> ENGLISH
        }
        AppLanguage.ENGLISH -> ENGLISH
        AppLanguage.CHINESE -> "zh"
        AppLanguage.JAPANESE -> "ja"
        AppLanguage.KOREAN -> "ko"
    }

    private fun parseBundle(source: String): Map<String, Map<String, String>> {
        val result: MutableMap<String, MutableMap<String, String>> = linkedMapOf(
            ENGLISH to linkedMapOf(),
            "zh" to linkedMapOf(),
            "ja" to linkedMapOf(),
            "ko" to linkedMapOf(),
        )
        source.trimIndent().lineSequence().filter(String::isNotBlank).forEach { line ->
            val parts = line.split("\\t")
            require(parts.size == 4) { "Invalid translation entry: $line" }
            require(parts.none(String::isBlank)) { "Blank translation entry: $line" }
            require(parts[0] !in result.getValue(ENGLISH)) { "Duplicate translation key: ${parts[0]}" }
            result.getValue(ENGLISH)[parts[0]] = parts[0]
            result.getValue("zh")[parts[0]] = parts[1]
            result.getValue("ja")[parts[0]] = parts[2]
            result.getValue("ko")[parts[0]] = parts[3]
        }
        return result
    }
}
