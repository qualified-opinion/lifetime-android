package app.lifetime.ui.camera

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import app.lifetime.AppContainer
import app.lifetime.data.MediaType
import app.lifetime.data.Moment
import app.lifetime.data.MomentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel internal constructor(
    private val repository: MomentRepository,
    private val appScope: CoroutineScope,
    val locationTagging: StateFlow<Boolean>,
    private val updateLocationTagging: (Boolean) -> Unit,
    private val currentLocation: suspend () -> Location?,
) : ViewModel() {
    constructor(container: AppContainer) : this(
        container.repository,
        container.appScope,
        container.preferences.locationTagging,
        container.preferences::setLocationTagging,
        container.locationProvider::currentLocation,
    )

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _lastCapture = MutableStateFlow<Moment?>(null)
    val lastCapture: StateFlow<Moment?> = _lastCapture.asStateFlow()

    fun setLocationTagging(enabled: Boolean) = updateLocationTagging(enabled)

    fun newCaptureFile(type: MediaType): File = repository.newCaptureFile(type)

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    /**
     * Files a finished capture. Runs in the app scope, not viewModelScope, so a capture
     * still lands in the timeline if the user closes the camera straight away.
     */
    fun onCaptured(file: File, type: MediaType, capturedAt: Long): Job {
        val tagLocation = locationTagging.value
        return appScope.launch(Dispatchers.IO) {
            val moment = try {
                repository.addCaptured(file, type, capturedAt)
            } catch (failure: Exception) {
                _messages.trySend("Couldn't save that capture")
                if (failure is CancellationException) throw failure
                return@launch
            }
            _lastCapture.value = moment

            // Location is optional enrichment: the file and row are already safe.
            if (tagLocation) {
                try {
                    currentLocation()?.let { repository.updateLocation(moment.id, it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("CameraViewModel", "Couldn't tag capture location", failure)
                }
            }
        }
    }

    /**
     * Stores the caption for a capture. Runs in the app scope so it still lands if the camera
     * closes right after Save. Completes with false (and a message) if it couldn't be saved.
     */
    fun saveCaption(id: Long, text: String): Deferred<Boolean> = appScope.async(Dispatchers.IO) {
        val saved = try {
            repository.setCaption(id, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("CameraViewModel", "Couldn't save caption", failure)
            false
        }
        if (!saved) _messages.trySend("Couldn't save the caption")
        saved
    }
}
