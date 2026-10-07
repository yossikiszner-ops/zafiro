package com.niki914.zafiro.repo

/** Empty preference follows Android; unsupported system languages use English resources. */
internal object UiLanguage {
    fun normalize(tag: String): String = when (tag.trim().substringBefore('-').substringBefore('_').lowercase()) {
        "he", "iw" -> "he"
        "en" -> "en"
        else -> ""
    }
}
