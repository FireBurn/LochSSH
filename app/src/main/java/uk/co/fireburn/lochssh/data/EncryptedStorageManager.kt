package uk.co.fireburn.lochssh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedStorageManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun newReference(): String = UUID.randomUUID().toString()

    fun putSecret(reference: String, value: String) {
        if (!prefs.edit().putString(reference, value).commit()) {
            throw IOException("Could not save secret")
        }
    }

    fun getSecret(reference: String): String? = prefs.getString(reference, null)

    fun deleteSecret(reference: String) {
        if (!prefs.edit().remove(reference).commit()) {
            throw IOException("Could not remove secret")
        }
    }

    companion object {
        private const val FILE_NAME = "lochssh_secrets"
    }
}
