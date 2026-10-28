package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RememberedSessionTest {
    @Test
    fun roundTripsACommandWithSpacesQuotesAndUnicode() {
        val saved = RememberedSession(7, "tmux attach-session -t '\$0' # café ☕", 1234)
        assertEquals(saved, RememberedSession.decode(saved.encode()))
    }

    @Test
    fun roundTripsNoCommandAndAnEmptyOne() {
        assertEquals(RememberedSession(3, null, 9), RememberedSession.decode(RememberedSession(3, null, 9).encode()))
        assertEquals(RememberedSession(3, "", 9), RememberedSession.decode(RememberedSession(3, "", 9).encode()))
    }

    @Test
    fun rejectsDamagedEntries() {
        assertNull(RememberedSession.decode(""))
        assertNull(RememberedSession.decode("x ! 5"))
        assertNull(RememberedSession.decode("1 tmux 5"))
        assertNull(RememberedSession.decode("1 ! 5 extra"))
    }
}
