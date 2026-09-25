package uk.co.fireburn.lochssh.ui.identities

import uk.co.fireburn.lochssh.data.db.AuthTypes
import uk.co.fireburn.lochssh.data.db.IdentityEntity

internal object IdentityInput {
    fun error(
        name: String,
        username: String,
        authType: String,
        keySource: String,
        keyPath: String,
        keyMaterial: String,
        secret: String,
        existing: IdentityEntity?
    ): String? = when {
        name.isBlank() -> "Enter a name"
        username.isBlank() -> "Enter a username"
        authType == AuthTypes.PASSWORD && secret.isBlank() &&
            !(existing?.authType == AuthTypes.PASSWORD && existing.secretRef != null) ->
            "Enter a password"
        authType == AuthTypes.PUBLIC_KEY && keySource == KeySources.PATH && keyPath.isBlank() ->
            "Enter a private key path"
        authType == AuthTypes.PUBLIC_KEY && keySource == KeySources.PASTE && keyMaterial.isBlank() &&
            existing?.keyMaterialRef == null ->
            "Paste or generate a private key"
        else -> null
    }
}
