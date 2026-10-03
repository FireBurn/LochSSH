package uk.co.fireburn.lochssh.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockStateTest {
    @Test fun disabledLockDoesNotChangeNormalUse() {
        val lock = AppLockState()
        lock.background(); lock.result(true)
        assertFalse(lock.beginUnlock())
        assertFalse(lock.status.value.locked)
    }

    @Test fun enablingLocksBeforeShowingContent() {
        val lock = AppLockState()
        lock.setEnabled(true)
        assertTrue(lock.status.value.locked)
    }

    @Test fun onlyARequestedSuccessfulUnlockOpensContent() {
        val lock = AppLockState()
        lock.setEnabled(true); lock.result(true)
        assertTrue(lock.status.value.locked)
        assertTrue(lock.beginUnlock())
        assertFalse(lock.beginUnlock())
        lock.result(true)
        assertFalse(lock.status.value.locked)
        assertFalse(lock.status.value.waiting)
    }

    @Test fun cancelledAndFailedUnlocksStayLockedAndCanRetry() {
        val lock = AppLockState()
        lock.setEnabled(true); lock.beginUnlock(); lock.result(false)
        assertTrue(lock.status.value.locked)
        assertTrue(lock.beginUnlock())
        lock.failed("Device unlock unavailable")
        assertTrue(lock.status.value.locked)
        assertFalse(lock.status.value.waiting)
        assertTrue(lock.beginUnlock())
        assertTrue(lock.status.value.error == null)
    }

    @Test fun returningFromTheBackgroundRequiresAnotherUnlock() {
        val lock = AppLockState()
        lock.setEnabled(true); lock.beginUnlock(); lock.result(true); lock.background()
        assertTrue(lock.status.value.locked)
        assertTrue(lock.beginUnlock())
    }

    @Test fun rotatingWithTheSameSettingDoesNotDiscardAnUnlock() {
        val lock = AppLockState()
        lock.setEnabled(true); lock.beginUnlock(); lock.result(true); lock.setEnabled(true)
        assertFalse(lock.status.value.locked)
    }

    @Test fun oldResultsCannotUnlockAfterTheSettingChanges() {
        val lock = AppLockState()
        lock.setEnabled(true); lock.beginUnlock(); lock.setEnabled(false); lock.setEnabled(true); lock.result(true)
        assertTrue(lock.status.value.locked)
        assertFalse(lock.status.value.waiting)
    }

    @Test fun aNewProcessAlwaysStartsLockedWhenEnabled() {
        val first = AppLockState()
        first.setEnabled(true); first.beginUnlock(); first.result(true)
        val next = AppLockState()
        next.setEnabled(true)
        assertTrue(next.status.value.locked)
    }
}
