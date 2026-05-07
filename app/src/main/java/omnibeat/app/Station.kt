package omnibeat.app

data class Station(
    val id: String,
    val title: String,
    val streamUrl: String,
    val tags: List<String>,
)

data class StationEditorState(
    val stationIndex: Int?,
    val title: String,
    val streamUrl: String,
    val tags: String,
)
