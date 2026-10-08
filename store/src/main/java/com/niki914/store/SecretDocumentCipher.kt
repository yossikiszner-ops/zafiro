package com.niki914.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption binds each document to its store identity. No plaintext fallback. */
internal class SecretDocumentCipher(private val key: SecretKey) {
    fun encrypt(storeId: String, plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(storeId.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        require(cipher.iv.size == IV_BYTES)
        return PREFIX + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }
    fun decrypt(storeId: String, envelope: String): String {
        require(envelope.startsWith(PREFIX)) { "Unsupported secure configuration format" }
        val bytes = Base64.getDecoder().decode(envelope.removePrefix(PREFIX))
        require(bytes.size >= IV_BYTES + 16) { "Invalid secure configuration" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        cipher.updateAAD(storeId.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), StandardCharsets.UTF_8)
    }
    companion object {
        const val PREFIX = "zafiro:aes-gcm:v1:"
        private const val IV_BYTES = 12
    }
}

internal object AndroidConfigurationKey {
    private const val ALIAS = "zafiro.configuration.v1"
    @Volatile private var cached: SecretKey? = null
    @Synchronized fun get(): SecretKey {
        cached?.let { return it }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build())
        }.generateKey()
        cached = key
        return key
    }
}
