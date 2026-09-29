package omnibeat.app.model

data class AppLanguage(
    val languageTag: String?,
    val displayName: String?,
) {
    companion object {
        val System = AppLanguage(null, null)
        val English = AppLanguage("en", "English")
        val Russian = AppLanguage("ru", "Русский")
        val entries = listOf(System, English, Russian)
    }
}
