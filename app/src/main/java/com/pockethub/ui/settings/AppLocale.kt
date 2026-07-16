package com.pockethub.ui.settings

/**
 * Supported in-app language options.
 *
 * [key] is persisted in DataStore; [localeTag] is passed to AppCompat's locale overlay.
 */
enum class AppLocale(
    val key: String,
    val localeTag: String?,
) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    CHINESE("zh", "zh"),
    ;

    companion object {
        fun fromKey(key: String?): AppLocale = entries.find { it.key == key } ?: SYSTEM
    }
}
