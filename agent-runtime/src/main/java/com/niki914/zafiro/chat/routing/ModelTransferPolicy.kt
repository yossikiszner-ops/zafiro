package com.niki914.zafiro.chat.routing

/** Resume/storage decisions; hash verification remains mandatory before atomic installation. */
object ModelTransferPolicy {
    private const val reserve = 64L * 1024 * 1024
    fun storageAvailable(available: Long, partial: Long, expected: Long): Boolean =
        expected > 0 && partial in 0..expected && available >= expected - partial + reserve
    /** Null rejects; false safely restarts on a full HTTP 200 response. */
    fun resume(status: Int, contentRange: String?, offset: Long, expected: Long): Boolean? {
        if (offset !in 0 until expected) return null
        if (status == 200) return false
        if (status != 206) return null
        val range = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$").matchEntire(contentRange.orEmpty()) ?: return null
        val start = range.groupValues[1].toLongOrNull() ?: return null
        val end = range.groupValues[2].toLongOrNull() ?: return null
        val total = range.groupValues[3].toLongOrNull() ?: return null
        return true.takeIf { start == offset && end == expected - 1 && total == expected }
    }
}
