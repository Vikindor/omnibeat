package omnibeat.app.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import omnibeat.app.model.AppLanguage
import omnibeat.app.data.TranslationLanguage
import java.util.Locale

tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

fun Context.applyAppLanguage(appLanguage: AppLanguage) {
    TranslationLanguage.select(this, appLanguage.languageTag.orEmpty())
}

fun Context.currentAppLanguage(): AppLanguage {
    val localeManager = getSystemService(LocaleManager::class.java)
    val locales = localeManager.applicationLocales
    if (locales.isEmpty) {
        return AppLanguage.System
    }
    val languageTag = locales[0]?.toLanguageTag() ?: return AppLanguage.System
    return TranslationLanguage.languages.firstOrNull { it.languageTag == languageTag }
        ?: Locale.forLanguageTag(languageTag).let { AppLanguage(languageTag, it.getDisplayName(it)) }
}
