package com.niki914.zafiro.chat.routing

/** Device measurements only. A missing measurement is never reported as zero or success. */
object AndroidBrainBenchmark {
    const val guiOwlModel = "mPLUG/GUI-Owl-1.5-2B-Instruct"
    val candidates = listOf("accessibility", "qwen3-06-int4", "dictalm3-17-int4", guiOwlModel)
    enum class InputMode { Accessibility, Screenshot, Combined }
    data class Task(val id: String, val instruction: String, val mode: InputMode,
        val multiStep: Boolean = false, val recovery: Boolean = false)
    val tasks = listOf(
        Task("he-ground", "מצא את כפתור החיפוש במסך", InputMode.Screenshot),
        Task("en-ground", "Locate the search button on this screen", InputMode.Screenshot),
        Task("he-type", "לחץ על שדה החיפוש והקלד ירושלים", InputMode.Combined, true),
        Task("en-type", "Tap search and type Jerusalem", InputMode.Combined, true),
        Task("he-swipe", "גלול למטה עד שתמצא את הגדרות התצוגה", InputMode.Combined, true),
        Task("en-swipe", "Scroll down until Display settings is visible", InputMode.Combined, true),
        Task("he-plan", "פתח הגדרות, עבור לתצוגה ובדוק אם מצב כהה מופעל בלי לשנות אותו", InputMode.Combined, true),
        Task("en-plan", "Open Settings, navigate to Display and check dark mode without changing it", InputMode.Combined, true),
        Task("he-recovery", "הופיע חלון לא צפוי. סגור אותו והמשך לחיפוש המקורי", InputMode.Combined, true, true),
        Task("en-recovery", "Dismiss the unexpected dialog and resume the original search", InputMode.Combined, true, true),
        Task("he-tree", "מצא את כפתור החזרה", InputMode.Accessibility),
        Task("en-tree", "Locate the Back control", InputMode.Accessibility),
        Task("he-whatsapp", "פתח וואטסאפ, מצא את איש הקשר לבדיקה והכן הודעה: אני בדרך. אל תשלח עדיין", InputMode.Combined, true),
        Task("en-whatsapp", "Open WhatsApp, find the test contact and draft: I am on my way. Do not send yet", InputMode.Combined, true),
        Task("he-form", "מלא בטופס הבדיקה שם יוסי ועיר ירושלים בלי לשלוח את הטופס", InputMode.Combined, true),
        Task("en-form", "Fill the test form with name Yossi and city Jerusalem without submitting", InputMode.Combined, true),
    )
    data class Measurement(
        val candidate: String, val taskId: String, val device: String,
        val quantization: String, val coldLoadMs: Long?, val firstOutputMs: Long?,
        val elapsedMs: Long, val peakPssBytes: Long?, val storageBytes: Long?,
        val batteryMicroAh: Long?, val success: Boolean?, val failure: String? = null,
    ) {
        init {
            require(candidate in candidates)
            require(tasks.any { it.id == taskId })
            require(device.isNotBlank() && quantization.isNotBlank())
            require(elapsedMs >= 0)
            require(listOfNotNull(coldLoadMs, firstOutputMs, peakPssBytes, storageBytes, batteryMicroAh).all { it >= 0 })
            require(failure == null || success != true)
        }
    }
    // Feed the same captured screen/tree and task into existing runtime adapters.
    // The harness has no action authority and must never send messages or mutate settings.
    suspend fun compare(run: suspend (String, Task) -> Measurement): List<Measurement> {
        val results = mutableListOf<Measurement>()
        for (task in tasks) for (candidate in candidates) {
            val measurement = run(candidate, task)
            require(measurement.candidate == candidate && measurement.taskId == task.id)
            results += measurement
        }
        return results
    }
}
