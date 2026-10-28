package uk.co.fireburn.lochssh.data

import java.util.Base64

enum class KeyboardAction(val label: String) {
    ESC("ESC"), TAB("TAB"), CTRL("CTRL"), ALT("ALT"), FN("FN"),
    UP("▲"), DOWN("▼"), LEFT("◄"), RIGHT("►"),
    HOME("HOME"), END("END"), PAGE_UP("PGUP"), PAGE_DOWN("PGDN"),
    DELETE("DEL"), BACKSPACE("Backspace"), ENTER("Enter"),
    FONT_UP("Aa+"), FONT_DOWN("Aa-"), KEYBOARD("Keyboard"), TEXT("Text")
}

data class KeyboardButton(val action: KeyboardAction, val text: String = "") {
    init {
        require(if (action == KeyboardAction.TEXT) {
            text.isNotEmpty() && text.length <= MAX_TEXT_LENGTH && text.none { it.code < 32 || it.code == 127 }
        } else text.isEmpty())
    }

    val label: String get() = if (action == KeyboardAction.TEXT) text else action.label

    companion object {
        const val MAX_TEXT_LENGTH = 16
    }
}

data class KeyboardLayout(val rows: List<List<KeyboardButton>>) {
    init {
        require(rows.size in 1..MAX_ROWS && rows.all { it.size in 1..MAX_KEYS })
    }

    fun encode(): String = rows.joinToString("\n") { row ->
        row.joinToString(",") { button ->
            button.action.name + ":" + Base64.getEncoder().encodeToString(button.text.toByteArray(Charsets.UTF_8))
        }
    }

    companion object {
        const val MAX_ROWS = 4
        const val MAX_KEYS = 10

        val DEFAULT = KeyboardLayout(listOf(
            listOf(
                KeyboardButton(KeyboardAction.ESC),
                KeyboardButton(KeyboardAction.TEXT, "/"),
                KeyboardButton(KeyboardAction.TEXT, "|"),
                KeyboardButton(KeyboardAction.TEXT, "-"),
                KeyboardButton(KeyboardAction.UP), KeyboardButton(KeyboardAction.DELETE),
                KeyboardButton(KeyboardAction.HOME), KeyboardButton(KeyboardAction.PAGE_UP),
                KeyboardButton(KeyboardAction.FONT_UP), KeyboardButton(KeyboardAction.ENTER)
            ),
            listOf(
                KeyboardButton(KeyboardAction.TAB), KeyboardButton(KeyboardAction.CTRL),
                KeyboardButton(KeyboardAction.ALT), KeyboardButton(KeyboardAction.LEFT),
                KeyboardButton(KeyboardAction.DOWN), KeyboardButton(KeyboardAction.RIGHT),
                KeyboardButton(KeyboardAction.END), KeyboardButton(KeyboardAction.PAGE_DOWN),
                KeyboardButton(KeyboardAction.FONT_DOWN), KeyboardButton(KeyboardAction.KEYBOARD)
            )
        ))

        fun decode(value: String?): KeyboardLayout = runCatching {
            require(value != null && value.length <= 4096)
            KeyboardLayout(value.split('\n').map { row ->
                row.split(',').map { encoded ->
                    val parts = encoded.split(':', limit = 2)
                    require(parts.size == 2)
                    KeyboardButton(
                        KeyboardAction.valueOf(parts[0]),
                        Base64.getDecoder().decode(parts[1]).toString(Charsets.UTF_8)
                    )
                }
            })
        }.getOrDefault(DEFAULT)
    }
}
