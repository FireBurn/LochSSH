package uk.co.fireburn.lochssh.ui.terminal

import androidx.navigation.NavHostController
import uk.co.fireburn.lochssh.Routes

fun NavHostController.switchTerminal(sessionId: Long) {
    navigate(Routes.terminal(sessionId)) {
        popUpTo(Routes.HOSTS)
        launchSingleTop = true
    }
}
