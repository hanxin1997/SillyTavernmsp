package com.sillytavern.eink.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sillytavern.eink.model.StoredProfile
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores one profile and its credentials. Passwords never enter a normal
 * preference value, log message, Intent, or JavaScript context.
 */
class CredentialStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadProfile(): StoredProfile? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null) ?: return migrateLegacyProfile()
        return StoredProfile(
            baseUrl = baseUrl,
            handle = preferences.getString(KEY_HANDLE, "").orEmpty(),
            allowPrivateLanHttp = preferences.getBoolean(KEY_ALLOW_LAN_HTTP, false),
        )
    }

    fun saveProfile(profile: StoredProfile) {
        preferences.edit()
            .putString(KEY_BASE_URL, profile.baseUrl.trimEnd('/'))
            .putString(KEY_HANDLE, profile.handle)
            .putBoolean(KEY_ALLOW_LAN_HTTP, profile.allowPrivateLanHttp)
            .apply()
    }

    fun savePassword(password: String) {
        val encrypted = encrypt(password.toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(KEY_PASSWORD, encrypted).apply()
    }

    fun loadPassword(): String? = preferences.getString(KEY_PASSWORD, null)?.let { encoded ->
        runCatching { String(decrypt(encoded), StandardCharsets.UTF_8) }.getOrNull()
    }

    fun clearPassword() = preferences.edit().remove(KEY_PASSWORD).apply()

    fun saveBasicAuth(host: String, realm: String, username: String, password: String) {
        val key = basicKey(host, realm)
        val value = JSONObject().put("username", username).put("password", password).toString()
        preferences.edit().putString("basic_$key", encrypt(value.toByteArray(StandardCharsets.UTF_8))).apply()
    }

    fun loadBasicAuth(host: String, realm: String): Pair<String, String>? {
        val encoded = preferences.getString("basic_${basicKey(host, realm)}", null) ?: return null
        return runCatching {
            val json = JSONObject(String(decrypt(encoded), StandardCharsets.UTF_8))
            json.getString("username") to json.getString("password")
        }.getOrNull()
    }

    fun clearBasicAuth(host: String, realm: String) =
        preferences.edit().remove("basic_${basicKey(host, realm)}").apply()

    fun clearAll() = preferences.edit().clear().apply()

    private fun migrateLegacyProfile(): StoredProfile? {
        val legacy = context.getSharedPreferences("server_profile", Context.MODE_PRIVATE)
        val baseUrl = legacy.getString("base_url", null) ?: return null
        val profile = StoredProfile(
            baseUrl = baseUrl,
            handle = legacy.getString("handle", "").orEmpty(),
            allowPrivateLanHttp = legacy.getBoolean("allow_private_lan_http", false),
        )
        saveProfile(profile)
        legacy.edit().clear().apply()
        return profile
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value)
        val payload = ByteArray(1 + cipher.iv.size + encrypted.size)
        payload[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(payload, 1)
        encrypted.copyInto(payload, 1 + cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): ByteArray {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val ivSize = payload.first().toInt() and 0xff
        require(ivSize in 12..16 && payload.size > ivSize + 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, payload.copyOfRange(1, ivSize + 1)),
        )
        return cipher.doFinal(payload.copyOfRange(ivSize + 1, payload.size))
    }

    private fun basicKey(host: String, realm: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${host.lowercase()}\u0000$realm".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(24)

    companion object {
        private const val PREFERENCES = "browser_credentials"
        private const val KEYSTORE_ALIAS = "st_eink_browser_credentials"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_HANDLE = "handle"
        private const val KEY_ALLOW_LAN_HTTP = "allow_private_lan_http"
        private const val KEY_PASSWORD = "password"
    }
}
