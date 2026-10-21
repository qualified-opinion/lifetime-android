package app.lifetime.lock

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise real FragmentManager state saving; this does not simulate biometric hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33], application = Application::class)
class FragmentHostTest {
    @Test fun initializedFragmentCanBeSavedRestoredAndThenResumed() {
        val saved = Bundle()
        val first = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val pending = PendingFragment().apply {
                arguments = Bundle().apply { putString("marker", "kept before creation") }
            }
            first.get().supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(pending, "pending")
                .setMaxLifecycle(pending, Lifecycle.State.INITIALIZED)
                .commitNow()
            assertEquals(Lifecycle.State.INITIALIZED, pending.lifecycle.currentState)
            assertEquals(0, pending.createCalls)

            // Fragment 1.8.8 fixed saving an INITIALIZED fragment before onCreate.
            first.pause().saveInstanceState(saved).stop().destroy()
            assertEquals(0, pending.saveCalls)
        } finally {
            if (!first.get().isDestroyed) first.destroy()
        }

        val second = Robolectric.buildActivity(FragmentActivity::class.java)
            .create(saved).start().resume().visible()
        try {
            val manager = second.get().supportFragmentManager
            val pending = manager.findFragmentByTag("pending") as PendingFragment?
            assertNotNull(pending)
            val restored = pending!!
            assertEquals("kept before creation", restored.arguments!!.getString("marker"))
            assertEquals(Lifecycle.State.INITIALIZED, restored.lifecycle.currentState)
            assertEquals(0, restored.createCalls)

            manager.beginTransaction()
                .setMaxLifecycle(restored, Lifecycle.State.RESUMED)
                .commitNow()
            assertEquals(Lifecycle.State.RESUMED, restored.lifecycle.currentState)
            assertEquals(1, restored.createCalls)
        } finally {
            second.pause().stop().destroy()
        }
    }

    // Public no-arg type so FragmentFactory can restore it from the saved class name.
    class PendingFragment : Fragment() {
        var createCalls = 0
            private set
        var saveCalls = 0
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            createCalls++
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            saveCalls++
        }
    }
}
