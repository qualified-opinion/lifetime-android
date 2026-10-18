package app.lifetime.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny settings store. SharedPreferences is plenty for a handful of switches. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("lifetime_prefs", Context.MODE_PRIVATE)

    private val _locationTagging = MutableStateFlow(prefs.getBoolean(KEY_LOCATION, false))
    val locationTagging: StateFlow<Boolean> = _locationTagging.asStateFlow()

    /** Blank in recent apps and blocks screenshots (FLAG_SECURE). On unless turned off. */
    private val _privacyScreen = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_SCREEN, true))
    val privacyScreen: StateFlow<Boolean> = _privacyScreen.asStateFlow()

    /** Ask for the phone's unlock before showing the timeline. Off unless turned on. */
    private val _appLock = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK, false))
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    fun setLocationTagging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION, enabled).apply()
        _locationTagging.value = enabled
    }

    fun setPrivacyScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_SCREEN, enabled).apply()
        _privacyScreen.value = enabled
    }

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _appLock.value = enabled
    }

    private companion object {
        const val KEY_LOCATION = "location_tagging"
        const val KEY_PRIVACY_SCREEN = "privacy_screen"
        const val KEY_APP_LOCK = "app_lock"
    }
}
