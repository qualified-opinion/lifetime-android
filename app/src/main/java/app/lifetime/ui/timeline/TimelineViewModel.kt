package app.lifetime.ui.timeline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifetime.data.Moment
import app.lifetime.data.MomentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

class TimelineViewModel internal constructor(
    private val repository: MomentRepository,
    scopeOverride: CoroutineScope?,
) : ViewModel() {
    constructor(repository: MomentRepository) : this(repository, null)

    private val scope = scopeOverride ?: viewModelScope

    val state: StateFlow<TimelineState> = repository.moments
        .map(::buildTimeline)
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), TimelineState())

    /** A long import/export in progress. [progress] is 0..1, or null when unknown. */
    data class Busy(val label: String, val progress: Float? = null)

    /** Non-null while a long import/export is running; shown as a small progress line. */
    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    /** One-off messages for the snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    // Kept in the ViewModel so returning from the picker or camera does not repeat the prompt.
    internal var mediaLocationRequested = false

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        import(uris.size) { progress -> repository.import(uris, progress) }
    }

    internal fun import(
        totalItems: Int,
        read: suspend ((Int, Int) -> Unit) -> MomentRepository.ImportResult,
    ) = scope.launch {
        _busy.value = if (totalItems == 1) Busy("Importing…") else Busy("Importing 0 of $totalItems…", 0f)
        try {
            val result = read { done, total ->
                if (total > 1) _busy.value = Busy("Importing $done of $total…", done / total.toFloat())
            }
            val failed = totalItems - result.imported
            _messages.send(buildString {
                append(if (result.imported == 1) "Added 1 moment" else "Added ${result.imported} moments")
                if (result.undated > 0) append(" (${result.undated} had no date, filed under today)")
                if (failed > 0) append(" ($failed couldn't be read)")
            })
        } finally {
            _busy.value = null
        }
    }

    fun export(target: Uri) = export { repository.exportZip(target) }

    internal fun export(writeZip: suspend () -> MomentRepository.ExportResult) = scope.launch {
        _busy.value = Busy("Exporting your timeline…")
        try {
            val exported = writeZip()
            _messages.trySend(buildString {
                append("Exported ${exported.exported} moments")
                if (exported.missing > 0) append(" (${exported.missing} files were missing)")
            })
        } catch (cancelled: CancellationException) {
            // The repository has finished cleanup; sending must also work in a cancelled scope.
            _messages.trySend("Export failed: ${cancelled.message ?: "Export cancelled"}")
            throw cancelled
        } catch (failure: Exception) {
            _messages.trySend("Export failed: ${failure.message}")
        } finally {
            _busy.value = null
        }
    }

    /** Row index for a picked date: that day, or the closest earlier day that has moments. */
    fun rowIndexFor(date: LocalDate): Int? {
        val days = state.value.dayIndex
        if (days.isEmpty()) return null
        return (days.firstOrNull { (day, _) -> !day.isAfter(date) } ?: days.last()).second
    }
}
