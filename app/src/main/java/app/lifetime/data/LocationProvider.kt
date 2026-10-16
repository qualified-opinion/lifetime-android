package app.lifetime.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Optional location for new captures, using the platform LocationManager so it works
 * offline (GPS) and without Google Play services.
 */
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** A recent fix if one is cached, otherwise a fresh one (or null after a few seconds). */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission() || manager == null) return null

        val providers = manager.getProviders(true)
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_ENOUGH_MS) return last

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return last
        }
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return fresh ?: last
    }

    private companion object {
        const val FRESH_ENOUGH_MS = 2 * 60 * 1000L
        const val FIX_TIMEOUT_MS = 5_000L
    }
}
