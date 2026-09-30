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
import uk.co.fireburn.lochssh.ui.terminal.TerminalKey
import uk.co.fireburn.lochssh.ui.terminal.TerminalKeyEncoder

// Invisible text input that captures system keyboard input. The InputConnection
// forwards everything to the terminal instead of editing the (always empty) field.
class TerminalEditText(context: Context) : EditText(context) {
    var onText: ((String) -> Unit)? = null
    var onKey: ((KeyEvent) -> Boolean)? = null
    var onDelete: ((Int) -> Unit)? = null

    // Keys the terminal took on the way down, so their release is not passed on
    // either. Anything else, the back key included, is left alone.
    private val claimed = mutableSetOf<Int>()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (onKey?.invoke(event) == true) {
            claimed.add(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (claimed.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

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
                // The password variation stops the keyboard composing words,
                // so every character arrives as it is typed.
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
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
    fun encode(event: KeyEvent, latchedCtrl: Boolean = false, latchedAlt: Boolean = false,
        applicationCursor: Boolean = false, applicationKeypad: Boolean = false): ByteArray? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val ctrl = event.isCtrlPressed || latchedCtrl
        val alt = event.isAltPressed || latchedAlt
        val code = event.keyCode
        val modifier = 1 + (if (event.isShiftPressed) 1 else 0) +
            (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
        val keypad = when (code) {
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                ('0'.code + code - KeyEvent.KEYCODE_NUMPAD_0).toChar()
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> '/'
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> '*'
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> '-'
            KeyEvent.KEYCODE_NUMPAD_ADD -> '+'
            KeyEvent.KEYCODE_NUMPAD_DOT -> '.'
            KeyEvent.KEYCODE_NUMPAD_COMMA -> ','
            KeyEvent.KEYCODE_NUMPAD_ENTER -> '\r'
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> '='
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> '('
            KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> ')'
            else -> null
        }
        if (keypad != null) return uk.co.fireburn.lochssh.ui.terminal.TerminalKeypad.encode(
            keypad, applicationKeypad, event.isNumLockOn, modifier, applicationCursor)
        if (code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            val number = code - KeyEvent.KEYCODE_F1
            if (number < 4) {
                val final = ('P'.code + number).toChar()
                return if (modifier == 1) "\u001bO$final".toByteArray() else seq("1;$modifier$final")
            }
            return TerminalKeyEncoder.navigation("${listOf(15, 17, 18, 19, 20, 21, 23, 24)[number - 4]}~", modifier)
        }

        when (code) {
            KeyEvent.KEYCODE_ENTER -> return byteArrayOf(0x0D)
            KeyEvent.KEYCODE_DEL -> return byteArrayOf(0x7F)
            KeyEvent.KEYCODE_TAB -> return if (event.isShiftPressed) seq("Z") else byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE -> return byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP -> return TerminalKeyEncoder.navigation("A", modifier, applicationCursor)
            KeyEvent.KEYCODE_DPAD_DOWN -> return TerminalKeyEncoder.navigation("B", modifier, applicationCursor)
            KeyEvent.KEYCODE_DPAD_LEFT -> return TerminalKeyEncoder.navigation("D", modifier, applicationCursor)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return TerminalKeyEncoder.navigation("C", modifier, applicationCursor)
            KeyEvent.KEYCODE_MOVE_HOME -> return TerminalKeyEncoder.navigation("H", modifier, applicationCursor)
            KeyEvent.KEYCODE_MOVE_END -> return TerminalKeyEncoder.navigation("F", modifier, applicationCursor)
            KeyEvent.KEYCODE_PAGE_UP -> return TerminalKeyEncoder.navigation("5~", modifier)
            KeyEvent.KEYCODE_PAGE_DOWN -> return TerminalKeyEncoder.navigation("6~", modifier)
            KeyEvent.KEYCODE_FORWARD_DEL -> return TerminalKeyEncoder.navigation("3~", modifier)
            KeyEvent.KEYCODE_INSERT -> return TerminalKeyEncoder.navigation("2~", modifier)
        }

        val textModifiers = event.metaState and
            (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()
        val c = event.getUnicodeChar(textModifiers).toChar()
        if (c.code >= 0x20) {
            return encodeText(c.toString(), ctrl, alt)
        }
        return null
    }

    fun encodeText(text: String, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        return requireNotNull(TerminalKeyEncoder.encode(
            TerminalKey.Text(text.replace('\n', '\r')), ctrl, alt, false
        ))
    }

    private fun seq(s: String) = byteArrayOf(0x1B.toByte(), '['.code.toByte()) + s.toByteArray()
}
