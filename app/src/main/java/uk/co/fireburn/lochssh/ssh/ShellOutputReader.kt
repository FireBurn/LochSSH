package uk.co.fireburn.lochssh.ssh

import java.io.IOException
import java.io.InputStream

internal object ShellOutputReader {
    fun read(
        input: InputStream,
        isRunning: () -> Boolean,
        onOutput: (ByteArray, Int) -> Unit,
        onStopped: (IOException?) -> Unit
    ) {
        var failure: IOException? = null
        try {
            val buffer = ByteArray(8192)
            while (isRunning()) {
                val count = input.read(buffer)
                if (count == -1) break
                if (count > 0) onOutput(buffer, count)
            }
        } catch (e: IOException) {
            failure = e
        } finally {
            onStopped(failure)
        }
    }
}
