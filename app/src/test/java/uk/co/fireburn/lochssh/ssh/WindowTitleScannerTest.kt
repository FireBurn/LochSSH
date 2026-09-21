package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowTitleScannerTest {

    private fun scan(vararg chunks: String): String? {
        var title: String? = null
        val scanner = WindowTitleScanner { title = it }
        for (chunk in chunks) {
            val bytes = chunk.toByteArray(Charsets.UTF_8)
            scanner.feed(bytes, bytes.size)
        }
        return title
    }

    @Test
    fun readsAWindowTitle() {
        assertEquals("less /etc/services", scan("]0;less /etc/services"))
    }

    @Test
    fun keepsCharactersThatTakeMoreThanOneByte() {
        // Claude Code puts a U+2733 in its title, which is three bytes of UTF-8.
        assertEquals("✳ Claude Code", scan("]0;✳ Claude Code"))
    }

    @Test
    fun readsATitleSplitAcrossReads() {
        assertEquals("✳ Claude Code", scan("]0;✳ Clau", "de Code"))
    }

    @Test
    fun acceptsTheStringTerminator() {
        assertEquals("vim", scan("]2;vim\\"))
    }

    @Test
    fun describesAShellFromItsIntegrationRecord() {
        val record = "]3008;start=abc;type=shell;user=fireburn;" +
            "hostname=axion.fireburn.co.uk;cwd=/home/fireburn/work\\"
        assertEquals("fireburn@axion: ~/work", scan(record))
    }

    @Test
    fun ignoresRecordsWithNothingToShow() {
        assertNull(scan("]3008;end=abc;exit=success\\"))
    }
}
