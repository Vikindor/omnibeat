package omnibeat.app.model

import java.time.LocalDateTime

data class TrackHistoryEntry(
    val id: Long,
    val trackText: String,
    val stationTitle: String,
    val createdAt: LocalDateTime,
)
