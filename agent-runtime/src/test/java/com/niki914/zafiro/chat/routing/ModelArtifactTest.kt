package com.niki914.zafiro.chat.routing

// Downloaded weights must fail closed on corruption, truncation and swapped artifacts.
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModelArtifactTest {
    @Test fun corruptAndPartialWeightsAreRejected() {
        val file = File.createTempFile("model-integrity", ".litertlm")
        try {
            val artifact = ModelArtifact("test", "Test", "https://example.com/weights", 3,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            file.writeText("abc")
            assertTrue(artifact.verify(file))
            file.writeText("abd")
            assertFalse(artifact.verify(file))
            file.writeText("ab")
            assertFalse(artifact.verify(file))
            file.delete()
            assertFalse(artifact.verify(file))
        } finally { file.delete() }
    }
}
