package app.lifetime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lifetime.ui.camera.CameraScreen
import app.lifetime.ui.theme.LifetimeTheme
import app.lifetime.ui.timeline.TimelineScreen
import app.lifetime.ui.viewer.ViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifetimeTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LifetimeNavHost()
                }
            }
        }
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
