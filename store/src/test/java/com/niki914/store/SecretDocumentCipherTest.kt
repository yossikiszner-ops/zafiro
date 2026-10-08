package com.niki914.store

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class SecretDocumentCipherTest {
    private fun cipher() = SecretDocumentCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    @Test fun secretJsonRoundTripsWithoutPlaintextOnDisk() {
        val cipher = cipher()
        val json = """{"api_key":"test-secret-only","name":"עברית"}"""
        val encoded = cipher.encrypt("llm.saved_configs", json)
        assertFalse(encoded.contains("test-secret-only"))
        assertEquals(json, cipher.decrypt("llm.saved_configs", encoded))
        assertNotEquals(encoded, cipher.encrypt("llm.saved_configs", json))
    }
    @Test(expected = AEADBadTagException::class) fun swappedDocumentIsRejected() {
        val cipher = cipher()
        cipher.decrypt("tools.mcp.servers", cipher.encrypt("llm.saved_configs", "{}"))
    }
    @Test(expected = AEADBadTagException::class) fun modifiedCiphertextIsRejected() {
        val cipher = cipher()
        val encoded = cipher.encrypt("llm.saved_configs", "{}")
        val bytes = Base64.getDecoder().decode(encoded.removePrefix(SecretDocumentCipher.PREFIX))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        cipher.decrypt("llm.saved_configs", SecretDocumentCipher.PREFIX + Base64.getEncoder().encodeToString(bytes))
    }
    @Test(expected = AEADBadTagException::class) fun differentKeyCannotDecrypt() {
        cipher().decrypt("llm.saved_configs", cipher().encrypt("llm.saved_configs", "{}"))
    }
}
