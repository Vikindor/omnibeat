package omnibeat.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import omnibeat.app.data.TranslationLanguage

@Composable
fun appStringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    TranslationLanguage.get(LocalResources.current, id, *formatArgs)
