package com.niki914.zafiro.chat.routing

/** A provider refusal is not permission to change credentials or enable billing. */
object ProviderBackoff {
    fun delayMillis(status: Int, body: String, retryAfter: String?): Long {
        val header = retryAfter?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val structured = Regex("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        val human = Regex("retry in (?:(\\d+)h)?(?:(\\d+)m)?([0-9.]+)s", RegexOption.IGNORE_CASE).find(body)?.let {
            (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 3600 + (it.groupValues[2].toDoubleOrNull() ?: 0.0) * 60 + (it.groupValues[3].toDoubleOrNull() ?: 0.0)
        }
        val zeroQuota = status == 429 && Regex("limit:\\s*0\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val seconds = listOfNotNull(header, structured, human).maxOrNull() ?: if (zeroQuota) 86400.0 else 60.0
        return (seconds.coerceIn(if (zeroQuota) 3600.0 else 1.0, 86400.0) * 1000).toLong()
    }
}
