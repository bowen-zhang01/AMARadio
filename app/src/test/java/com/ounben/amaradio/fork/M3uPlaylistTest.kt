package com.ounben.amaradio.fork

import com.ounben.amaradio.fork.curated.CuratedRepository
import com.ounben.amaradio.fork.curated.CuratedSources
import com.ounben.amaradio.fork.curated.M3uPlaylist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class M3uPlaylistTest {

    @Test
    fun parsesAttributesAndTitles() {
        val text = """
            #EXTM3U
            #EXTINF:-1 group-title="总台" tvg-logo="https://example.org/logo.png",经济之声
            https://ngcdn002.cnr.cn/live/jjzs/index.m3u8
            #EXTINF:-1,AI FM
            http://example.org/AI_FM.mp3
        """.trimIndent()

        val entries = M3uPlaylist.parse(text)

        assertEquals(2, entries.size)
        assertEquals("经济之声", entries[0].title)
        assertEquals("总台", entries[0].group)
        assertEquals("https://example.org/logo.png", entries[0].logo)
        assertEquals("https://ngcdn002.cnr.cn/live/jjzs/index.m3u8", entries[0].url)
        assertEquals("AI FM", entries[1].title)
        assertNull(entries[1].group)
    }

    @Test
    fun keepsCommasInsideQuotedAttributesAndTitles() {
        val (attributes, title) =
            M3uPlaylist.parseExtInf("""#EXTINF:-1 group-title="News, Talk" x-role="backup",Station, with comma""")

        assertEquals("News, Talk", attributes["group-title"])
        assertEquals("backup", attributes["x-role"])
        assertEquals("Station, with comma", title)
    }

    @Test
    fun supportsExtGrpAndFallsBackToUrlForMissingTitle() {
        val text = """
            #EXTM3U
            #EXTGRP:Beijing
            #EXTINF:-1,
            https://example.org/live/fm945.m3u8?token=1
            # a comment
            not-a-url
        """.trimIndent()

        val entries = M3uPlaylist.parse(text)

        assertEquals(1, entries.size)
        assertEquals("Beijing", entries[0].group)
        assertEquals("fm945.m3u8", entries[0].title)
    }

    @Test
    fun bundledPlaylistIsWellFormed() {
        val asset = File("src/main/assets/curated/beijing-national.m3u")
        val playlist = CuratedRepository.toPlaylist(CuratedSources.beijingNational, asset.readText(), null)

        assertEquals(44, playlist.stations.size)
        assertEquals(12, playlist.backupUuids.size)
        assertEquals(32, playlist.primaryStations.size)
        assertTrue(playlist.stations.all { it.StreamUrl.startsWith("https://") })
        assertTrue(playlist.stations.all { it.TagsAll.isNotEmpty() })
        assertEquals(playlist.stations.size, playlist.stations.map { it.StationUuid }.toSet().size)
        // Every station has bundled artwork that actually exists.
        playlist.stations.forEach { station ->
            assertTrue(station.IconUrl.startsWith(M3uPlaylist.ASSET_LOGO_SCHEME), station.Name)
            val logo = File("src/main/assets/" + station.IconUrl.removePrefix(M3uPlaylist.ASSET_LOGO_SCHEME))
            assertTrue(logo.isFile, "missing ${logo.path}")
        }
    }

    @Test
    fun bundledPlaylistHasEnglishNames() {
        val text = File("src/main/assets/curated/beijing-national.m3u").readText()
        val chinese = CuratedRepository.toPlaylist(CuratedSources.beijingNational, text, null)
        val english = CuratedRepository.toPlaylist(CuratedSources.beijingNational, text, null, english = true)
        val cjk = Regex("[\\u3400-\\u9fff\\uff08\\uff09]")

        assertEquals(listOf("北京", "北京电视伴音", "北京各区", "中央台", "国际台", "北京（备用）"), chinese.groups)
        assertEquals(
            listOf("Beijing", "Beijing TV audio", "Beijing districts", "CNR national", "CRI international", "Beijing (backup)"),
            english.groups
        )
        assertTrue(english.stations.none { cjk.containsMatchIn(it.Name) || cjk.containsMatchIn(it.TagsAll) })
        // Same stations and ids in both languages, so favourites survive a language switch.
        assertEquals(chinese.stations.map { it.StationUuid }, english.stations.map { it.StationUuid })
        assertEquals(chinese.backupUuids, english.backupUuids)
        assertEquals("北京新闻广播 FM94.5", chinese.stations.first().Name)
        assertEquals("Beijing News Radio FM94.5", english.stations.first().Name)
    }

    @Test
    fun beijingStationsCarryTheirRegion() {
        val playlist = CuratedRepository.toPlaylist(
            CuratedSources.beijingNational, File("src/main/assets/curated/beijing-national.m3u").readText(), null
        )

        val beijing = playlist.primaryStationsIn("beijing")
        assertEquals(16, beijing.size)
        assertEquals(setOf("北京", "北京电视伴音", "北京各区"), beijing.map { it.TagsAll }.toSet())
        // National stations have no region; backups are never listed.
        assertTrue(playlist.primaryStations.filter { it.TagsAll == "中央台" }.none { it.StationUuid in playlist.regions })
        assertTrue(beijing.none { it.StationUuid in playlist.backupUuids })
    }

    @Test
    fun streamKeysIgnoreSchemeQueryAndQingtingHost() {
        assertEquals(
            CuratedRepository.streamKey("https://lhttp.qtfm.cn/live/5021739/64k.mp3"),
            CuratedRepository.streamKey("http://lhttp.qingting.fm/live/5021739/64k.mp3")
        )
        assertEquals(
            CuratedRepository.streamKey("https://pili-live-hls-bjhr-tv.huairtv.com/bjhr-tv/FM101.m3u8"),
            CuratedRepository.streamKey("https://pili-live-hls-bjhr-tv.huairtv.com/bjhr-tv/FM101.m3u8?sign=7f85&t=69bb")
        )
        assertTrue(
            CuratedRepository.streamKey("https://brtv-radiolive.rbc.cn/alive/fm945.m3u8") !=
                CuratedRepository.streamKey("https://brtv-radiolive.rbc.cn/alive/fm974.m3u8")
        )
    }
}
