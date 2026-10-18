package app.lifetime.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the timeline must be hidden behind the phone's unlock. Plain Kotlin so the timing
 * rules are tested on the JVM; the activity reports visibility and the lock screen reports
 * the unlock result.
 *
 * Locked on a cold start and after [graceMillis] or more in the background. The grace period
 * covers trips to the Files picker, Share, the export dialog and the unlock screen itself.
 * Lives in the app container, so a rotation or theme change never relocks, and a killed
 * process always starts locked.
 */
class AppLock(
    private val isEnabled: () -> Boolean,
    private val setEnabled: (Boolean) -> Unit,
    private val clock: () -> Long,
    private val graceMillis: Long = GRACE_MILLIS,
) {
    private val _locked = MutableStateFlow(isEnabled())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var hiddenAt: Long? = null

    /** The app went to the background (not a configuration change). */
    fun onAppHidden() {
        hiddenAt = clock()
    }

    /** The app is visible again. */
    fun onAppShown() {
        val since = hiddenAt ?: return
        hiddenAt = null
        if (isEnabled() && clock() - since >= graceMillis) _locked.value = true
    }

    /** The phone's unlock succeeded. */
    fun unlocked() {
        _locked.value = false
    }

    /** Call only after the phone's unlock succeeded, so the owner knows it works. */
    fun turnOn() {
        setEnabled(true)
    }

    /** Whoever can reach the switch is already inside, so no unlock is needed. */
    fun turnOff() {
        setEnabled(false)
        _locked.value = false
    }

    /**
     * The phone has no screen lock any more, so nothing can be checked. Open the app and turn
     * the lock off rather than lock the owner out of their moments. Returns true if it was on.
     */
    fun onNoScreenLock(): Boolean {
        val wasOn = isEnabled()
        turnOff()
        return wasOn
    }

    companion object {
        const val GRACE_MILLIS = 60_000L
    }
}
