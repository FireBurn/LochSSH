package uk.co.fireburn.lochssh.local

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalShellAndroidTest {
    private val directory: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private fun readUntil(connection: LocalShellConnection, text: String, times: Int = 2): String {
        val seen = StringBuilder()
        val buffer = ByteArray(1024)
        val deadline = System.currentTimeMillis() + 10_000
        val reader = Thread {
            while (synchronized(seen) { Regex(Regex.escape(text)).findAll(seen).count() } < times) {
                val count = connection.input.read(buffer)
                if (count < 0) break
                synchronized(seen) { seen.append(String(buffer, 0, count)) }
            }
        }
        reader.isDaemon = true
        reader.start()
        reader.join(deadline - System.currentTimeMillis())
        return synchronized(seen) { seen.toString() }
    }

    @Test
    fun commandsRunAndTypingIsEchoed() {
        val connection = LocalShellConnection(directory)
        try {
            connection.output.write("echo LOCH_42\r".toByteArray())
            val seen = readUntil(connection, "LOCH_42\r\n")
            assertTrue(seen, seen.contains("echo LOCH_42\r\n"))
        } finally {
            connection.close()
        }
    }

    @Test
    fun interruptStopsARunningCommand() {
        val connection = LocalShellConnection(directory)
        try {
            connection.output.write("sleep 100\r".toByteArray())
            Thread.sleep(500)
            connection.output.write(byteArrayOf(0x03))
            connection.output.write("echo AFTER_INT\r".toByteArray())
            val seen = readUntil(connection, "AFTER_INT\r\n")
            assertTrue(seen, Regex("AFTER_INT\r\n").findAll(seen).count() >= 2)
        } finally {
            connection.close()
        }
    }

    @Test
    fun closingTheShellStopsItsRunningCommand() {
        val childFile = File.createTempFile("shell-child-", ".pid", directory)
        val connection = LocalShellConnection(directory)
        var child: Int? = null
        try {
            connection.output.write(
                "sleep 100 & echo \$! > '${childFile.absolutePath}'; wait\r".toByteArray()
            )
            val started = System.currentTimeMillis() + 5_000
            while (childFile.length() == 0L && System.currentTimeMillis() < started) Thread.sleep(20)
            child = childFile.readText().trim().toInt()
            assertTrue("Child command did not start", running(child))
            connection.close()
            val stopped = System.currentTimeMillis() + 5_000
            while (running(child) && System.currentTimeMillis() < stopped) Thread.sleep(20)
            assertTrue("Child command $child is still running", !running(child))
        } finally {
            connection.close()
            child?.let { if (running(it)) android.os.Process.sendSignal(it, 9) }
            childFile.delete()
        }
    }

    private fun running(pid: Int): Boolean {
        val stat = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return false
        return stat.substringAfterLast(')').trim().firstOrNull() != 'Z'
    }

    @Test
    fun endOfInputEndsTheShell() {
        val connection = LocalShellConnection(directory)
        connection.output.write(byteArrayOf(0x04))
        val deadline = System.currentTimeMillis() + 5_000
        while (connection.isOpen && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(!connection.isOpen)
    }
}
