package com.sabimoni.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreApiKeyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ApiKeyStore {

    override fun observeHasKey(): Flow<Boolean> =
        dataStore.data.map { it[CIPHERTEXT_KEY] != null }

    override suspend fun get(): String? {
        val encoded = dataStore.data.first()[CIPHERTEXT_KEY] ?: return null
        val blob = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (blob.size <= IV_LENGTH) return null

        // A Keystore key can be lost by a device credential reset or a data restore.
        // Treat that as "no key" so setup can prompt again instead of crashing.
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, blob, 0, IV_LENGTH),
            )
            cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    override suspend fun put(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + ciphertext
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        dataStore.edit { it[CIPHERTEXT_KEY] = encoded }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(CIPHERTEXT_KEY) }
        runCatching {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "sabimoni.ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
        val CIPHERTEXT_KEY = stringPreferencesKey("ai_api_key_ciphertext")
    }
}
