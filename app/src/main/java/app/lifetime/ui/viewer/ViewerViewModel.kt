package app.lifetime.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifetime.data.Moment
import app.lifetime.data.MomentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ViewerViewModel(private val repository: MomentRepository) : ViewModel() {

    /** Null until loaded. Same newest-first order as the timeline, so swiping left goes back in time. */
    val moments: StateFlow<List<Moment>?> = repository.moments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    fun delete(moment: Moment) {
        viewModelScope.launch { repository.delete(moment) }
    }
}
