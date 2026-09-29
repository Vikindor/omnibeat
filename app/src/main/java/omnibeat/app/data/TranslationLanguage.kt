package omnibeat.app.data

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import omnibeat.app.model.AppLanguage
import omnibeat.app.model.ImportedTranslation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A complete imported catalog belongs to one locale, never to every app language. */
object TranslationLanguage {
    var imported: ImportedTranslation? by mutableStateOf(null)
        private set
    private var loaded = false

    fun activeTranslation(resources: Resources): ImportedTranslation? =
        imported?.takeIf { it.languageTag == resources.configuration.locales[0].toLanguageTag() }

    val languages: List<AppLanguage>
        get() = imported?.let { translation ->
            AppLanguage.entries.filterNot { it.languageTag == translation.languageTag } + translation.language
        } ?: AppLanguage.entries

    suspend fun load(repository: StationRepository) {
        if (loaded) return
        imported = repository.loadImportedTranslation()
        loaded = true
    }

    private fun registerLanguage(context: Context, translation: ImportedTranslation?) {
        val manager = context.getSystemService(LocaleManager::class.java)
        val builtin = LocaleConfig.fromContextIgnoringOverride(context).supportedLocales
        val tags = buildList {
            if (builtin != null) repeat(builtin.size()) { add(builtin[it].toLanguageTag()) }
            translation?.let { add(it.languageTag) }
        }.distinct()
        val config = translation?.let { LocaleConfig(LocaleList.forLanguageTags(tags.joinToString(","))) }
        if (manager.overrideLocaleConfig?.supportedLocales != config?.supportedLocales) {
            manager.overrideLocaleConfig = config
        }
    }

    fun select(context: Context, localeTags: String) {
        val manager = context.getSystemService(LocaleManager::class.java)
        val locales = LocaleList.forLanguageTags(localeTags)
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
    }

    suspend fun install(context: Context, repository: StationRepository, translation: ImportedTranslation) = withContext(NonCancellable) {
        // A locale change can recreate the Activity; finish the persisted language transition.
        val manager = context.getSystemService(LocaleManager::class.java)
        val installed = repository.importTranslation(translation, manager.applicationLocales.toLanguageTags())
        imported = installed
        registerLanguage(context, installed)
        select(context, installed.languageTag)
    }

    suspend fun reset(context: Context, repository: StationRepository) = withContext(NonCancellable) {
        val previous = checkNotNull(imported).previousLocaleTags
        repository.clearImportedTranslation()
        imported = null
        select(context, previous)
        registerLanguage(context, null)
    }

    fun get(resources: Resources, @StringRes id: Int, vararg args: Any): String {
        val translation = activeTranslation(resources)
        if (translation == null) {
            return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
        }
        val text = translation.strings.getValue(resources.getResourceEntryName(id))
        return if (args.isEmpty()) text else String.format(translation.locale, text, *args)
    }
}

fun Resources.appString(@StringRes id: Int, vararg args: Any): String =
    TranslationLanguage.get(this, id, *args)
