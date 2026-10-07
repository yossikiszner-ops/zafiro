package com.niki914.zafiro.repo

import org.junit.Assert.*
import org.junit.Test

class CustomUpdateVersionsTest {
    @Test fun customRevisionIsComparedAndOfficialDoesNotReplaceCustomAtSameBase() {
        assertTrue(CustomUpdateVersions.newer("1.6.0-zafiro.2", "1.6.0-zafiro.1"))
        assertFalse(CustomUpdateVersions.newer("1.6.0", "1.6.0-zafiro.1"))
        assertFalse(CustomUpdateVersions.newer("1.6.0-zafiro.1", "1.6.0-zafiro.1"))
        assertTrue(CustomUpdateVersions.newer("1.6.1-zafiro.1", "1.6.0-zafiro.9"))
        assertTrue(CustomUpdateVersions.newer("1.6.0-zafiro.1", "1.5.2"))
    }
}
