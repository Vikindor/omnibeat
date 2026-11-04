package omnibeat.app.model

fun orderedTabs(playlists: List<Playlist>, savedOrder: List<String>): List<MainPage> {
    val available = MainPage.tabPages + playlists.map { MainPage.Playlist(it.id) }
    val byId = available.associateBy { it.name }
    val ordered = savedOrder.distinct().mapNotNull(byId::get)
    val orderedIds = ordered.mapTo(mutableSetOf()) { it.name }
    return ordered + available.filterNot { it.name in orderedIds }
}
