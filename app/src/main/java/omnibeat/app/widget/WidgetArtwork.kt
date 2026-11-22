package omnibeat.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import omnibeat.app.data.StationArtworkCache

internal suspend fun widgetArtwork(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    val file = StationArtworkCache.fileForMedia(context, url)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    val metrics = context.resources.displayMetrics
    val target = (128 * metrics.density).toInt().coerceAtLeast(1)
    val window = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
    val cap = if (window != null && window.width() > 0 && window.height() > 0) {
        6L * window.width() * window.height()
    } else {
        6L * metrics.widthPixels * metrics.heightPixels
    }
    var sample = 1
    while (
        bounds.outWidth / sample > target * 2 ||
        bounds.outHeight / sample > target * 2 ||
        (bounds.outWidth / sample).coerceAtLeast(1).toLong() *
            (bounds.outHeight / sample).coerceAtLeast(1) * 4L > cap
    ) {
        sample *= 2
    }
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    })
}
