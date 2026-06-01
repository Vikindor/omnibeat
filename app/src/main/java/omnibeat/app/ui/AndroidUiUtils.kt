package omnibeat.app.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import omnibeat.app.model.AppLanguage

tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

fun Context.applyAppLanguage(appLanguage: AppLanguage) {
    val localeManager = getSystemService(LocaleManager::class.java)
    val nextLocales = appLanguage.languageTag
        ?.let(LocaleList::forLanguageTags)
        ?: LocaleList.getEmptyLocaleList()
    if (localeManager.applicationLocales != nextLocales) {
        localeManager.applicationLocales = nextLocales
    }
}

fun Context.currentAppLanguage(): AppLanguage {
    val localeManager = getSystemService(LocaleManager::class.java)
    val locales = localeManager.applicationLocales
    if (locales.isEmpty) {
        return AppLanguage.System
    }
    val languageTag = locales[0]?.toLanguageTag() ?: return AppLanguage.System
    return AppLanguage.entries.firstOrNull { it.languageTag == languageTag || it.languageTag == locales[0]?.language }
        ?: AppLanguage.System
}
