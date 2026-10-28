package uk.co.fireburn.lochssh.mosh

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Runs against a real mosh-server when MOSH_SERVER names one, and is skipped otherwise.
class MoshInteropTest {
    @Test
    fun typesIntoARealMoshServerAndSeesTheScreenChange() {
        val server = System.getenv("MOSH_SERVER")
        assumeTrue(server != null && File(server).canExecute())
        val builder = ProcessBuilder(server, "new", "-c", "256", "-p", "61041", "--", "/bin/sh")
        builder.environment()["LANG"] = "C.UTF-8"
        builder.environment()["LC_ALL"] = "C.UTF-8"
        builder.redirectErrorStream(true)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        val started = MoshServerOutput.parse(output)!!
        val connection = MoshConnection("127.0.0.1", started.port, started.key, 80, 24)
        val seen = StringBuilder()
        val reader = Thread {
            val buffer = ByteArray(4096)
            while (true) {
                val count = connection.input.read(buffer)
                if (count < 0) break
                synchronized(seen) { seen.append(String(buffer, 0, count)) }
            }
        }
        reader.isDaemon = true
        reader.start()
        try {
            connection.output.write("echo MOSH_\$((6*7))\r".toByteArray())
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && !synchronized(seen) { seen.contains("MOSH_42") }) Thread.sleep(50)
            assertTrue("screen output was: ${synchronized(seen) { seen.toString() }}", synchronized(seen) { seen.contains("MOSH_42") })
            connection.resize(100, 30)
            connection.breakSocketForTest()
            connection.output.write("echo AFTER_\$((7*6))\r".toByteArray())
            val after = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < after && !synchronized(seen) { seen.contains("AFTER_42") }) Thread.sleep(50)
            assertTrue("after a broken socket the output was: ${synchronized(seen) { seen.toString() }}",
                synchronized(seen) { seen.contains("AFTER_42") })
            connection.shutdown()
            val gone = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < gone && connection.isOpen) Thread.sleep(50)
        } finally {
            Thread.sleep(500)
            connection.close()
        }
    }
}
