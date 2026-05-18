package omnibeat.app

import omnibeat.app.data.StationExportCodec
import omnibeat.app.data.StationExportData
import omnibeat.app.data.StationImportMode
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationExportCodecTest {
    @Test
    fun encodeDecodePreservesNativeExportData() {
        val station = station(
            id = "station-1",
            title = "Nightride FM",
            streamUrl = "https://stream.nightride.fm/nightride.mp3",
            tags = listOf("synthwave", "night"),
            isFavorite = true,
        )
        val exportJson = StationExportCodec.encode(
            StationExportData(
                stations = listOf(station),
                sortState = StationSortState(StationSortMode.Custom, ascending = false),
                customStationOrder = listOf("station-1"),
                customFavoriteOrder = listOf("station-1"),
                exportedAt = "2026-05-13T10:00:00Z",
            ),
        )

        val decoded = StationExportCodec.decode(exportJson)

        assertEquals(listOf(station), decoded.stations)
        assertEquals(StationSortState(StationSortMode.Custom, ascending = false), decoded.sortState)
        assertEquals(listOf("station-1"), decoded.customStationOrder)
        assertEquals(listOf("station-1"), decoded.customFavoriteOrder)
    }

    @Test
    fun replaceUsesImportedLibraryAndSanitizesOrders() {
        val importedStation = station(id = "imported", streamUrl = "https://example.com/imported.mp3")
        val importResult = StationExportCodec.buildImportResult(
            importedData = StationExportData(
                stations = listOf(importedStation),
                sortState = StationSortState(StationSortMode.Custom, ascending = false),
                customStationOrder = listOf("missing", "imported"),
                customFavoriteOrder = listOf("imported", "missing"),
            ),
            currentStations = listOf(station(id = "current", streamUrl = "https://example.com/current.mp3")),
            currentSortState = StationSortState(),
            currentCustomStationOrder = listOf("current"),
            currentCustomFavoriteOrder = emptyList(),
            mode = StationImportMode.Replace,
        )

        assertEquals(listOf(importedStation), importResult.stations)
        assertEquals(listOf("imported"), importResult.customStationOrder)
        assertEquals(listOf("imported"), importResult.customFavoriteOrder)
    }

    @Test
    fun mergeUpdatesMatchingStreamUrlsAndAppendsNewStations() {
        val current = station(
            id = "current-id",
            title = "Old title",
            streamUrl = "https://example.com/same.mp3",
            tags = listOf("old"),
            isFavorite = false,
        )
        val updated = station(
            id = "imported-existing-id",
            title = "New title",
            streamUrl = "https://example.com/same.mp3",
            tags = listOf("new"),
            isFavorite = true,
        )
        val added = station(
            id = "new-id",
            title = "Added",
            streamUrl = "https://example.com/new.mp3",
            isFavorite = true,
        )

        val importResult = StationExportCodec.buildImportResult(
            importedData = StationExportData(stations = listOf(updated, added)),
            currentStations = listOf(current),
            currentSortState = StationSortState(StationSortMode.StationTitle, ascending = true),
            currentCustomStationOrder = listOf("current-id"),
            currentCustomFavoriteOrder = emptyList(),
            mode = StationImportMode.Merge,
        )

        assertEquals(2, importResult.stations.size)
        assertEquals("current-id", importResult.stations[0].id)
        assertEquals("New title", importResult.stations[0].title)
        assertEquals(listOf("new"), importResult.stations[0].tags)
        assertTrue(importResult.stations[0].isFavorite)
        assertEquals("new-id", importResult.stations[1].id)
        assertEquals(listOf("current-id", "new-id"), importResult.customStationOrder)
        assertEquals(listOf("new-id"), importResult.customFavoriteOrder)
    }

    @Test
    fun decodeSkipsStationsWithoutStreamUrlAndCleansTags() {
        val decoded = StationExportCodec.decode(
            """
            {
              "schemaVersion": 1,
              "stations": [
                { "title": "No URL", "streamUrl": "" },
                {
                  "id": "valid",
                  "title": "",
                  "streamUrl": " https://example.com/live.mp3 ",
                  "tags": [" rock ", "", "Rock"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded.stations.size)
        assertEquals("valid", decoded.stations.first().id)
        assertEquals("https://example.com/live.mp3", decoded.stations.first().title)
        assertEquals(listOf("rock"), decoded.stations.first().tags)
        assertFalse(decoded.stations.first().isFavorite)
    }

    private fun station(
        id: String,
        title: String = "Station",
        streamUrl: String,
        tags: List<String> = emptyList(),
        isFavorite: Boolean = false,
        dateAdded: String = "2026-05-13T10:00:00Z",
    ): Station {
        return Station(
            id = id,
            title = title,
            streamUrl = streamUrl,
            tags = tags,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
        )
    }
}
