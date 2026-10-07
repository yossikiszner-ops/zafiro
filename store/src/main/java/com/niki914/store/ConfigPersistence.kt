package com.niki914.store

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

internal object ConfigPersistence {

    fun fileFor(context: Context, descriptor: StoreDescriptor): File {
        return File(context.filesDir, descriptor.relativePath)
    }

    fun readJson(context: Context, descriptor: StoreDescriptor): String? {
        val file = fileFor(context, descriptor)
        if (!file.exists()) return null
        val text = file.readText(Charsets.UTF_8)
        if (!containsSecrets(descriptor)) return text
        if (text.startsWith(SecretDocumentCipher.PREFIX)) {
            return SecretDocumentCipher(AndroidConfigurationKey.get()).decrypt(descriptor.id, text)
        }
        // Migrate only valid existing JSON. Invalid input stays intact for the repository to diagnose.
        if (text.isNotBlank() && runCatching { JSONObject(text) }.isSuccess) {
            writeJson(context, descriptor, text)
        }
        return text
    }

    fun writeJson(context: Context, descriptor: StoreDescriptor, json: String) {
        val text = if (containsSecrets(descriptor)) {
            SecretDocumentCipher(AndroidConfigurationKey.get()).encrypt(descriptor.id, json)
        } else json
        writeTextAtomically(fileFor(context, descriptor), text)
    }

    private fun containsSecrets(descriptor: StoreDescriptor): Boolean = descriptor.id in setOf(
        StoreDescriptorRegistry.LLM_CONFIGS_ID,
        StoreDescriptorRegistry.LOCAL_SETTINGS_ID,
        StoreDescriptorRegistry.TOOLS_MCP_SERVERS_ID,
    )

    private fun writeTextAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val atomicFile = AtomicFile(target)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (t: Throwable) {
            stream?.let { atomicFile.failWrite(it) }
            throw t
        }
    }
}
