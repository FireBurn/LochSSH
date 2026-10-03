package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceParserTest {
    private val sample = """
        LOCHSSH:UPTIME
        100.25 200.50
        LOCHSSH:CPU
        cpu  100 20 30 400 50 6 7 8 90 10
        LOCHSSH:LOAD
        0.50 1.25 2.00 1/100 900
        LOCHSSH:MEMORY
        MemTotal: 1024 kB
        MemFree: 100 kB
        MemAvailable: 600 kB
        LOCHSSH:NETWORK
        Inter-| Receive | Transmit
        eth0: 1000 1 0 0 0 0 0 0 2000 1 0 0 0 0 0 0
        lo: 100 1 0 0 0 0 0 0 100 1 0 0 0 0 0 0
        LOCHSSH:DISK
        Filesystem 1024-blocks Used Available Capacity Mounted on
        /dev/root 10000 2000 7500 22% /
        LOCHSSH:TEMPERATURE
    """.trimIndent() + "\nCPU package\t45000\nambient\t-1000\n"

    @Test fun readsLinuxValuesAndKeepsInterfacesSeparate() {
        val reading = PerformanceParser.parse(sample)
        assertEquals(100.25, reading.uptimeSeconds!!, 0.001)
        assertEquals(listOf(100L, 20L, 30L, 400L, 50L, 6L, 7L, 8L), reading.cpu?.values)
        assertEquals(listOf(0.5, 1.25, 2.0), reading.load)
        assertEquals(MemoryUse(1048576, 614400), reading.memory)
        assertEquals(DiskUse(10240000, 2048000, 7680000), reading.disk)
        assertEquals(NetworkCounters(1000, 2000), reading.network["eth0"])
        assertEquals(NetworkCounters(100, 100), reading.network["lo"])
        assertEquals(listOf(Temperature("CPU package", 45.0), Temperature("ambient", -1.0)), reading.temperatures)
    }

    @Test fun missingOptionalValuesAreNotInvented() {
        val reading = PerformanceParser.parse("LOCHSSH:CPU\ncpu 1 2 3 4\n")
        assertNull(reading.memory)
        assertNull(reading.disk)
        assertNull(reading.uptimeSeconds)
        assertTrue(reading.load.isEmpty())
        assertTrue(reading.network.isEmpty())
        assertTrue(reading.temperatures.isEmpty())
    }

    @Test fun rejectsUnsupportedAndOversizedResponses() {
        assertThrows(IllegalArgumentException::class.java) { PerformanceParser.parse("Not a Linux server") }
        assertThrows(IllegalArgumentException::class.java) { PerformanceParser.parse("x".repeat(PerformanceParser.MAX_OUTPUT + 1)) }
    }

    @Test fun rejectsInvalidNumbersWithoutLosingOtherReadings() {
        val broken = sample.replace("100.25", "NaN").replace("0.50", "Infinity")
            .replace("600 kB", "-1 kB").replace("/dev/root 10000", "/dev/root 9223372036854775807")
            .replace("eth0: 1000", "eth0: -1").replace("45000", "NaN")
        val reading = PerformanceParser.parse(broken)
        assertNull(reading.uptimeSeconds)
        assertTrue(reading.load.isEmpty())
        assertNull(reading.memory)
        assertNull(reading.disk)
        assertEquals(setOf("lo"), reading.network.keys)
        assertEquals(listOf(Temperature("ambient", -1.0)), reading.temperatures)
    }

    @Test fun rejectsImpossibleMemoryAndDiskCounts() {
        assertNull(PerformanceParser.parse(sample.replace("600 kB", "2000 kB")).memory)
        assertNull(PerformanceParser.parse(sample.replace("10000 2000 7500", "10000 20000 7500")).disk)
    }

    @Test fun rejectsNegativeCpuCountersAndIgnoresUnknownSections() {
        val reading = PerformanceParser.parse(sample.replace("cpu  100", "cpu  -1") + "LOCHSSH:UNKNOWN\nwrong\n")
        assertNull(reading.cpu)
        assertEquals(2, reading.temperatures.size)
    }

    @Test fun readsDeviceNamesContainingSpaces() {
        val reading = PerformanceParser.parse(sample.replace("/dev/root", "a device with spaces"))
        assertEquals(2048000L, reading.disk?.usedBytes)
    }
}
