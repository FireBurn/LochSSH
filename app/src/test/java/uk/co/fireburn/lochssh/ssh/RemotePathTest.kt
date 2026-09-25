package uk.co.fireburn.lochssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RemotePathTest {
    @Test
    fun joinsNamesWithoutLosingTheRoot() {
        assertEquals("/file.txt", RemotePath.child("/", "file.txt"))
        assertEquals("/home/user/file.txt", RemotePath.child("/home/user/", "file.txt"))
    }

    @Test
    fun rejectsNamesThatEscapeTheCurrentDirectory() {
        for (name in listOf("", ".", "..", "a/b", "a\u0000b")) {
            assertThrows(IllegalArgumentException::class.java) {
                RemotePath.child("/home/user", name)
            }
        }
    }

    @Test
    fun findsParentDirectories() {
        assertNull(RemotePath.parent("/"))
        assertNull(RemotePath.parent("."))
        assertEquals("/", RemotePath.parent("/home"))
        assertEquals("/home", RemotePath.parent("/home/user/"))
    }
}
