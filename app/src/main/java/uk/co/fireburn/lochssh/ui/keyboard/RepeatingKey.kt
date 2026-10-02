package uk.co.fireburn.lochssh.ui.keyboard

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Modifier.repeatingKey(enabled: Boolean, onRelease: () -> Unit, onClick: () -> Unit): Modifier {
    val action = rememberUpdatedState(onClick)
    val released = rememberUpdatedState(onRelease)
    if (!enabled) return this
    return semantics {
        role = Role.Button
        onClick { action.value(); released.value(); true }
    }.pointerInput(Unit) {
        detectTapGestures(onPress = {
            coroutineScope {
                action.value()
                val repeat = launch {
                    delay(400)
                    while (true) {
                        action.value()
                        delay(80)
                    }
                }
                try {
                    tryAwaitRelease()
                } finally {
                    repeat.cancel()
                    released.value()
                }
            }
        })
    }
}
