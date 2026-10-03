package uk.co.fireburn.lochssh.ui.hosts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class HostProxySettingsAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosesProxyAndPasswordIdentityThenClearsDisabledFields() {
        var fields by mutableStateOf(HostProxyFields())
        val identities = listOf(IdentityEntity(1, "Proxy login", "user", AuthTypes.PASSWORD),
            IdentityEntity(2, "Private key", "user", AuthTypes.PUBLIC_KEY))
        compose.setContent { MaterialTheme { HostProxySettings(fields, identities, { fields = it }, null) } }
        compose.onNodeWithText("Proxy address").assertDoesNotExist()
        compose.onNodeWithText("HTTP CONNECT").performClick()
        compose.onNodeWithText("Proxy address").performTextInput("proxy.invalid")
        compose.onNodeWithText("Choose proxy identity").performClick()
        compose.onNodeWithText("Private key").assertDoesNotExist()
        compose.onNodeWithText("Proxy login").performClick()
        compose.runOnIdle {
            assertEquals(HostProxyFields("HTTP", "proxy.invalid", "1080", 1), fields)
        }
        compose.onNodeWithText("Proxy login: Proxy login").assertExists()
        compose.onNodeWithText("None").performClick()
        compose.runOnIdle { assertEquals(HostProxyFields(), fields) }
        compose.onNodeWithText("Proxy address").assertDoesNotExist()
    }

    @Test fun showsErrorsAndAllowsAnAnonymousSocksProxy() {
        var fields by mutableStateOf(HostProxyFields("SOCKS5", "proxy.invalid", "1080", 9))
        compose.setContent { MaterialTheme {
            HostProxySettings(fields, emptyList(), { fields = it }, "The proxy identity no longer exists")
        } }
        compose.onNodeWithText("Proxy login: Missing identity").assertExists()
        compose.onNodeWithText("The proxy identity no longer exists").assertExists()
        compose.onNodeWithText("Choose proxy identity").performClick()
        compose.onNodeWithText("None (no proxy login)").performClick()
        compose.runOnIdle { assertEquals(null, fields.identityId); assertEquals("SOCKS5", fields.type) }
    }

    @Test fun warningRequiresConfirmationAndCancellationDoesNotSave() {
        var show by mutableStateOf(true)
        var saves = 0
        compose.setContent { MaterialTheme {
            if (show) ProxyLoginWarning(onConfirm = { saves++; show = false }, onDismiss = { show = false })
        } }
        compose.onNodeWithText("Save this proxy login?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, saves); show = true }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(false, show) }
    }
}
