package app.lifetime.ui.timeline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifetime.data.Moment
import app.lifetime.data.MomentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class TimelineViewModel(private val repository: MomentRepository) : ViewModel() {

    val state: StateFlow<TimelineState> = repository.moments
        .map(::buildTimeline)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineState())

    /** Non-null while a long import/export is running; shown as a small progress line. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    /** One-off messages for the snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = if (uris.size == 1) "Importing…" else "Importing ${uris.size} items…"
            val imported = repository.import(uris)
            _busy.value = null
            val failed = uris.size - imported
            _messages.send(
                buildString {
                    append(if (imported == 1) "Added 1 moment" else "Added $imported moments")
                    if (failed > 0) append(" ($failed couldn't be read)")
                },
            )
        }
    }

    fun export(target: Uri) {
        viewModelScope.launch {
            _busy.value = "Exporting your timeline…"
            val result = runCatching { repository.exportZip(target) }
            _busy.value = null
            _messages.send(
                result.fold(
                    onSuccess = { "Exported $it moments" },
                    onFailure = { "Export failed: ${it.message}" },
                ),
            )
        }
    }

    /** Row index for a picked date: that day, or the closest earlier day that has moments. */
    fun rowIndexFor(date: LocalDate): Int? {
        val days = state.value.dayIndex
        if (days.isEmpty()) return null
        return (days.firstOrNull { (day, _) -> !day.isAfter(date) } ?: days.last()).second
    }
}
