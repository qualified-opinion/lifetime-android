package app.lifetime

import android.content.Context
import android.os.SystemClock
import app.lifetime.data.AppPreferences
import app.lifetime.data.LifetimeDatabase
import app.lifetime.data.LocationProvider
import app.lifetime.data.MediaMetadataReader
import app.lifetime.data.MediaStorage
import app.lifetime.data.MomentRepository
import app.lifetime.lock.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app-wide singletons. Created once in [LifetimeApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Work that must outlive a screen (e.g. saving a capture after the camera closes). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)
    val locationProvider = LocationProvider(appContext)

    /** Process-wide, so a new activity (rotation, theme change) keeps the unlocked state. */
    val appLock = AppLock(
        isEnabled = { preferences.appLock.value },
        setEnabled = preferences::setAppLock,
        clock = SystemClock::elapsedRealtime,
    )

    private val database = LifetimeDatabase.create(appContext)
    private val storage = MediaStorage(appContext)

    val repository = MomentRepository(
        context = appContext,
        dao = database.momentDao(),
        storage = storage,
        metadataReader = MediaMetadataReader(),
    )
}
