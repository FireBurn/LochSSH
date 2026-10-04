package uk.co.fireburn.lochssh.ui.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TerminalInputConnectionAndroidTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var view: TerminalEditText
    private val text = mutableListOf<String>()
    private val deleted = mutableListOf<Int>()

    @Test fun nullAndEmptyCommitsDoNotSendText() {
        showInput()
        ui.runOnIdle {
            val input = view.onCreateInputConnection(EditorInfo())
            input.commitText(null, 1)
            input.commitText("", 1)
            assertTrue(text.isEmpty())
        }
    }

    @Test fun commitsPreserveSupplementaryCharactersAndUseCurrentCallbacks() {
        showInput()
        ui.runOnIdle {
            val input = view.onCreateInputConnection(EditorInfo())
            input.commitText("a𐐷😀", 1)
            assertEquals(listOf("a𐐷😀"), text)
            val replacement = mutableListOf<String>()
            view.onText = replacement::add
            input.commitText("next", 1)
            assertEquals(listOf("next"), replacement)
            assertEquals(listOf("a𐐷😀"), text)
        }
    }

    @Test fun bothDeletionMethodsReachTheTerminal() {
        showInput()
        ui.runOnIdle {
            val input = view.onCreateInputConnection(EditorInfo())
            assertTrue(input.deleteSurroundingText(2, 0))
            assertTrue(input.deleteSurroundingTextInCodePoints(1, 0))
            assertEquals(listOf(2, 1), deleted)
        }
    }

    private fun showInput() {
        ui.setContent {
            MaterialTheme {
                TerminalImeInput(text::add, { true }, deleted::add, onReady = { view = it })
            }
        }
    }
}
