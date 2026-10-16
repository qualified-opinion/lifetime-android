package app.lifetime.ui.camera

import androidx.lifecycle.ViewModel
import app.lifetime.AppContainer
import app.lifetime.data.MediaType
import app.lifetime.data.Moment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    val locationTagging: StateFlow<Boolean> = container.preferences.locationTagging

    private val _lastCapture = MutableStateFlow<Moment?>(null)
    val lastCapture: StateFlow<Moment?> = _lastCapture.asStateFlow()

    fun setLocationTagging(enabled: Boolean) = container.preferences.setLocationTagging(enabled)

    fun newCaptureFile(type: MediaType): File = repository.newCaptureFile(type)

    fun fileFor(moment: Moment): File = repository.fileFor(moment)

    /**
     * Files a finished capture. Runs in the app scope, not viewModelScope, so a capture
     * still lands in the timeline if the user closes the camera straight away.
     */
    fun onCaptured(file: File, type: MediaType, capturedAt: Long) {
        val tagLocation = locationTagging.value
        container.appScope.launch {
            val location = if (tagLocation) container.locationProvider.currentLocation() else null
            _lastCapture.value = repository.addCaptured(file, type, capturedAt, location)
        }
    }
}
