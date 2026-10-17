package app.lifetime.ui.viewer

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ZoomableImageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedSingleTapsHideAndRestoreControlsAfterRecomposition() {
        val visible = mutableStateOf(true)
        compose.setContent {
            // Deliberately capture the value, just as ViewerPager does. The input coroutine
            // must refresh this callback after every toggle, not retain the first value.
            val renderedVisible = visible.value
            ZoomableImage(
                file = File("unused-photo.jpg"),
                isCurrentPage = true,
                onTap = { visible.value = !renderedVisible },
                onZoomedChange = {},
                modifier = Modifier.testTag("photo"),
            )
        }

        listOf(false, true, false, true).forEach { expected ->
            compose.onNodeWithTag("photo").performTouchInput { click() }
            // A single tap waits for the double-tap timeout before firing.
            compose.mainClock.advanceTimeBy(400)
            compose.runOnIdle { assertEquals(expected, visible.value) }
        }
    }

    @Test fun doubleTapUsesLatestZoomCallbackAndDoesNotToggleToolbar() {
        val callbackVersion = mutableStateOf(0)
        val changes = mutableListOf<Pair<Int, Boolean>>()
        var taps = 0
        compose.setContent {
            val version = callbackVersion.value
            ZoomableImage(
                file = File("unused-photo.jpg"),
                isCurrentPage = true,
                onTap = { taps++ },
                onZoomedChange = { changes.add(version to it) },
                modifier = Modifier.testTag("photo"),
            )
        }
        // First start the pointer-input coroutine, then replace its callbacks.
        compose.onNodeWithTag("photo").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { callbackVersion.value = 1; taps = 0 }
        compose.waitForIdle()
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(listOf(1 to true), changes)
            assertEquals(0, taps)
        }
    }
}
