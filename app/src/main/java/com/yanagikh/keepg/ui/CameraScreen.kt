package com.yanagikh.keepg.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepGCameraScreen(
    onClose: () -> Unit,
    onCaptured: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var qualityMode by rememberSaveable { mutableStateOf(true) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    var gridEnabled by rememberSaveable { mutableStateOf(true) }
    var zoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var exposureIndex by rememberSaveable { mutableIntStateOf(0) }
    var minExposure by remember { mutableIntStateOf(0) }
    var maxExposure by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(permissionGranted, previewView, lensFacing, qualityMode, lifecycleOwner) {
        val view = previewView
        if (!permissionGranted || view == null) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        if (qualityMode) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
                    )
                    .setFlashMode(flashMode)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                camera = boundCamera
                imageCapture = capture
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
                if (torchEnabled && boundCamera.cameraInfo.hasFlashUnit()) {
                    boundCamera.cameraControl.enableTorch(true)
                }
            }.onFailure { statusMessage = "Camera unavailable: ${it.message ?: it::class.java.simpleName}" }
        }, mainExecutor)
        onDispose {
            camera = null
            imageCapture = null
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        scope.launch {
            countdown = timerSeconds
            while (countdown > 0) {
                delay(1_000)
                countdown--
            }
            val name = "KeepG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeepG")
            }
            val output = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ).build()
            capture.flashMode = flashMode
            capture.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    statusMessage = "Saved $name"
                    onCaptured()
                }

                override fun onError(exception: ImageCaptureException) {
                    statusMessage = "Capture failed: ${exception.message ?: exception.imageCaptureError}"
                }
            })
        }
    }

    if (!permissionGranted) {
        Scaffold(topBar = { TopAppBar(title = { Text("KeepG Camera") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Default.Close, "Close") } }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Camera permission is required to use the built-in camera.")
                Spacer(Modifier.height(12.dp))
                Button({ permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera access") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KeepG Camera") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Default.Close, "Close camera") } },
                actions = {
                    IconButton({ gridEnabled = !gridEnabled }) { Icon(Icons.Default.GridOn, "Composition grid") }
                    IconButton({
                        torchEnabled = !torchEnabled
                        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torchEnabled)
                    }) { Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch") }
                    IconButton(::cycleFlash) {
                        Icon(
                            when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            },
                            "Photo flash",
                        )
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
                        currentCamera.cameraControl.startFocusAndMetering(action)
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
                    if (maxZoom > minZoom) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomIn, null)
                            Slider(
                                value = zoomRatio.coerceIn(minZoom, maxZoom),
                                onValueChange = { value -> zoomRatio = value; camera?.cameraControl?.setZoomRatio(value) },
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
                                    camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
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
                        FilterChip(qualityMode, { qualityMode = !qualityMode }, { Text(if (qualityMode) "Max quality" else "Fast capture") }, leadingIcon = { Icon(Icons.Default.HighQuality, null) })
                        FilterChip(timerSeconds != 0, { timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 } }, { Text(if (timerSeconds == 0) "Timer off" else "${timerSeconds}s timer") }, leadingIcon = { Icon(Icons.Default.Timer, null) })
                        AssistChip(
                            onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                            label = { Text(if (lensFacing == CameraSelector.LENS_FACING_BACK) "Rear camera" else "Front camera") },
                            leadingIcon = { Icon(Icons.Default.Cameraswitch, null) },
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = ::takePhoto,
                            enabled = imageCapture != null && countdown == 0,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                        ) { Icon(Icons.Default.Camera, "Take photo", Modifier.size(36.dp)) }
                    }
                }
            }
        }
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
