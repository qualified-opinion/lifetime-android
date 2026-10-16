package app.lifetime.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lifetime.LifetimeApp
import app.lifetime.data.MediaType
import app.lifetime.ui.components.DarkSystemBars
import app.lifetime.ui.components.Formatting
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Videos are meant to be short moments, not films. */
private const val MAX_VIDEO_MS = 60_000L

private val RecordRed = Color(0xFFE5484D)

@Composable
fun CameraScreen(onClose: () -> Unit, onOpenMoment: (Long) -> Unit) {
    DarkSystemBars()
    val context = LocalContext.current
    val app = context.applicationContext as LifetimeApp
    val vm: CameraViewModel = viewModel { CameraViewModel(app.container) }

    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var hasAudio by remember { mutableStateOf(context.isGranted(Manifest.permission.RECORD_AUDIO)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = context.isGranted(Manifest.permission.CAMERA)
        hasAudio = context.isGranted(Manifest.permission.RECORD_AUDIO)
        asked = true
    }
    // Re-check when coming back from system settings.
    LifecycleResumeEffect(Unit) {
        hasCamera = context.isGranted(Manifest.permission.CAMERA)
        hasAudio = context.isGranted(Manifest.permission.RECORD_AUDIO)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            CameraContent(vm = vm, hasAudio = hasAudio, onClose = onClose, onOpenMoment = onOpenMoment)
        } else if (asked) {
            CameraPermissionMessage(onClose = onClose)
        }
    }
}

@SuppressLint("MissingPermission") // audio is only enabled when RECORD_AUDIO is granted
@Composable
private fun CameraContent(
    vm: CameraViewModel,
    hasAudio: Boolean,
    onClose: () -> Unit,
    onOpenMoment: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    // CameraController wires preview, capture, tap-to-focus and pinch-to-zoom for us.
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    var mode by rememberSaveable { mutableStateOf(MediaType.PHOTO) }
    var frontCamera by rememberSaveable { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedNanos by remember { mutableLongStateOf(0L) }
    var flash by remember { mutableStateOf(false) }

    val lastCapture by vm.lastCapture.collectAsStateWithLifecycle()
    val locationTagging by vm.locationTagging.collectAsStateWithLifecycle()

    // Only one of photo/video is bound at a time: binding both isn't supported on every device.
    LaunchedEffect(mode) {
        controller.setEnabledUseCases(
            if (mode == MediaType.PHOTO) CameraController.IMAGE_CAPTURE else CameraController.VIDEO_CAPTURE,
        )
    }
    LaunchedEffect(frontCamera) {
        controller.cameraSelector =
            if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    // Stop a running recording if the screen goes away; it is still saved.
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        vm.setLocationTagging(granted)
        if (!granted) Toast.makeText(context, "Location permission was not granted", Toast.LENGTH_SHORT).show()
    }
    fun toggleLocation() {
        when {
            locationTagging -> vm.setLocationTagging(false)
            context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) -> vm.setLocationTagging(true)
            else -> locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    fun takePhoto() {
        val file = vm.newCaptureFile(MediaType.PHOTO)
        val capturedAt = System.currentTimeMillis()
        flash = true
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    vm.onCaptured(file, MediaType.PHOTO, capturedAt)
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    Toast.makeText(context, "Couldn't take photo: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    fun toggleRecording() {
        recording?.let {
            it.stop()
            return
        }
        val file = vm.newCaptureFile(MediaType.VIDEO)
        val capturedAt = System.currentTimeMillis()
        recordedNanos = 0
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_VIDEO_MS).build()
        recording = controller.startRecording(options, AudioConfig.create(hasAudio), mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status -> recordedNanos = event.recordingStats.recordedDurationNanos
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    // These "errors" still leave a complete, playable file behind.
                    val usable = event.error in setOf(
                        VideoRecordEvent.Finalize.ERROR_NONE,
                        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
                    ) && file.length() > 0
                    if (usable) {
                        vm.onCaptured(file, MediaType.VIDEO, capturedAt)
                    } else {
                        file.delete()
                        Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Unit
            }
        }
    }

    val isRecording = recording != null

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Brief white flash so a photo feels taken.
        val flashAlpha by animateFloatAsState(if (flash) 0.7f else 0f, tween(if (flash) 40 else 220), label = "flash")
        LaunchedEffect(flash) { if (flash) { delay(60); flash = false } }
        Box(Modifier.fillMaxSize().alpha(flashAlpha).background(Color.White))

        // Top bar: close, recording timer, location toggle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onClose, enabled = !isRecording) {
                Icon(Icons.Outlined.Close, contentDescription = "Close camera", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(RecordRed))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Formatting.duration(recordedNanos / 1_000_000)} / ${Formatting.duration(MAX_VIDEO_MS)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = !isRecording) { toggleLocation() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    if (locationTagging) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                    contentDescription = null,
                    tint = if (locationTagging) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (locationTagging) "Location on" else "Location off",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Bottom: mode switch, then last capture / shutter / flip.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .navigationBarsPadding().padding(top = 12.dp, bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.alpha(if (isRecording) 0f else 1f)) {
                ModeLabel("Photo", selected = mode == MediaType.PHOTO, enabled = !isRecording) { mode = MediaType.PHOTO }
                ModeLabel("Video", selected = mode == MediaType.VIDEO, enabled = !isRecording) { mode = MediaType.VIDEO }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Last capture: proof it was saved, and a shortcut to look at it.
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .then(
                            lastCapture?.let { m -> Modifier.clickable(enabled = !isRecording) { onOpenMoment(m.id) } }
                                ?: Modifier,
                        ),
                ) {
                    lastCapture?.let { m ->
                        AsyncImage(
                            model = vm.fileFor(m),
                            contentDescription = "Last capture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                ShutterButton(
                    mode = mode,
                    recording = isRecording,
                    onClick = { if (mode == MediaType.PHOTO) takePhoto() else toggleRecording() },
                )

                IconButton(
                    onClick = { frontCamera = !frontCamera },
                    enabled = !isRecording,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Outlined.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShutterButton(mode: MediaType, recording: Boolean, onClick: () -> Unit) {
    val innerSize by animateDpAsState(if (recording) 28.dp else 60.dp, label = "shutterSize")
    val corner by animateDpAsState(if (recording) 6.dp else 30.dp, label = "shutterCorner")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(78.dp).border(4.dp, Color.White, CircleShape).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(innerSize).clip(RoundedCornerShape(corner))
                .background(if (mode == MediaType.VIDEO) RecordRed else Color.White),
        )
    }
}

@Composable
private fun CameraPermissionMessage(onClose: () -> Unit) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            "Lifetime needs the camera to capture moments. Photos and videos never leave your phone.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text("Open settings") }
        TextButton(onClick = onClose) { Text("Not now", color = Color.White) }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
