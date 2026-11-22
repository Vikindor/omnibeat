package omnibeat.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    @Suppress("UsePropertyAccessSyntax")
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
}
