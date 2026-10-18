package app.lifetime

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lifetime.ui.camera.CameraScreen
import app.lifetime.ui.lock.LockScreen
import app.lifetime.ui.theme.LifetimeTheme
import app.lifetime.ui.timeline.TimelineScreen
import app.lifetime.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// A FragmentActivity (still a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    private val container get() = (application as LifetimeApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = container.preferences
        // Collecting starts right away, so the flags are set before the first frame.
        lifecycleScope.launch {
            combine(preferences.privacyScreen, preferences.appLock, ::Pair).collect { (privacyScreen, appLock) ->
                applyWindowPrivacy(privacyScreen, appLock)
            }
        }
        setContent {
            LifetimeTheme {
                val locked by container.appLock.locked.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // Kept composed while locked so unlocking returns to the same screen.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                        LifetimeNavHost()
                    }
                    if (locked) LockScreen(container.appLock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onAppShown()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.appLock.onAppHidden()
    }

    /** FLAG_SECURE blanks recents and blocks screenshots; with the lock on, recents stay blank (13+). */
    private fun applyWindowPrivacy(privacyScreen: Boolean, appLock: Boolean) {
        if (privacyScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!privacyScreen && !appLock)
    }
}

private object Routes {
    const val TIMELINE = "timeline"
    const val CAMERA = "camera"
    const val MOMENT = "moment/{id}"
    fun moment(id: Long) = "moment/$id"
}

@androidx.compose.runtime.Composable
private fun LifetimeNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TIMELINE) {
        composable(Routes.TIMELINE) {
            TimelineScreen(
                onOpenCamera = { nav.navigate(Routes.CAMERA) },
                onOpenMoment = { nav.navigate(Routes.moment(it)) },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onClose = { nav.popBackStack() },
                onOpenMoment = { nav.navigate(Routes.moment(it)) },
            )
        }
        composable(Routes.MOMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ViewerScreen(
                momentId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
