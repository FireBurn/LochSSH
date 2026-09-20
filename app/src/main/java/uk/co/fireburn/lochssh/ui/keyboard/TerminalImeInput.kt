package uk.co.fireburn.lochssh.ui.keyboard

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Invisible text input that captures system keyboard input. The InputConnection
// forwards everything to the terminal instead of editing the (always empty) field.
class TerminalEditText(context: Context) : EditText(context) {
    var onText: ((String) -> Unit)? = null
    var onKey: ((KeyEvent) -> Boolean)? = null
    var onDelete: ((Int) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                onText?.invoke(text.toString())
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return onKey?.invoke(event) ?: true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) onDelete?.invoke(beforeLength)
                return true
            }
        }
    }
}

@Composable
fun TerminalImeInput(
    onText: (String) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onReady: (TerminalEditText) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            TerminalEditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                onReady(this)
            }
        },
        update = { view ->
            view.onText = onText
            view.onKey = onKey
            view.onDelete = onDelete
        },
        modifier = modifier
    )
}

// Encodes IME/hardware key events as terminal byte sequences.
object ImeKeyEncoder {
    fun encode(event: KeyEvent): ByteArray? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed
        val code = event.keyCode

        if (ctrl) {
            val c = event.unicodeChar.toChar()
            if (c in 'a'..'z' || c in 'A'..'Z') return byteArrayOf((c.lowercaseChar().code - 96).toByte())
            if (code == KeyEvent.KEYCODE_SPACE) return byteArrayOf(0)
        }

        when (code) {
            KeyEvent.KEYCODE_ENTER -> return byteArrayOf(0x0D)
            KeyEvent.KEYCODE_DEL -> return byteArrayOf(0x7F)
            KeyEvent.KEYCODE_TAB -> return byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE -> return byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP -> return arrow('A', ctrl, alt)
            KeyEvent.KEYCODE_DPAD_DOWN -> return arrow('B', ctrl, alt)
            KeyEvent.KEYCODE_DPAD_LEFT -> return arrow('D', ctrl, alt)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return arrow('C', ctrl, alt)
            KeyEvent.KEYCODE_MOVE_HOME -> return seq("H")
            KeyEvent.KEYCODE_MOVE_END -> return seq("F")
            KeyEvent.KEYCODE_PAGE_UP -> return seq("5~")
            KeyEvent.KEYCODE_PAGE_DOWN -> return seq("6~")
            KeyEvent.KEYCODE_FORWARD_DEL -> return seq("3~")
        }

        val c = event.unicodeChar.toChar()
        if (c.code >= 0x20) {
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            return if (alt) byteArrayOf(0x1B) + bytes else bytes
        }
        return null
    }

    fun encodeText(text: String): ByteArray =
        text.replace('\n', '\r').toByteArray(Charsets.UTF_8)

    private fun arrow(final: Char, ctrl: Boolean, alt: Boolean): ByteArray {
        val mod = (if (ctrl) 4 else 0) + (if (alt) 2 else 0)
        val suffix = if (mod == 0) final.toString() else "1;${mod + 1}$final"
        return seq(suffix)
    }

    private fun seq(s: String) = byteArrayOf(0x1B.toByte(), '['.code.toByte()) + s.toByteArray()
}
