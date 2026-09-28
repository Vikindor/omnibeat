package omnibeat.app.data

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.IllegalFormatException

/** Imported strings override app-owned text only; Android and library resources stay native. */
object TranslationStrings {
    var overrides: Map<String, String> by mutableStateOf(emptyMap())
        private set

    fun apply(strings: Map<String, String>) {
        overrides = strings
    }

    fun get(resources: Resources, @StringRes id: Int, vararg args: Any): String {
        val translated = if (overrides.isEmpty()) null else overrides[resources.getResourceEntryName(id)]
        if (translated == null) {
            return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
        }
        if (args.isEmpty()) return translated
        return try {
            String.format(resources.configuration.locales[0], translated, *args)
        } catch (_: IllegalFormatException) {
            // Keep the app usable if a saved translation comes from another app version.
            resources.getString(id, *args)
        }
    }
}

fun Resources.appString(@StringRes id: Int, vararg args: Any): String =
    TranslationStrings.get(this, id, *args)
