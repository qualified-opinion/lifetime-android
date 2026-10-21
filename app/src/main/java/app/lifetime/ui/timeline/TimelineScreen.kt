package app.lifetime.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lifetime.LifetimeApp
import app.lifetime.data.Moment
import app.lifetime.ui.components.Formatting
import app.lifetime.ui.components.MomentThumbnail
import app.lifetime.ui.lock.DeviceUnlock
import app.lifetime.ui.lock.findFragmentActivity
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val MonthHeaderHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenCamera: () -> Unit,
    onOpenMoment: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LifetimeApp
    val vm: TimelineViewModel = viewModel { TimelineViewModel(app.container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val privacyScreen by app.container.preferences.privacyScreen.collectAsStateWithLifecycle()
    val appLockOn by app.container.preferences.appLock.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerPx = with(LocalDensity.current) { MonthHeaderHeight.toPx() }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // The Files picker grants access to original documents. Photo-picker copies can
    // redact GPS even when ACCESS_MEDIA_LOCATION has already been granted.
    val pickMedia = rememberLauncherForActivityResult(
        OriginalMediaPicker(),
    ) { uris -> vm.import(uris) }
    val openPicker = {
        pickMedia.launch(Unit)
    }
    val mediaLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> openPicker() } // A denial still continues the import.
    val launchImport = {
        if (Build.VERSION.SDK_INT >= 29 && !vm.mediaLocationRequested &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.mediaLocationRequested = true
            Toast.makeText(app, "To keep photo locations, allow access if asked, then select the original files.", Toast.LENGTH_LONG).show()
            mediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            openPicker()
        }
    }

    // Turning the lock on asks for the phone's unlock first, so it is known to work.
    val toggleAppLock = toggle@{
        val appLock = app.container.appLock
        if (appLockOn) return@toggle appLock.turnOff()
        val activity = context.findFragmentActivity() ?: return@toggle
        if (!DeviceUnlock.hasScreenLock(context)) {
            scope.launch { snackbar.showSnackbar("Set a screen lock in the phone's settings first.") }
            return@toggle
        }
        DeviceUnlock.ask(activity, "Turn on app lock") { result ->
            if (result == DeviceUnlock.Result.UNLOCKED) appLock.turnOn()
        }
    }

    // Providers label zips differently, so accept the common types and let the restore check it.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.restore(uri) }

    // "Save as" dialog for the export zip, so the user chooses where it lands.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.export(uri) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text("Lifetime", style = MaterialTheme.typography.titleLarge)
                        if (state.totalMoments > 0) {
                            Text(
                                if (state.totalMoments == 1) "1 moment" else "${state.totalMoments} moments",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.totalMoments > 0) {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Jump to date")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import photos and videos") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = { menuOpen = false; launchImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export everything (.zip)") },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            enabled = state.totalMoments > 0,
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("lifetime-export-${LocalDate.now()}.zip")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore from export (.zip)") },
                            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                            enabled = busy == null,
                            onClick = {
                                menuOpen = false
                                restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Block screenshots") },
                            leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                            trailingIcon = { Switch(checked = privacyScreen, onCheckedChange = null) },
                            onClick = { app.container.preferences.setPrivacyScreen(!privacyScreen) },
                        )
                        DropdownMenuItem(
                            text = { Text("App lock") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                            trailingIcon = { Switch(checked = appLockOn, onCheckedChange = null) },
                            onClick = { menuOpen = false; toggleAppLock() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.totalMoments > 0) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = launchImport) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import photos and videos")
                    }
                    ExtendedFloatingActionButton(
                        onClick = onOpenCamera,
                        expanded = expandedFab,
                        icon = { Icon(Icons.Outlined.PhotoCamera, null) },
                        text = { Text("Capture") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit
                state.totalMoments == 0 -> EmptyTimeline(onCapture = onOpenCamera, onImport = launchImport)
                else -> TimelineList(
                    state = state,
                    listState = listState,
                    fileFor = vm::fileFor,
                    onOpenMoment = onOpenMoment,
                )
            }
            AnimatedVisibility(visible = busy != null, modifier = Modifier.align(Alignment.TopCenter)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth()) {
                        val progress = busy?.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(
                            busy?.label.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initial = state.dayIndex.firstOrNull()?.first ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    // The picker reports midnight UTC of the chosen day.
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    val index = vm.rowIndexFor(date) ?: return@TextButton
                    scope.launch {
                        listState.scrollToItem(index)
                        // Keep the day header clear of the sticky month header.
                        if (index > 0) listState.scrollBy(-headerPx)
                    }
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState, showModeToggle = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineList(
    state: TimelineState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    fileFor: (Moment) -> File,
    onOpenMoment: (Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 160.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.rows.forEach { row ->
            when (row) {
                is TimelineRow.Year -> item(key = row.key, contentType = "year") {
                    Text(
                        row.year.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp),
                    )
                }
                is TimelineRow.Month -> stickyHeader(key = row.key, contentType = "month") {
                    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)) {
                        Box(
                            Modifier.fillMaxWidth().height(MonthHeaderHeight).padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(Formatting.monthLabel(row.firstDay), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                is TimelineRow.Day -> item(key = row.key, contentType = "day") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                    ) {
                        Text(
                            Formatting.dayLabel(row.date, today),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.count > 1) {
                            Text(
                                "${row.count}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is TimelineRow.Grid -> item(key = row.key, contentType = "grid${row.columns}") {
                    GridRow(row, fileFor, onOpenMoment)
                }
                is TimelineRow.Post -> item(key = row.key, contentType = "post") {
                    PostRow(row.moment, fileFor(row.moment)) { onOpenMoment(row.moment.id) }
                }
            }
        }
    }
}

@Composable
private fun GridRow(row: TimelineRow.Grid, fileFor: (Moment) -> File, onOpenMoment: (Long) -> Unit) {
    val ratio = if (row.columns == 1) 4f / 3f else 1f
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
    ) {
        row.moments.forEach { moment ->
            MomentThumbnail(
                moment = moment,
                file = fileFor(moment),
                onClick = { onOpenMoment(moment.id) },
                modifier = Modifier.weight(1f).aspectRatio(ratio),
            )
        }
        // Keep cells the same size when the last row of a day isn't full.
        repeat(row.columns - row.moments.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A captioned moment: the photo or video at full width (in its own shape, within limits), its words under it. */
@Composable
private fun PostRow(moment: Moment, file: File, onOpen: () -> Unit) {
    val ratio = if (moment.width > 0 && moment.height > 0) {
        (moment.width.toFloat() / moment.height).coerceIn(4f / 5f, 16f / 9f)
    } else {
        4f / 3f
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        MomentThumbnail(
            moment = moment,
            file = file,
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )
        Text(
            moment.caption.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyTimeline(onCapture: () -> Unit, onImport: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(
            Icons.Outlined.WbSunny,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("Your lifetime starts here", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Capture a moment, or bring in photos and videos you already have. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = onCapture) {
            Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.size(8.dp))
            Text("Capture a moment")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onImport) {
            Icon(Icons.Outlined.PhotoLibrary, null)
            Spacer(Modifier.size(8.dp))
            Text("Import photos and videos")
        }
    }
}
