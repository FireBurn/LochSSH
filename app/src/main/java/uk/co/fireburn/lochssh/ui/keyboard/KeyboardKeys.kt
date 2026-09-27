package uk.co.fireburn.lochssh.ui.keyboard

import uk.co.fireburn.lochssh.data.KeyboardAction
import uk.co.fireburn.lochssh.data.KeyboardButton
import uk.co.fireburn.lochssh.ui.terminal.TerminalKey

internal fun KeyboardButton.terminalKey(): TerminalKey? = when (action) {
    KeyboardAction.ESC -> TerminalKey.Esc
    KeyboardAction.TAB -> TerminalKey.Tab
    KeyboardAction.ENTER -> TerminalKey.Enter
    KeyboardAction.UP -> TerminalKey.Arrow('A')
    KeyboardAction.DOWN -> TerminalKey.Arrow('B')
    KeyboardAction.LEFT -> TerminalKey.Arrow('D')
    KeyboardAction.RIGHT -> TerminalKey.Arrow('C')
    KeyboardAction.HOME -> TerminalKey.Home
    KeyboardAction.END -> TerminalKey.End
    KeyboardAction.PAGE_UP -> TerminalKey.PgUp
    KeyboardAction.PAGE_DOWN -> TerminalKey.PgDn
    KeyboardAction.DELETE -> TerminalKey.Del
    KeyboardAction.BACKSPACE -> TerminalKey.Backspace
    KeyboardAction.TEXT -> TerminalKey.Text(text)
    else -> null
}
