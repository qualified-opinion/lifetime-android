package app.lifetime.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var enabled = false
    private var now = 0L
    private fun lock() = AppLock(isEnabled = { enabled }, setEnabled = { enabled = it }, clock = { now })

    @Test fun coldStartIsLockedOnlyWhenTheLockIsOn() {
        enabled = false
        assertFalse(lock().locked.value)
        enabled = true
        assertTrue(lock().locked.value)
    }

    @Test fun shortTripAwayStaysUnlockedLongOneRelocks() {
        enabled = true
        val lock = lock()
        lock.unlocked()

        lock.onAppHidden()
        now += AppLock.GRACE_MILLIS - 1 // Files picker, Share, export dialog
        lock.onAppShown()
        assertFalse(lock.locked.value)

        lock.onAppHidden()
        now += AppLock.GRACE_MILLIS
        lock.onAppShown()
        assertTrue(lock.locked.value)
    }

    @Test fun lockOffNeverLocks() {
        val lock = lock()
        lock.onAppHidden()
        now += 10 * AppLock.GRACE_MILLIS
        lock.onAppShown()
        assertFalse(lock.locked.value)
    }

    @Test fun shownWithoutHiddenChangesNothing() {
        // The first onStart of a cold start, and a configuration change (hidden is not reported).
        enabled = true
        val lock = lock()
        lock.unlocked()
        now += 10 * AppLock.GRACE_MILLIS
        lock.onAppShown()
        assertFalse(lock.locked.value)
    }

    @Test fun eachTripIsTimedFromItsOwnStart() {
        enabled = true
        val lock = lock()
        lock.unlocked()
        lock.onAppHidden()
        now += 1_000
        lock.onAppShown()
        now += 10 * AppLock.GRACE_MILLIS // a long time in the foreground
        lock.onAppHidden()
        now += 1_000
        lock.onAppShown()
        assertFalse(lock.locked.value)
    }

    @Test fun turningOnDoesNotLockRightAwayAndOffUnlocks() {
        val lock = lock()
        lock.turnOn()
        assertTrue(enabled)
        assertFalse(lock.locked.value)

        lock.onAppHidden()
        now += AppLock.GRACE_MILLIS
        lock.onAppShown()
        assertTrue(lock.locked.value)

        lock.turnOff()
        assertFalse(enabled)
        assertFalse(lock.locked.value)
    }

    @Test fun noScreenLockOpensTheAppAndTurnsTheLockOff() {
        enabled = true
        val lock = lock()
        assertTrue(lock.locked.value)
        assertTrue(lock.onNoScreenLock())
        assertFalse(lock.locked.value)
        assertFalse(enabled)
        assertEquals(false, lock.onNoScreenLock()) // already off: no second message
    }
}
