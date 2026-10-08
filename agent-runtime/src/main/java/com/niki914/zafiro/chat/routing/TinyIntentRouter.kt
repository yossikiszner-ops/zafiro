package com.niki914.zafiro.chat.routing

import kotlin.math.sqrt

/** Tiny bilingual prototype classifier. Similarity is a routing hint, never action authorization. */
object TinyIntentRouter {
    enum class Domain { Android, Skill, Mcp, Web, Reasoning }
    data class Decision(val domain: Domain, val similarity: Double, val margin: Double)
    private val samples = mapOf(
        Domain.Android to listOf("open app camera whatsapp settings bluetooth", "go back previous screen home", "send message contact type keyboard volume flashlight",
            "פתח תפתח אפליקציה מצלמה וואטסאפ ספוטיפיי הגדרות בלוטוס", "תחזור אחורה למסך הקודם הבית", "שלח תשלח תכתוב הודעה לאמא ליוסי ווליום פנס"),
        Domain.Skill to listOf("run install skill", "הפעל התקן מיומנות"),
        Domain.Mcp to listOf("search connected mcp server", "חפש שרת mcp מחובר"),
        Domain.Web to listOf("search web internet research weather news", "חפש באינטרנט מחקר מזג אוויר חדשות"),
        Domain.Reasoning to listOf("explain why reason summarize write story", "תסביר למה סכם סיפור חשיבה"),
    )
    private fun features(text: String): Map<String, Double> {
        val normalized = " " + text.lowercase().replace(Regex("[\\p{P}\\p{S}]+"), " ").replace(Regex("\\s+"), " ") + " "
        val result = HashMap<String, Double>()
        for (size in 2..3) for (index in 0..normalized.length - size) {
            val feature = normalized.substring(index, index + size)
            result[feature] = (result[feature] ?: 0.0) + 1.0
        }
        return result
    }
    private val vectors = samples.mapValues { (_, text) -> text.map(::features) }
    private fun similarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        val scale = sqrt(a.values.sumOf { it * it } * b.values.sumOf { it * it })
        return if (scale == 0.0) 0.0 else a.entries.sumOf { it.value * (b[it.key] ?: 0.0) } / scale
    }
    fun classify(text: String): Decision {
        val input = features(text.take(1024))
        val scores = vectors.map { (domain, prototypes) -> domain to prototypes.maxOf { similarity(input, it) } }.sortedByDescending { it.second }
        val first = scores.first()
        return Decision(first.first, first.second, first.second - scores[1].second)
    }
}
