package uk.co.fireburn.lochssh.ui.terminal

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.fireburn.lochssh.Routes

class TerminalNavigationAndroidTest {
    @Test
    fun clearsOlderTerminalPagesBeforeSwitching() = withController { nav ->
        nav.navigate(Routes.terminal(11))
        nav.navigate(Routes.terminal(12))
        nav.switchTerminal(13)

        assertEquals(13L, nav.currentBackStackEntry?.arguments?.getLong("sessionId"))
        assertEquals(Routes.HOSTS, nav.previousBackStackEntry?.destination?.route)
        assertTrue(nav.popBackStack())
        assertEquals(Routes.HOSTS, nav.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun repeatedSwitchesKeepOnlyTheCurrentTerminal() = withController { nav ->
        nav.navigate(Routes.terminal(11))
        repeat(10) { nav.switchTerminal(12 + it.toLong()) }

        assertEquals(21L, nav.currentBackStackEntry?.arguments?.getLong("sessionId"))
        assertEquals(Routes.HOSTS, nav.previousBackStackEntry?.destination?.route)
    }

    private fun withController(check: (NavHostController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val store = ViewModelStore()
            try {
                val owner = object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this).apply {
                        currentState = Lifecycle.State.RESUMED
                    }
                }
                val nav = NavHostController(instrumentation.targetContext)
                nav.setViewModelStore(store)
                nav.setLifecycleOwner(owner)
                nav.navigatorProvider.addNavigator(ComposeNavigator())
                nav.graph = nav.createGraph(startDestination = Routes.HOSTS) {
                    composable(Routes.HOSTS) {}
                    composable(Routes.TERMINAL,
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {}
                }
                check(nav)
            } catch (error: Throwable) {
                failure = error
            } finally {
                store.clear()
            }
        }
        failure?.let { throw it }
    }
}
