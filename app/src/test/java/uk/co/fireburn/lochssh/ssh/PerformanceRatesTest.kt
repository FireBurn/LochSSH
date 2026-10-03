package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceRatesTest {
    private fun snapshot(time: Double, counters: NetworkCounters) = PerformanceSnapshot(time,
        null, emptyList(), null, null, mapOf("eth0" to counters), emptyList())

    @Test fun cpuUsesChangesAndTreatsIoWaitAsIdle() {
        val before = CpuCounters(listOf(100, 0, 100, 600, 200, 0, 0, 0))
        val now = CpuCounters(listOf(140, 0, 120, 620, 220, 0, 0, 0))
        assertEquals(60.0, PerformanceRates.cpu(now, before)!!, 0.001)
    }

    @Test fun cpuNeedsTwoValidReadingsAndHandlesResets() {
        val cpu = CpuCounters(listOf(100, 0, 100, 600))
        assertNull(PerformanceRates.cpu(cpu, null))
        assertNull(PerformanceRates.cpu(cpu, cpu))
        assertNull(PerformanceRates.cpu(CpuCounters(listOf(0, 0, 0, 0)), cpu))
        assertNull(PerformanceRates.cpu(CpuCounters(listOf(100, 0, 100, 600, 1)), cpu))
    }

    @Test fun cpuDoesNotOverflowWithLargeCounts() {
        val before = CpuCounters(List(8) { 0 })
        val now = CpuCounters(List(8) { Long.MAX_VALUE })
        assertEquals(75.0, PerformanceRates.cpu(now, before)!!, 0.001)
    }

    @Test fun networkUsesServerElapsedTime() {
        val before = snapshot(100.0, NetworkCounters(1000, 2000))
        val now = snapshot(102.0, NetworkCounters(3048, 3024))
        assertEquals(NetworkRate(1024.0, 512.0), PerformanceRates.network(now, before)["eth0"])
    }

    @Test fun networkHandlesMissingDataResetsAndNewInterfaces() {
        val before = snapshot(100.0, NetworkCounters(1000, 2000))
        assertTrue(PerformanceRates.network(before, null).isEmpty())
        assertTrue(PerformanceRates.network(before, before).isEmpty())
        assertTrue(PerformanceRates.network(before.copy(uptimeSeconds = 99.0), before).isEmpty())
        assertTrue(PerformanceRates.network(snapshot(102.0, NetworkCounters(1, 2)), before).isEmpty())
        assertTrue(PerformanceRates.network(before.copy(uptimeSeconds = null), before).isEmpty())
        assertTrue(PerformanceRates.network(before.copy(uptimeSeconds = 102.0, network = mapOf("new" to NetworkCounters(1, 2))), before).isEmpty())
    }

    @Test fun networkRejectsUnrepresentableRates() {
        val before = snapshot(0.0, NetworkCounters(0, 0))
        val now = snapshot(Double.MIN_VALUE, NetworkCounters(Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(PerformanceRates.network(now, before).isEmpty())
    }
}
