package com.niki914.zafiro.chat.routing

import java.io.File
import java.security.MessageDigest

/** Pinned model weights only: never scripts, executable code, mutable main URLs or archive extraction. */
data class ModelArtifact(val id: String, val name: String, val url: String, val bytes: Long, val sha256: String,
    val extension: String = "litertlm") {
    fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == sha256
    }

    companion object {
        val guiOwlWeights = ModelArtifact("gui-owl-15-2b-q4", "GUI-Owl 1.5 2B · Q4_K_M",
            "https://huggingface.co/mradermacher/GUI-Owl-1.5-2B-Instruct-GGUF/resolve/a0592aa615540e507a918bc6281fdbfc1b441361/GUI-Owl-1.5-2B-Instruct.Q4_K_M.gguf",
            1107410432L, "5fd2c77781a14a91128928b5bd313252aab076b44682396af4bb2c99667f5ef5", "gguf")
        val guiOwlVision = ModelArtifact("gui-owl-15-2b-vision-q8", "GUI-Owl vision · Q8_0",
            "https://huggingface.co/mradermacher/GUI-Owl-1.5-2B-Instruct-GGUF/resolve/a0592aa615540e507a918bc6281fdbfc1b441361/GUI-Owl-1.5-2B-Instruct.mmproj-Q8_0.gguf",
            445053216L, "16748786911f630d0c14869ac28999f77715053a62b2a00d2327021bd6d2afb7", "gguf")
        val androidBrainArtifacts = listOf(guiOwlWeights, guiOwlVision)
        val candidates = listOf(
            ModelArtifact("qwen3-06-int4", "Qwen3 0.6B · INT4",
                "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
                344671744L, "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856"),
            ModelArtifact("dictalm3-17-int4", "DictaLM 3.0 1.7B · INT4",
                "https://huggingface.co/barakplasma/dictalm-3.0-1.7b-thinking-android/resolve/af1a8a70c0ccfc6877fae99bf9c9fea9df462c61/dictalm-3.0-1.7b-instruct-int4.litertlm",
                892583936L, "3ae341699d480d8bd0e5a285547b86634058441185fe1ba03982a78af94c0da6"),
        )
        val downloadable get() = candidates + androidBrainArtifacts
    }
}
