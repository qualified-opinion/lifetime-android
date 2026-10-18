package app.lifetime.data

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppPreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun privacyScreenOnAndAppLockOffByDefault() {
        val prefs = AppPreferences(context)
        assertTrue(prefs.privacyScreen.value)
        assertFalse(prefs.appLock.value)
        assertFalse(prefs.locationTagging.value)
    }

    @Test fun choicesSurviveARestart() {
        AppPreferences(context).apply {
            setPrivacyScreen(false)
            setAppLock(true)
        }
        val reopened = AppPreferences(context)
        assertFalse(reopened.privacyScreen.value)
        assertTrue(reopened.appLock.value)
    }
}
