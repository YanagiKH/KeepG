package com.yanagikh.keepg.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Surface as AndroidSurface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private enum class CameraCaptureMode { PHOTO, VIDEO }

private enum class CameraVideoQuality(val label: String, val quality: Quality) {
    UHD("4K", Quality.UHD),
    FHD("1080p", Quality.FHD),
    HD("720p", Quality.HD),
    SD("480p", Quality.SD),
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepGCameraScreen(
    onClose: () -> Unit,
    onCaptured: () -> Unit,
    initialGridEnabled: Boolean,
    recordAudioEnabled: Boolean,
    onGridEnabledChange: (Boolean) -> Unit,
    onRecordAudioEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    fun localized(key: String): String = UiLocalizer.text(appLanguage, key)
    fun localizedFormat(key: String, vararg args: Any): String = String.format(Locale.ROOT, localized(key), *args)
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    val screenAlive = remember { AtomicBoolean(true) }

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var waitingForAudioPermission by remember { mutableStateOf(false) }
    var pendingVideoStart by remember { mutableStateOf(false) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    var captureModeName by rememberSaveable { mutableStateOf(CameraCaptureMode.PHOTO.name) }
    val captureMode = CameraCaptureMode.entries.firstOrNull { it.name == captureModeName } ?: CameraCaptureMode.PHOTO
    var videoQualityName by rememberSaveable { mutableStateOf(CameraVideoQuality.FHD.name) }
    val selectedVideoQuality = CameraVideoQuality.entries.firstOrNull { it.name == videoQualityName } ?: CameraVideoQuality.FHD
    var supportedVideoQualities by remember { mutableStateOf(CameraVideoQuality.entries.toList()) }
    var showVideoQualityMenu by remember { mutableStateOf(false) }

    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var qualityMode by rememberSaveable { mutableStateOf(true) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    var gridEnabled by rememberSaveable(initialGridEnabled) { mutableStateOf(initialGridEnabled) }
    var zoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposureIndex by rememberSaveable { mutableIntStateOf(0) }
    var minExposure by remember { mutableIntStateOf(0) }
    var maxExposure by remember { mutableIntStateOf(0) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var photoCaptureInProgress by remember { mutableStateOf(false) }
    var recordingStarting by remember { mutableStateOf(false) }
    var recordingStopping by remember { mutableStateOf(false) }
    var recordingPaused by remember { mutableStateOf(false) }
    var recordedDurationNanos by remember { mutableLongStateOf(0L) }

    fun reportCameraError(action: String, userMessageKey: String, error: Throwable?) {
        if (error == null) Log.e("KeepGCamera", action) else Log.e("KeepGCamera", action, error)
        if (screenAlive.get()) {
            statusMessage = localizedFormat(userMessageKey, localized("Operation failed"))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) cameraResult@{ granted ->
        if (!screenAlive.get()) return@cameraResult
        permissionGranted = granted
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) audioResult@{ granted ->
        if (!screenAlive.get()) return@audioResult
        audioPermissionGranted = granted
        val shouldStart = waitingForAudioPermission
        waitingForAudioPermission = false
        if (!granted) {
            onRecordAudioEnabledChange(false)
            if (screenAlive.get()) statusMessage = localized("Microphone permission denied; recording without audio")
        }
        if (shouldStart && screenAlive.get()) pendingVideoStart = true else if (shouldStart) recordingStarting = false
    }

    fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
    }

    fun setGrid(value: Boolean) {
        gridEnabled = value
        onGridEnabledChange(value)
    }

    fun stopActiveRecording(showStoppingState: Boolean) {
        val recording = activeRecording ?: return
        if (recordingStopping) return
        if (showStoppingState && screenAlive.get()) recordingStopping = true
        runCatching { recording.stop() }.onFailure { error ->
            runCatching { recording.close() }
            if (activeRecording === recording) activeRecording = null
            recordingStopping = false
            recordingPaused = false
            reportCameraError("Unable to stop recording", "Unable to stop recording: %s", error)
        }
    }

    fun pauseOrResumeRecording() {
        val recording = activeRecording ?: return
        runCatching {
            if (recordingPaused) recording.resume() else recording.pause()
        }.onFailure { error ->
            reportCameraError("Recording control failed", "Recording control failed: %s", error)
        }
    }

    fun beginVideoRecording() {
        if (!screenAlive.get() || captureMode != CameraCaptureMode.VIDEO || activeRecording != null) {
            recordingStarting = false
            return
        }
        val capture = videoCapture
        if (capture == null) {
            recordingStarting = false
            statusMessage = localized("Video camera is not ready")
            return
        }

        val name = "KeepG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.mp4"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG")
            }
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ).setContentValues(values).build()
            capture.targetRotation = previewView?.display?.rotation ?: AndroidSurface.ROTATION_0
            var pendingRecording = capture.output.prepareRecording(context, outputOptions)
            if (recordAudioEnabled && audioPermissionGranted) pendingRecording = pendingRecording.withAudioEnabled()

            var startedRecording: Recording? = null
            val recording = pendingRecording.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        if (screenAlive.get()) {
                            recordingStarting = false
                            recordingPaused = false
                            recordedDurationNanos = 0L
                            statusMessage = localized("Recording")
                        }
                    }
                    is VideoRecordEvent.Status -> {
                        if (screenAlive.get() && activeRecording === startedRecording) {
                            recordedDurationNanos = event.recordingStats.recordedDurationNanos.coerceAtLeast(0L)
                        }
                    }
                    is VideoRecordEvent.Pause -> {
                        if (screenAlive.get() && activeRecording === startedRecording) recordingPaused = true
                    }
                    is VideoRecordEvent.Resume -> {
                        if (screenAlive.get() && activeRecording === startedRecording) recordingPaused = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        val outputUri = event.outputResults.outputUri
                        runCatching { startedRecording?.close() }
                        if (event.hasError()) {
                            event.cause?.let { Log.e("KeepGCamera", "Recording failed with error ${event.error}", it) }
                                ?: Log.e("KeepGCamera", "Recording failed with error ${event.error}")
                            val keepPartialOutput = outputUri != Uri.EMPTY &&
                                event.recordingStats.recordedDurationNanos > 0L &&
                                event.error in setOf(
                                    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                                    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                                    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                                    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
                                )
                            if (!keepPartialOutput && outputUri != Uri.EMPTY) {
                                runCatching { context.contentResolver.delete(outputUri, null, null) }
                            }
                            if (screenAlive.get()) {
                                statusMessage = if (keepPartialOutput) {
                                    localized("Recording ended early; partial video saved")
                                } else {
                                    localizedFormat("Recording failed: %s", localizedFormat("error %s", event.error))
                                }
                            }
                            if (keepPartialOutput) runCatching { onCaptured() }
                        } else {
                            if (screenAlive.get()) statusMessage = localizedFormat("Saved %s", name)
                            runCatching { onCaptured() }
                        }
                        if (activeRecording === startedRecording) activeRecording = null
                        if (screenAlive.get()) {
                            recordingStarting = false
                            recordingStopping = false
                            recordingPaused = false
                            recordedDurationNanos = 0L
                        }
                    }
                    else -> Unit
                }
            }
            startedRecording = recording
            activeRecording = recording
            recordingStarting = false
            recordingStopping = false
            recordingPaused = false
            recordedDurationNanos = 0L
        } catch (error: Throwable) {
            recordingStarting = false
            recordingStopping = false
            reportCameraError("Recording could not start", "Recording could not start: %s", error)
        }
    }

    fun requestVideoRecording() {
        if (recordingStarting || activeRecording != null || countdown > 0) return
        recordingStarting = true
        scope.launch {
            countdown = timerSeconds
            while (countdown > 0 && screenAlive.get()) {
                delay(1_000)
                countdown--
            }
            if (!screenAlive.get() || captureMode != CameraCaptureMode.VIDEO) {
                recordingStarting = false
                return@launch
            }
            if (recordAudioEnabled && !audioPermissionGranted) {
                waitingForAudioPermission = true
                runCatching { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    .onFailure {
                        waitingForAudioPermission = false
                        onRecordAudioEnabledChange(false)
                        statusMessage = localized("Microphone permission unavailable; recording without audio")
                        pendingVideoStart = true
                    }
            } else {
                beginVideoRecording()
            }
        }
    }

    fun takePhoto() {
        if (photoCaptureInProgress || countdown > 0) return
        photoCaptureInProgress = true
        scope.launch {
            countdown = timerSeconds
            while (countdown > 0 && screenAlive.get()) {
                delay(1_000)
                countdown--
            }
            if (!screenAlive.get() || captureMode != CameraCaptureMode.PHOTO) {
                photoCaptureInProgress = false
                return@launch
            }
            val capture = imageCapture
            if (capture == null) {
                photoCaptureInProgress = false
                statusMessage = localized("Photo camera is not ready")
                return@launch
            }
            val name = "KeepG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeepG")
                }
                val output = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ).build()
                capture.targetRotation = previewView?.display?.rotation ?: AndroidSurface.ROTATION_0
                capture.flashMode = flashMode
                capture.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (screenAlive.get()) {
                            photoCaptureInProgress = false
                            statusMessage = localizedFormat("Saved %s", name)
                        }
                        runCatching { onCaptured() }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (screenAlive.get()) {
                            photoCaptureInProgress = false
                            reportCameraError("Photo capture failed (${exception.imageCaptureError})", "Capture failed: %s", exception)
                        }
                    }
                })
            }.onFailure { error ->
                if (screenAlive.get()) {
                    photoCaptureInProgress = false
                    reportCameraError("Photo capture failed", "Capture failed: %s", error)
                }
            }
        }
    }

    fun closeCamera() {
        screenAlive.set(false)
        val recording = activeRecording
        activeRecording = null
        if (recording != null) {
            runCatching { recording.stop() }.onFailure { runCatching { recording.close() } }
        }
        onClose()
    }

    BackHandler(onBack = ::closeCamera)

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            runCatching { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }.onFailure { error ->
                reportCameraError("Camera permission request failed", "Camera permission request failed: %s", error)
            }
        }
    }

    LaunchedEffect(pendingVideoStart, videoCapture, captureMode) {
        if (pendingVideoStart) {
            pendingVideoStart = false
            beginVideoRecording()
        }
    }

    DisposableEffect(Unit) {
        screenAlive.set(true)
        onDispose {
            screenAlive.set(false)
            val recording = activeRecording
            activeRecording = null
            if (recording != null) {
                runCatching { recording.stop() }.onFailure { runCatching { recording.close() } }
            }
        }
    }

    DisposableEffect(permissionGranted, previewView, lensFacing, qualityMode, captureMode, selectedVideoQuality, lifecycleOwner) {
        val view = previewView
        if (!permissionGranted || view == null) return@DisposableEffect onDispose { }

        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        var boundUseCases: Array<UseCase> = emptyArray()
        val providerFuture = try {
            ProcessCameraProvider.getInstance(context)
        } catch (error: Throwable) {
            reportCameraError("Camera initialization failed", "Camera initialization failed: %s", error)
            return@DisposableEffect onDispose { }
        }
        providerFuture.addListener({
            if (disposed || !screenAlive.get()) return@addListener
            runCatching {
                val provider = providerFuture.get()
                if (disposed || !screenAlive.get()) return@runCatching
                boundProvider = provider
                provider.unbindAll()
                camera = null
                imageCapture = null
                videoCapture = null

                val rotation = view.display?.rotation ?: AndroidSurface.ROTATION_0
                val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = view.surfaceProvider }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                require(provider.hasCamera(selector)) { "Selected camera is not available" }

                val boundCamera = when (captureMode) {
                    CameraCaptureMode.PHOTO -> {
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(
                                if (qualityMode) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                                else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
                            )
                            .setFlashMode(flashMode)
                            .setTargetRotation(rotation)
                            .build()
                        boundUseCases = arrayOf(preview, capture)
                        imageCapture = capture
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }
                    CameraCaptureMode.VIDEO -> {
                        val fallback = FallbackStrategy.lowerQualityOrHigherThan(selectedVideoQuality.quality)
                        val qualitySelector = QualitySelector.from(selectedVideoQuality.quality, fallback)
                        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                        val capture = VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
                        boundUseCases = arrayOf(preview, capture)
                        videoCapture = capture
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }
                }

                if (disposed || !screenAlive.get()) {
                    provider.unbind(*boundUseCases)
                    return@runCatching
                }
                camera = boundCamera

                if (captureMode == CameraCaptureMode.VIDEO) {
                    val supported = Recorder.getVideoCapabilities(boundCamera.cameraInfo)
                        .getSupportedQualities(DynamicRange.SDR)
                    val mapped = CameraVideoQuality.entries.filter { it.quality in supported }
                    if (mapped.isNotEmpty()) {
                        supportedVideoQualities = mapped
                        if (selectedVideoQuality !in mapped) videoQualityName = mapped.first().name
                    }
                }

                boundCamera.cameraInfo.zoomState.value?.let { state ->
                    minZoom = state.minZoomRatio
                    maxZoom = state.maxZoomRatio.coerceAtLeast(state.minZoomRatio)
                    zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
                    boundCamera.cameraControl.setZoomRatio(zoomRatio)
                }
                boundCamera.cameraInfo.exposureState.let { state ->
                    minExposure = state.exposureCompensationRange.lower
                    maxExposure = state.exposureCompensationRange.upper
                    exposureIndex = exposureIndex.coerceIn(minExposure, maxExposure)
                    if (state.isExposureCompensationSupported) {
                        boundCamera.cameraControl.setExposureCompensationIndex(exposureIndex)
                    }
                }
                if (boundCamera.cameraInfo.hasFlashUnit()) {
                    boundCamera.cameraControl.enableTorch(torchEnabled)
                } else {
                    torchEnabled = false
                }
            }.onFailure { error ->
                val provider = boundProvider
                if (provider != null && boundUseCases.isNotEmpty()) {
                    runCatching { provider.unbind(*boundUseCases) }
                }
                if (!disposed && screenAlive.get()) {
                    camera = null
                    imageCapture = null
                    videoCapture = null
                    reportCameraError("Camera unavailable", "Camera unavailable: %s", error)
                }
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            val recording = activeRecording
            activeRecording = null
            if (recording != null) {
                runCatching { recording.stop() }.onFailure { runCatching { recording.close() } }
            }
            camera = null
            imageCapture = null
            videoCapture = null
            val provider = boundProvider
            if (provider != null && boundUseCases.isNotEmpty()) runCatching { provider.unbind(*boundUseCases) }
        }
    }

    if (!permissionGranted) {
        Scaffold(topBar = { TopAppBar(title = { Text(tr("KeepG Camera")) }, navigationIcon = { IconButton(::closeCamera) { Icon(Icons.Default.Close, tr("Close")) } }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(tr("Camera permission is required to use the built-in camera."))
                Spacer(Modifier.height(12.dp))
                Button({
                    runCatching { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }.onFailure { error ->
                        reportCameraError("Camera permission request failed", "Camera permission request failed: %s", error)
                    }
                }) { Text(tr("Grant camera access")) }
                statusMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    val controlsLocked = activeRecording != null || recordingStarting || recordingStopping || countdown > 0 || photoCaptureInProgress
    val hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("KeepG Camera"))
                        if (activeRecording != null || recordingStarting) {
                            Text(
                                if (recordingStarting) tr("Starting video…") else formatRecordingDuration(recordedDurationNanos),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(::closeCamera) { Icon(Icons.Default.Close, tr("Close camera")) } },
                actions = {
                    IconButton({ setGrid(!gridEnabled) }) { Icon(Icons.Default.GridOn, tr("Composition grid")) }
                    IconButton(
                        onClick = {
                            torchEnabled = !torchEnabled
                            runCatching { camera?.cameraControl?.enableTorch(torchEnabled) }.onFailure { error ->
                                torchEnabled = false
                                reportCameraError("Torch unavailable", "Torch unavailable: %s", error)
                            }
                        },
                        enabled = hasFlashUnit,
                    ) { Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, tr("Torch")) }
                    if (captureMode == CameraCaptureMode.PHOTO) {
                        IconButton(::cycleFlash, enabled = !controlsLocked && hasFlashUnit) {
                            Icon(
                                when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                    else -> Icons.Default.FlashOff
                                },
                                tr("Photo flash"),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(camera, previewView) {
                    detectTapGestures { point ->
                        val currentCamera = camera ?: return@detectTapGestures
                        val view = previewView ?: return@detectTapGestures
                        val meteringPoint = view.meteringPointFactory.createPoint(point.x, point.y)
                        val action = FocusMeteringAction.Builder(meteringPoint)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        runCatching { currentCamera.cameraControl.startFocusAndMetering(action) }.onFailure { error ->
                            reportCameraError("Focus unavailable", "Focus unavailable: %s", error)
                        }
                    }
                },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            previewView = this
                        }
                    },
                    update = { previewView = it },
                )
                if (gridEnabled) CameraThirdsGrid(Modifier.fillMaxSize())
                if (countdown > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                    ) { Text(countdown.toString(), Modifier.padding(24.dp), style = MaterialTheme.typography.displayMedium) }
                }
                if (activeRecording != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error.copy(alpha = .90f),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (recordingPaused) Icons.Default.Pause else Icons.Default.FiberManualRecord, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (recordingPaused) trf("Paused · %s", formatRecordingDuration(recordedDurationNanos)) else formatRecordingDuration(recordedDurationNanos),
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                statusMessage?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .84f),
                    ) { Text(message, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }

            Surface(tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(
                            selected = captureMode == CameraCaptureMode.PHOTO,
                            onClick = { captureModeName = CameraCaptureMode.PHOTO.name },
                            label = { Text(tr("Photo")) },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            enabled = !controlsLocked,
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = captureMode == CameraCaptureMode.VIDEO,
                            onClick = { captureModeName = CameraCaptureMode.VIDEO.name },
                            label = { Text(tr("Video")) },
                            leadingIcon = { Icon(Icons.Default.Videocam, null) },
                            enabled = !controlsLocked,
                        )
                    }
                    if (maxZoom > minZoom) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomIn, null)
                            Slider(
                                value = zoomRatio.coerceIn(minZoom, maxZoom),
                                onValueChange = { value ->
                                    zoomRatio = value
                                    runCatching { camera?.cameraControl?.setZoomRatio(value) }
                                },
                                valueRange = minZoom..maxZoom,
                                modifier = Modifier.weight(1f),
                            )
                            Text(String.format(Locale.US, "%.1f×", zoomRatio), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (minExposure < maxExposure) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Exposure, null)
                            Slider(
                                value = exposureIndex.toFloat(),
                                onValueChange = { value ->
                                    exposureIndex = value.toInt().coerceIn(minExposure, maxExposure)
                                    runCatching { camera?.cameraControl?.setExposureCompensationIndex(exposureIndex) }
                                },
                                valueRange = minExposure.toFloat()..maxExposure.toFloat(),
                                steps = (maxExposure - minExposure - 1).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                            )
                            Text("EV $exposureIndex", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (captureMode == CameraCaptureMode.PHOTO) {
                            FilterChip(
                                selected = qualityMode,
                                onClick = { qualityMode = !qualityMode },
                                label = { Text(tr(if (qualityMode) "Max quality" else "Fast capture")) },
                                leadingIcon = { Icon(Icons.Default.HighQuality, null) },
                                enabled = !controlsLocked,
                            )
                        } else {
                            Box {
                                AssistChip(
                                    onClick = { showVideoQualityMenu = true },
                                    label = { Text(trf("Quality %s", selectedVideoQuality.label)) },
                                    leadingIcon = { Icon(Icons.Default.HighQuality, null) },
                                    enabled = !controlsLocked,
                                )
                                DropdownMenu(showVideoQualityMenu, { showVideoQualityMenu = false }) {
                                    supportedVideoQualities.forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality.label) },
                                            onClick = {
                                                videoQualityName = quality.name
                                                showVideoQualityMenu = false
                                            },
                                            leadingIcon = { if (quality == selectedVideoQuality) Icon(Icons.Default.Check, null) },
                                        )
                                    }
                                }
                            }
                            FilterChip(
                                selected = recordAudioEnabled,
                                onClick = { onRecordAudioEnabledChange(!recordAudioEnabled) },
                                label = { Text(tr(if (recordAudioEnabled) "Audio on" else "Audio off")) },
                                leadingIcon = { Icon(if (recordAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff, null) },
                                enabled = !controlsLocked,
                            )
                        }
                        FilterChip(
                            selected = timerSeconds != 0,
                            onClick = { timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 } },
                            label = { Text(if (timerSeconds == 0) tr("Timer off") else trf("%s-second timer", timerSeconds)) },
                            leadingIcon = { Icon(Icons.Default.Timer, null) },
                            enabled = !controlsLocked,
                        )
                        AssistChip(
                            onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                            label = { Text(tr(if (lensFacing == CameraSelector.LENS_FACING_BACK) "Rear camera" else "Front camera")) },
                            leadingIcon = { Icon(Icons.Default.Cameraswitch, null) },
                            enabled = !controlsLocked,
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (captureMode == CameraCaptureMode.PHOTO) {
                            FilledIconButton(
                                onClick = ::takePhoto,
                                enabled = imageCapture != null && !controlsLocked,
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                            ) { Icon(Icons.Default.Camera, tr("Take photo"), Modifier.size(36.dp)) }
                        } else if (activeRecording == null) {
                            FilledIconButton(
                                onClick = ::requestVideoRecording,
                                enabled = videoCapture != null && !controlsLocked,
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Icon(Icons.Default.FiberManualRecord, tr("Start recording"), Modifier.size(38.dp)) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = ::pauseOrResumeRecording,
                                    enabled = !recordingStopping,
                                    modifier = Modifier.size(54.dp),
                                ) { Icon(if (recordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause, tr(if (recordingPaused) "Resume recording" else "Pause recording")) }
                                FilledIconButton(
                                    onClick = { stopActiveRecording(showStoppingState = true) },
                                    enabled = !recordingStopping,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) { Icon(Icons.Default.Stop, tr("Stop recording"), Modifier.size(36.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecordingDuration(durationNanos: Long): String {
    val totalSeconds = durationNanos.coerceAtLeast(0L) / 1_000_000_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun CameraThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = .45f)
        val oneThirdW = size.width / 3f
        val oneThirdH = size.height / 3f
        drawLine(color, Offset(oneThirdW, 0f), Offset(oneThirdW, size.height), 1f)
        drawLine(color, Offset(oneThirdW * 2f, 0f), Offset(oneThirdW * 2f, size.height), 1f)
        drawLine(color, Offset(0f, oneThirdH), Offset(size.width, oneThirdH), 1f)
        drawLine(color, Offset(0f, oneThirdH * 2f), Offset(size.width, oneThirdH * 2f), 1f)
    }
}
