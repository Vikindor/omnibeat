package omnibeat.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import omnibeat.app.data.TranslationStrings

@Composable
fun appStringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    TranslationStrings.get(LocalResources.current, id, *formatArgs)
