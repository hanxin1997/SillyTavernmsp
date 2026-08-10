package com.sillytavern.eink.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PersistentCookieJar(context: Context, accountKey: String) : CookieJar {
    private val storageKey = serverKey(accountKey)
    private val preferences = context.getSharedPreferences("cookies_$storageKey", Context.MODE_PRIVATE)
    private val lock = Any()
    private var cookies = readCookies()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val merged = this.cookies.filter { it.expiresAt > now }.toMutableList()
        for (cookie in cookies) {
            merged.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > now) merged.add(cookie)
        }
        this.cookies = merged
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val active = cookies.filter { it.expiresAt > now }
        if (active.size != cookies.size) {
            cookies = active
            persist()
        }
        active.filter { it.matches(url) }
    }

    fun clear() = synchronized(lock) {
        cookies = emptyList()
        preferences.edit().clear().apply()
    }

    private fun persist() {
        val array = JSONArray()
        cookies.forEach { cookie ->
            array.put(JSONObject().apply {
                put("name", cookie.name)
                put("value", cookie.value)
                put("expiresAt", cookie.expiresAt)
                put("domain", cookie.domain)
                put("path", cookie.path)
                put("secure", cookie.secure)
                put("httpOnly", cookie.httpOnly)
                put("hostOnly", cookie.hostOnly)
            })
        }
        preferences.edit().putString("cookies", encrypt(array.toString())).apply()
    }

    private fun readCookies(): List<Cookie> {
        val encrypted = preferences.getString("cookies", null) ?: return emptyList()
        val raw = decrypt(encrypted) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    val builder = Cookie.Builder()
                        .name(value.getString("name"))
                        .value(value.getString("value"))
                        .expiresAt(value.getLong("expiresAt"))
                        .path(value.getString("path"))
                    if (value.optBoolean("hostOnly", true)) builder.hostOnlyDomain(value.getString("domain")) else builder.domain(value.getString("domain"))
                    if (value.optBoolean("secure")) builder.secure()
                    if (value.optBoolean("httpOnly")) builder.httpOnly()
                    add(builder.build())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("st_eink_$storageKey", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            "st_eink_$storageKey",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = ByteArray(1 + cipher.iv.size + cipher.getOutputSize(value.toByteArray().size))
        payload[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(payload, 1)
        val encrypted = cipher.doFinal(value.toByteArray())
        encrypted.copyInto(payload, 1 + cipher.iv.size)
        return Base64.encodeToString(payload.copyOf(1 + cipher.iv.size + encrypted.size), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val ivSize = payload.first().toInt() and 0xff
        require(ivSize in 12..16 && payload.size > ivSize + 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload.copyOfRange(1, 1 + ivSize)))
        String(cipher.doFinal(payload.copyOfRange(1 + ivSize, payload.size)))
    }.getOrNull()

    companion object {
        private fun serverKey(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.trimEnd('/').toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(20)
    }
}
