package omnibeat.app.model

enum class AppLanguage(
    val languageTag: String?,
    val displayName: String?,
) {
    System(null, null),
    English("en", "English"),
    Russian("ru", "Русский"),
}
