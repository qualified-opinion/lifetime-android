package app.lifetime.ui.lock

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import app.lifetime.lock.AppLock

/**
 * Covers the whole app while locked. The surface swallows touches; the caller hides the
 * content underneath from accessibility. Asks for the unlock once on its own, then on tap.
 */
@Composable
fun LockScreen(appLock: AppLock) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val noScreenLock = {
        if (appLock.onNoScreenLock()) {
            Toast.makeText(context, "App lock turned off: this phone has no screen lock.", Toast.LENGTH_LONG).show()
        }
    }
    val ask = ask@{
        if (activity == null) return@ask
        if (!DeviceUnlock.hasScreenLock(context)) return@ask noScreenLock()
        DeviceUnlock.ask(activity, "Unlock Lifetime") { result ->
            when (result) {
                DeviceUnlock.Result.UNLOCKED -> appLock.unlocked()
                DeviceUnlock.Result.NO_SCREEN_LOCK -> noScreenLock()
                DeviceUnlock.Result.CANCELLED -> Unit
            }
        }
    }

    // Once per lock: a cancel shows the button instead of asking again in a loop.
    LaunchedEffect(Unit) { lifecycle.withResumed { ask() } }
    BackHandler { activity?.moveTaskToBack(true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Lifetime is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = ask) { Text("Unlock") }
        }
    }
}
