package uk.co.fireburn.lochssh.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppLockCardAndroidTest {
    @get:Rule val ui = createComposeRule()

    @Test fun refreshesDeviceLockAvailabilityWhenReturning() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        var available = false
        var saved: Boolean? = null
        ui.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        ui.setContent { MaterialTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AppLockCard(false, { saved = it }, { available })
            }
        } }
        ui.onNodeWithContentDescription("App lock").assertIsNotEnabled()
        ui.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            available = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        ui.onNodeWithContentDescription("App lock").assertIsEnabled().performClick()
        ui.runOnIdle {
            assertEquals(true, saved)
            owner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    @Test fun canDisableAnExistingLockWhenTheDeviceLockIsUnavailable() {
        var saved: Boolean? = null
        ui.setContent { MaterialTheme { AppLockCard(true, { saved = it }, { false }) } }
        ui.onNodeWithContentDescription("App lock").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(false, saved) }
    }
}
