package omnibeat.app.model

import java.util.Locale

data class ImportedTranslation(
    val languageTag: String,
    val strings: Map<String, String>,
    val source: String,
    val previousLocaleTags: String = "",
) {
    val locale: Locale get() = Locale.forLanguageTag(languageTag)
    val language: AppLanguage get() = AppLanguage(languageTag, locale.getDisplayName(locale))
}
