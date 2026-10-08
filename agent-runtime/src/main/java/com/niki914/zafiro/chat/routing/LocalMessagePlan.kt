package com.niki914.zafiro.chat.routing

/** Closed command grammar, not fuzzy guessing. Unsupported language escalates before any action. */
internal data class LocalMessagePlan(val recipient: String, val content: String) {
    companion object {
        fun parse(input: String): LocalMessagePlan? {
            val text = input.trim()
            val patterns = listOf(
                Regex("^(?:שלח|תשלח) (?:הודעת? )?(?:וואטסאפ|ואטסאפ) ל(.{1,48}?) (?:עם המילים|עם הטקסט) (.{1,500})$"),
                Regex("^(?:שלח|תשלח) ל(.{1,48}?) ב(?:וואטסאפ|ואטסאפ|whatsapp) (.{1,500})$", RegexOption.IGNORE_CASE),
                Regex("^(?:send|tell) (.{1,48}?) (?:on|via) whatsapp (.{1,500})$", RegexOption.IGNORE_CASE),
                Regex("^(?:שלח|תשלח) ל(אמא|אבא)(?: שלי)? (?!ב(?:וואטסאפ|ואטסאפ|whatsapp)(?: |$)|ול|וגם|and )(.{1,500})$", RegexOption.IGNORE_CASE),
            )
            val match = patterns.firstNotNullOfOrNull { it.matchEntire(text) } ?: return null
            val recipient = match.groupValues[1].trim().removeSuffix(" שלי").trim()
            val content = match.groupValues[2].trim()
            if (recipient.isBlank() || content.isBlank() || recipient.contains('\n') ||
                Regex("(?:\\band\\b|ואז|וגם| ולא)", RegexOption.IGNORE_CASE).containsMatchIn(recipient)) return null
            return LocalMessagePlan(recipient, content)
        }
    }
}
