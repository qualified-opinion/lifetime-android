package app.lifetime.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lifetime.LifetimeApp
import app.lifetime.data.MediaType
import app.lifetime.data.Moment
import app.lifetime.data.MomentSource
import app.lifetime.ui.components.DarkSystemBars
import app.lifetime.ui.components.Formatting
import java.io.File

/** Full-screen, swipeable viewer. Tap to hide/show the controls. */
@Composable
fun ViewerScreen(momentId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LifetimeApp
    val vm: ViewerViewModel = viewModel { ViewerViewModel(app.container.repository) }
    val moments by vm.moments.collectAsStateWithLifecycle()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    DarkSystemBars(hidden = !chromeVisible)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val list = moments
        when {
            list == null -> Unit
            list.isEmpty() -> LaunchedEffect(Unit) { onBack() }
            else -> ViewerPager(
                moments = list,
                startId = momentId,
                chromeVisible = chromeVisible,
                onChromeVisibleChange = { chromeVisible = it },
                vm = vm,
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerPager(
    moments: List<Moment>,
    startId: Long,
    chromeVisible: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
    vm: ViewerViewModel,
    onBack: () -> Unit,
) {
    val initialPage = remember { moments.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { moments.size }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val current = moments.getOrNull(pagerState.currentPage.coerceAtMost(moments.lastIndex))
    LaunchedEffect(current?.id) {
        zoomed = false
        // A photo must not inherit a video's auto-hidden controls (or a deleted page's state).
        onChromeVisibleChange(true)
    }

    HorizontalPager(
        state = pagerState,
        key = { moments[it].id },
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val moment = moments[page]
        val file = vm.fileFor(moment)
        when (moment.type) {
            MediaType.PHOTO -> ZoomableImage(
                file = file,
                isCurrentPage = page == pagerState.currentPage,
                onTap = { onChromeVisibleChange(!chromeVisible) },
                onZoomedChange = { if (page == pagerState.currentPage) zoomed = it },
            )
            MediaType.VIDEO -> VideoPlayer(
                file = file,
                isActive = page == pagerState.settledPage,
                onControlsVisibleChange = { if (page == pagerState.currentPage) onChromeVisibleChange(it) },
            )
        }
    }

    // Top bar over a soft gradient so it reads on bright photos.
    AnimatedVisibility(visible = chromeVisible && current != null, enter = fadeIn(), exit = fadeOut()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            current?.let { m ->
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(Formatting.shortDate(m.capturedAt), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(Formatting.time(m.capturedAt), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
            }
            current?.let { m ->
                IconButton(onClick = { shareMoment(context, m, vm.fileFor(m)) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
                }
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color.White)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color.White)
            }
        }
    }

    if (showInfo && current != null) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            MomentInfo(current, vm.fileFor(current).name)
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (current.type == MediaType.VIDEO) "Delete this video?" else "Delete this photo?") },
            text = {
                Text(
                    if (current.source == MomentSource.IMPORT) {
                        "It will be removed from your timeline. The original in your gallery is not touched."
                    } else {
                        "It will be removed from your timeline and this phone. This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(current)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MomentInfo(moment: Moment, fileName: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("Details", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        InfoRow(Icons.Outlined.CalendarToday, "Date", Formatting.fullDateTime(moment.capturedAt))

        val typeDetail = buildList {
            add(if (moment.type == MediaType.VIDEO) "Video" else "Photo")
            if (moment.type == MediaType.VIDEO && moment.durationMs > 0) add(Formatting.duration(moment.durationMs))
            if (moment.width > 0 && moment.height > 0) add("${moment.width} × ${moment.height}")
        }.joinToString(" · ")
        InfoRow(if (moment.type == MediaType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Photo, "Type", typeDetail)

        if (moment.latitude != null && moment.longitude != null) {
            val lat = moment.latitude
            val lon = moment.longitude
            InfoRow(Icons.Outlined.LocationOn, "Location", Formatting.coordinates(lat, lon)) {
                TextButton(onClick = {
                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open in maps") }
            }
        } else {
            InfoRow(Icons.Outlined.LocationOn, "Location", "Not recorded")
        }

        val origin = if (moment.source == MomentSource.CAMERA) "Captured in Lifetime" else "Imported ${Formatting.shortDate(moment.addedAt)}"
        InfoRow(
            Icons.Outlined.InsertDriveFile,
            "File",
            "$fileName · ${Formatting.fileSize(moment.sizeBytes)}\n$origin",
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            action?.invoke()
        }
    }
}

/** Sends one moment to another app (messages, mail, files) through the system share sheet. */
private fun shareMoment(context: android.content.Context, moment: Moment, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = moment.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
