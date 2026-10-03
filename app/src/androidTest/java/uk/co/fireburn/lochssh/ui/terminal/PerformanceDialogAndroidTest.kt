package uk.co.fireburn.lochssh.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.ssh.CpuCounters
import uk.co.fireburn.lochssh.ssh.DiskUse
import uk.co.fireburn.lochssh.ssh.MemoryUse
import uk.co.fireburn.lochssh.ssh.NetworkCounters
import uk.co.fireburn.lochssh.ssh.PerformanceSnapshot
import uk.co.fireburn.lochssh.ssh.Temperature
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PerformanceDialogAndroidTest {
    @get:Rule val ui = createComposeRule()

    private fun sample(count: Int) = PerformanceSnapshot(100.0 + count * 2,
        CpuCounters(listOf(100L + count * 60, 0, 0, 100L + count * 40, 0, 0, 0, 0)),
        listOf(0.5, 1.0, 2.0), MemoryUse(1048576, 524288), DiskUse(10485760, 2097152, 8388608),
        mapOf("eth0" to NetworkCounters(count * 2048L, count * 1024L)),
        listOf(Temperature("CPU package", 45.0)))

    @Test fun displaysMeasuredValuesAndStopsWhenClosed() {
        val calls = AtomicInteger()
        val open = mutableStateOf(true)
        ui.setContent {
            MaterialTheme {
                if (open.value) PerformanceDialog("Quark", { sample(calls.incrementAndGet()) }, { open.value = false })
                else Text("Closed")
            }
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("CPU: 60.0%").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("CPU: 60.0%").assertIsDisplayed()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithContentDescription("Recent CPU use, 0 to 100 percent").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("Recent CPU use, 0 to 100 percent").assertExists()
        ui.onNodeWithText("Memory: 512.0 KiB used / 1.0 MiB").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("eth0: ↓ 1.0 KiB/s  ↑ 512.0 B/s").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("CPU package: 45.0 °C").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithText("Closed").assertIsDisplayed()
        val stopped = calls.get()
        ui.mainClock.advanceTimeBy(10_000)
        ui.runOnIdle { assertEquals(stopped, calls.get()) }
    }

    @Test fun failedReadingStopsUntilRetry() {
        val calls = AtomicInteger()
        ui.setContent {
            MaterialTheme {
                PerformanceDialog("Quark", {
                    val count = calls.incrementAndGet()
                    if (count == 1) throw IOException("Server command timed out")
                    sample(count)
                }, {})
            }
        }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Server command timed out").assertIsDisplayed()
        ui.mainClock.advanceTimeBy(10_000)
        ui.runOnIdle { assertEquals(1, calls.get()) }
        ui.onNodeWithText("Retry").performClick()
        ui.waitUntil(5_000) {
            ui.onAllNodesWithText("Memory: 512.0 KiB used / 1.0 MiB").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Server command timed out").assertDoesNotExist()
        ui.onNodeWithText("Memory: 512.0 KiB used / 1.0 MiB").assertExists()
    }

    @Test fun closingInterruptsABlockedReading() {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val open = mutableStateOf(true)
        ui.setContent {
            MaterialTheme {
                if (open.value) PerformanceDialog("Quark", {
                    started.countDown()
                    try { Thread.sleep(30_000); sample(1) }
                    finally { stopped.countDown() }
                }, { open.value = false })
                else Text("Closed")
            }
        }
        ui.waitUntil(5_000) { started.count == 0L }
        ui.onNodeWithText("Close").performClick()
        ui.waitUntil(5_000) { stopped.count == 0L }
        ui.onNodeWithText("Closed").assertIsDisplayed()
    }

    @Test fun pausesReadingsWhenTheAppIsNotResumed() {
        val calls = AtomicInteger()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        ui.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        ui.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    PerformanceDialog("Quark", { sample(calls.incrementAndGet()) }, {})
                }
            }
        }
        ui.waitUntil(5_000) { calls.get() > 0 }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        val paused = calls.get()
        ui.mainClock.advanceTimeBy(10_000)
        ui.runOnIdle { assertEquals(paused, calls.get()) }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        ui.waitUntil(5_000) { calls.get() > paused }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
    }
}
