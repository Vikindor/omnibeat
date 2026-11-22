package omnibeat.app.data

import omnibeat.app.model.TrackHistoryEntry
import java.time.format.DateTimeFormatter
import java.util.Locale

object TrackHistoryTextCodec {
    private val timestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT)

    fun encode(entries: List<TrackHistoryEntry>): String =
        entries.joinToString(separator = "\n\n", postfix = if (entries.isEmpty()) "" else "\n") { entry ->
            "${entry.createdAt.format(timestampFormat)}\n${entry.trackText}\n${entry.stationTitle}"
        }
}
