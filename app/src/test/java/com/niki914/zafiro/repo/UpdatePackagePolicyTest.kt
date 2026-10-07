package com.niki914.zafiro.repo

import org.junit.Assert.*
import org.junit.Test

class UpdatePackagePolicyTest {
    @Test fun updatesRequireSameIdentityAndSignerAndNewerVersion() {
        fun accepts(name: String = "zafiro", version: Long = 13, signers: Set<String> = setOf("trusted")) =
            UpdatePackagePolicy.accepts(name, "zafiro", version, 12, signers, setOf("trusted"))
        assertTrue(accepts())
        assertFalse(accepts(name = "other"))
        assertFalse(accepts(version = 12))
        assertFalse(accepts(version = 11))
        assertFalse(accepts(signers = emptySet()))
        assertFalse(accepts(signers = setOf("different")))
    }
}
