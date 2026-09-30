package uk.co.fireburn.lochssh.ui.terminal

import android.view.KeyEvent

object SessionShortcuts {
    fun direction(event: KeyEvent, enabled: Boolean): Int? {
        if (!enabled || event.action != KeyEvent.ACTION_DOWN || !event.isCtrlPressed ||
            !event.isAltPressed || event.isShiftPressed || event.isMetaPressed) return null
        return when (event.keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> -1
            KeyEvent.KEYCODE_PAGE_DOWN -> 1
            else -> null
        }
    }

    fun destination(ids: List<Long>, current: Long, direction: Int): Long? {
        require(direction == -1 || direction == 1)
        if (ids.size < 2) return null
        val index = ids.indexOf(current)
        if (index < 0) return if (direction > 0) ids.first() else ids.last()
        return ids[(index + direction + ids.size) % ids.size]
    }
}
