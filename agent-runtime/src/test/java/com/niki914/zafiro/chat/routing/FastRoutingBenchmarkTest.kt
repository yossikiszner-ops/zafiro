package com.niki914.zafiro.chat.routing

// Measures pure route resolution, not Android latency; unsupported tasks must not produce partial plans.
import org.junit.Assert.*
import org.junit.Test

class FastRoutingBenchmarkTest {
    @Test fun bilingualCommandCorpusRoutesWithoutModelInference() {
        val fixtures = listOf(
            "פתח וואטסאפ" to "DIRECT",
            "שלח לאמא לילה טוב" to "MESSAGE",
            "תפתח ספוטיפיי" to "DIRECT",
            "תפתח הגדרות ותיכנס לבלוטוס" to "DIRECT",
            "תחזור למסך הבית" to "DIRECT",
            "פתח את המצלמה" to "DIRECT",
            "תחפש בהגדרות מצב חיסכון בסוללה" to "DIRECT",
            "תשלח לאמא בwhatsapp לילה טוב" to "MESSAGE",
            "Send Mom on WhatsApp good night" to "MESSAGE",
            "Please open Camera!" to "DIRECT",
            "go back" to "DIRECT",
            "lower volume" to "DIRECT",
            "תפתח whatsapp ותכתוב ליוסי שאני בדרך" to "ESCALATE",
            "תפתח ספוטיפיי ותשים מוזיקה" to "ESCALATE",
            "תמצא את יוסי בוואטסאפ ותכתוב לו שאני בדרך" to "ESCALATE",
            "תסביר לי למה הסוללה נגמרת" to "ESCALATE",
            "אל תשלח לאמא בוואטסאפ לילה טוב" to "ESCALATE",
            "שלח לאמא ולאבא בוואטסאפ לילה טוב" to "ESCALATE",
            "Do not open WhatsApp" to "ESCALATE",
        )
        fun route(text: String) = when {
            DirectCommand.parse(text) != null -> "DIRECT"
            LocalMessagePlan.parse(text) != null -> "MESSAGE"
            else -> "ESCALATE"
        }
        fixtures.forEach { (text, expected) -> assertEquals(text, expected, route(text)) }
        repeat(100) { fixtures.forEach { route(it.first) } }
        val start = System.nanoTime()
        repeat(200) { fixtures.forEach { route(it.first) } }
        val averageMicros = (System.nanoTime() - start) / (200.0 * fixtures.size * 1000)
        println("LOCAL_ROUTER_BENCHMARK samples=" + fixtures.size + " correct=" + fixtures.size +
            " mean_route_us=" + averageMicros + " environment=CI_JVM not_phone=true")
    }
}
