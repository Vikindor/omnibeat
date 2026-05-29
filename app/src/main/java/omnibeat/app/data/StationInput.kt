package omnibeat.app.data

fun parseStationTags(tags: String): List<String> {
    return tags.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}
