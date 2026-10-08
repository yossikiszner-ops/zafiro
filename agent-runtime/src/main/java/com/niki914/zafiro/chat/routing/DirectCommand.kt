package com.niki914.zafiro.chat.routing

/** Only single, unambiguous, non-destructive commands bypass language interpretation. */
internal sealed interface DirectCommand {
    data class Open(val app: String) : DirectCommand
    data object Back : DirectCommand
    data object Home : DirectCommand
    data class OpenSettings(val screen: SettingsScreen) : DirectCommand
    data class Volume(val direction: Int) : DirectCommand
    enum class SettingsScreen { Bluetooth, BatterySaver }
    companion object {
        fun parse(input: String): DirectCommand? {
            val text = input.trim().trimEnd('.', '!', '?', '。').lowercase()
                .removePrefix("please ").removeSuffix(" please").trim()
            if (text in setOf("go back", "back", "חזור", "תחזור", "חזור אחורה", "תחזור אחורה", "חזור למסך הקודם", "תחזור למסך הקודם", "go to the previous screen")) return Back
            if (text in setOf("go home", "home screen", "go to the home screen", "תחזור למסך הבית", "חזור למסך הבית")) return Home
            if (text in setOf("open bluetooth settings", "תיכנס להגדרות של הבלוטוס", "פתח הגדרות בלוטוס", "תפתח הגדרות ותיכנס לבלוטוס")) return OpenSettings(SettingsScreen.Bluetooth)
            if (text in setOf("open battery saver settings", "פתח הגדרות חיסכון בסוללה", "תחפש בהגדרות מצב חיסכון בסוללה")) return OpenSettings(SettingsScreen.BatterySaver)
            if (text in setOf("volume down", "turn volume down", "lower volume", "הנמך ווליום", "הורד ווליום", "הנמך את עוצמת הקול")) return Volume(-1)
            if (text in setOf("volume up", "turn volume up", "raise volume", "הגבר ווליום", "העלה ווליום", "הגבר את עוצמת הקול")) return Volume(1)
            val target = Regex("^(?:open|launch|פתח|תפתח|פתחי)(?: לי)? (?:את )?(.{1,64})$").matchEntire(text)?.groupValues?.get(1)?.trim() ?: return null
            if (Regex("(?:https?://|[/\\n]|\\b(?:and|then|send|message|delete|with)\\b|ואז|ושלח|שלח|הודעה|מחק| עם | (?:ות|ו(?:פתח|כתוב|לך|שים|חפש|הפעל)))").containsMatchIn(target)) return null
            return Open(target)
        }
    }
}
