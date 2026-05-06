package omnibeat.app

data class Station(
    val id: String,
    val name: String,
    val formatLabel: String,
    val sourceUrl: String,
)

data class StationEditorState(
    val stationIndex: Int?,
    val name: String,
    val sourceUrl: String,
)
