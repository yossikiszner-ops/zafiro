package com.niki914.zafiro.app.localai

// Protects against recognizing corrupt, incomplete or unsupported files as installed AI models.
import com.niki914.zafiro.chat.routing.ModelArtifact
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class LocalModelDiscoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "verified test model".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private val artifact = ModelArtifact("fixture", "Fixture", "https://example.com/model", bytes.size.toLong(), hash)

    @Test fun recognizesVerifiedModelWithDifferentFilenameInReadableStorage() {
        val file = File(temporary.root, "downloaded.LITERTLM").apply { writeBytes(bytes) }
        assertEquals(file, LocalModelDiscovery.discover(listOf(temporary.root), listOf(artifact))[artifact])
    }

    @Test fun rejectsCorruptModelEvenWhenFilenameAndSizeMatch() {
        File(temporary.root, "fixture.litertlm").writeBytes(ByteArray(bytes.size))
        assertTrue(LocalModelDiscovery.discover(listOf(temporary.root), listOf(artifact)).isEmpty())
    }

    @Test fun doesNotOfferPartialOrDifferentRuntimeArtifacts() {
        File(temporary.root, "fixture.partial").writeBytes(bytes)
        File(temporary.root, "fixture.gguf").writeBytes(bytes)
        assertTrue(LocalModelDiscovery.discover(listOf(temporary.root), listOf(artifact)).isEmpty())
    }

    @Test fun missingOrUnreadableRootDoesNotPreventCheckingOtherRoots() {
        val file = File(temporary.root, "valid.litertlm").apply { writeBytes(bytes) }
        val roots = listOf(File(temporary.root, "missing"), temporary.root, temporary.root)
        assertEquals(file, LocalModelDiscovery.discover(roots, listOf(artifact))[artifact])
    }
}
