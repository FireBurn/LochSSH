package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRegistryTest {
    @Test
    fun keepsRecentForwardWarningsUntilDismissed() {
        val registry = SessionRegistry()
        val id = registry.newId()
        registry.opening(id, 1, "Host")

        registry.addForwardWarning(id, "same error")
        registry.addForwardWarning(id, "same error")
        assertEquals(listOf("same error"), registry.find(id)?.forwardWarnings)

        (1..6).forEach { registry.addForwardWarning(id, "error $it") }
        assertEquals((2..6).map { "error $it" }, registry.find(id)?.forwardWarnings)

        registry.dismissForwardWarnings(id)
        assertTrue(registry.find(id)?.forwardWarnings.isNullOrEmpty())
    }
}
