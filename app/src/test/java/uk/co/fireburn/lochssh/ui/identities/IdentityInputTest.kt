package uk.co.fireburn.lochssh.ui.identities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

class IdentityInputTest {
    @Test
    fun requiresNameAndUsername() {
        assertEquals("Enter a name", error(name = ""))
        assertEquals("Enter a username", error(username = ""))
    }

    @Test
    fun requiresPasswordForNewPasswordIdentity() {
        assertEquals("Enter a password", error())
        assertNull(error(secret = "password"))
        assertNull(error(existing = identity(AuthTypes.PASSWORD, secretRef = "stored")))
        assertEquals("Enter a password", error(existing = identity(AuthTypes.NONE, secretRef = "stored")))
    }

    @Test
    fun requiresSelectedPrivateKeySource() {
        assertEquals("Enter a private key path", error(authType = AuthTypes.PUBLIC_KEY))
        assertNull(error(authType = AuthTypes.PUBLIC_KEY, keyPath = "/key"))
        assertEquals(
            "Paste or generate a private key",
            error(authType = AuthTypes.PUBLIC_KEY, keySource = KeySources.PASTE)
        )
        assertNull(error(authType = AuthTypes.PUBLIC_KEY, keySource = KeySources.PASTE, keyMaterial = "key"))
        assertNull(error(
            authType = AuthTypes.PUBLIC_KEY,
            keySource = KeySources.PASTE,
            existing = identity(AuthTypes.PUBLIC_KEY, keyMaterialRef = "stored")
        ))
    }

    @Test
    fun allowsIdentityWithoutPasswordOrKey() {
        assertNull(error(authType = AuthTypes.NONE))
    }

    private fun error(
        name: String = "server",
        username: String = "user",
        authType: String = AuthTypes.PASSWORD,
        keySource: String = KeySources.PATH,
        keyPath: String = "",
        keyMaterial: String = "",
        secret: String = "",
        existing: IdentityEntity? = null
    ): String? = IdentityInput.error(
        name, username, authType, keySource, keyPath, keyMaterial, secret, existing
    )

    private fun identity(
        authType: String,
        secretRef: String? = null,
        keyMaterialRef: String? = null
    ) = IdentityEntity(
        name = "server",
        username = "user",
        authType = authType,
        secretRef = secretRef,
        keyMaterialRef = keyMaterialRef
    )
}
