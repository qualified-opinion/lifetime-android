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

    fun setLocationTagging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION, enabled).apply()
        _locationTagging.value = enabled
    }

    private companion object {
        const val KEY_LOCATION = "location_tagging"
    }
}
