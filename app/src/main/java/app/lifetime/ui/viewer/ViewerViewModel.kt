package app.lifetime.ui.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifetime.data.Moment
import app.lifetime.data.MomentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ViewerViewModel(
    private val repository: MomentRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** Null until loaded. Same newest-first order as the timeline, so swiping left goes back in time. */
    val moments: StateFlow<List<Moment>?> = repository.moments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    fun delete(moment: Moment) {
        viewModelScope.launch { repository.delete(moment) }
    }

    /** Sets or clears a caption. App scope, so leaving the viewer right after Save still stores it. */
    fun saveCaption(id: Long, text: String): Deferred<Boolean> = appScope.async(Dispatchers.IO) {
        try {
            repository.setCaption(id, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("ViewerViewModel", "Couldn't save caption", failure)
            false
        }
    }
}
