package com.ounben.amaradio.fork.curated

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ounben.amaradio.CustomStationManager
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class CuratedPlaylist(
    val source: CuratedSource,
    val stations: List<DataRadioStation>,
    /** Distinct `group-title` values in playlist order. */
    val groups: List<String>,
    /** When the copy in use was downloaded, or null for the copy bundled in the APK. */
    val fetchedAt: Long?,
    /** Stations marked `x-role="backup"`: alternative streams of another entry. */
    val backupUuids: Set<String> = emptySet(),
    /** Region key (as in `cn-regions.json`) per station id, from `x-region="…"`. */
    val regions: Map<String, String> = emptyMap(),
    /** Whether names come from the `x-name-en` / `x-group-en` attributes. */
    val english: Boolean = false,
    /** The M3U text, kept to re-label the stations when the interface language changes. */
    val text: String = ""
) {
    val primaryStations: List<DataRadioStation>
        get() = stations.filterNot { it.StationUuid in backupUuids }

    /** Non-backup stations of one region, in playlist order. */
    fun primaryStationsIn(region: String): List<DataRadioStation> =
        primaryStations.filter { regions[it.StationUuid] == region }
}

/**
 * Loads curated playlists, preferring (in order) a fresh cached download, a new download,
 * a stale cache and finally the copy bundled in the APK.
 */
class CuratedRepository(
    private val context: Context,
    private val httpClient: () -> OkHttpClient
) {
    private val cacheDir = File(context.filesDir, "curated")

    suspend fun load(source: CuratedSource, english: Boolean, forceRefresh: Boolean = false): Result<CuratedPlaylist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cached = cacheFile(source)
                val cacheIsFresh = cached.exists() &&
                    System.currentTimeMillis() - cached.lastModified() < CACHE_TTL_MS

                val text: String
                val fetchedAt: Long?
                when {
                    !forceRefresh && cacheIsFresh -> {
                        text = cached.readText()
                        fetchedAt = cached.lastModified()
                    }
                    else -> {
                        val downloaded = source.updateUrl?.let { download(it) }
                        when {
                            downloaded != null -> {
                                cacheDir.mkdirs()
                                cached.writeText(downloaded)
                                text = downloaded
                                fetchedAt = cached.lastModified()
                            }
                            cached.exists() -> {
                                text = cached.readText()
                                fetchedAt = cached.lastModified()
                            }
                            source.assetPath != null -> {
                                text = readAsset(source.assetPath)
                                fetchedAt = null
                            }
                            else -> error("Playlist ${source.id} is unavailable offline")
                        }
                    }
                }
                toPlaylist(source, text, fetchedAt, english).withLocalLogos()
            }
        }

    /** Synchronous read of the bundled copy, used for first-run seeding. */
    fun loadBundled(source: CuratedSource, english: Boolean): CuratedPlaylist? =
        source.assetPath?.let { toPlaylist(source, readAsset(it), null, english).withLocalLogos() }

    /** The same playlist with station and group names in the other language. */
    fun relabel(playlist: CuratedPlaylist, english: Boolean): CuratedPlaylist =
        if (playlist.english == english) playlist
        else toPlaylist(playlist.source, playlist.text, playlist.fetchedAt, english).withLocalLogos()

    /**
     * Copies bundled artwork (`asset://` logos) into app storage and points the stations
     * at the copy. Notifications, Android Auto and the icon provider all read `file://`
     * icons, which assets cannot offer directly. Copies are refreshed after app updates.
     */
    private fun CuratedPlaylist.withLocalLogos(): CuratedPlaylist {
        val logoDir = File(context.filesDir, "curated_logos")
        val appUpdatedAt = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val resolved = stations.map { station ->
            val logo = station.IconUrl
            if (!logo.startsWith(M3uPlaylist.ASSET_LOGO_SCHEME)) return@map station
            val assetPath = logo.removePrefix(M3uPlaylist.ASSET_LOGO_SCHEME)
            val target = File(logoDir, assetPath.substringAfterLast('/'))
            val available = (target.exists() && target.lastModified() >= appUpdatedAt) || runCatching {
                logoDir.mkdirs()
                context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
            }.isSuccess
            station.copy(IconUrl = if (available) Uri.fromFile(target).toString() else "")
        }
        return copy(stations = resolved)
    }

    private fun download(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", "AMARadio").build()
        httpClient().newCall(request).execute().use { response ->
            val body = if (response.isSuccessful) response.body?.string() else null
            // Only accept something that actually parses into stations.
            body?.takeIf { M3uPlaylist.parse(it).isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Download failed for $url", e)
        null
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun cacheFile(source: CuratedSource) = File(cacheDir, "${source.id}.m3u")

    companion object {
        private const val TAG = "CuratedRepository"
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)

        /**
         * Builds the stations of a playlist. With [english], names and groups come from the
         * `x-name-en` and `x-group-en` attributes where an entry has them. Station ids depend
         * only on the stream URL, so favourites survive a language change.
         */
        fun toPlaylist(source: CuratedSource, text: String, fetchedAt: Long?, english: Boolean = false): CuratedPlaylist {
            val entries = M3uPlaylist.parse(text).distinctBy { it.url }
            val stations = entries.map { it.toStation(source, english) }
            val groups = entries.mapNotNull { it.displayGroup(english) }.distinct()
            val backups = entries.filter { it.attributes["x-role"] == "backup" }
                .map { CustomStationManager.generateUuidFromUrl(it.url) }
                .toSet()
            val regions = entries.mapNotNull { entry ->
                entry.attributes["x-region"]?.trim()?.ifEmpty { null }
                    ?.let { CustomStationManager.generateUuidFromUrl(entry.url) to it }
            }.toMap()
            return CuratedPlaylist(source, stations, groups, fetchedAt, backups, regions, english, text)
        }

        /**
         * Identity of a stream for de-duplication: no scheme, no query (tokens, signatures)
         * and Qingting's interchangeable live hosts folded into one.
         */
        fun streamKey(url: String): String =
            url.substringAfter("://").substringBefore('?').trimEnd('/').lowercase()
                .replace("lhttp.qingting.fm/", "lhttp.qtfm.cn/")
                .replace("lhttp-hw.qtfm.cn/", "lhttp.qtfm.cn/")

        private fun M3uEntry.displayGroup(english: Boolean): String? =
            (if (english) attributes["x-group-en"]?.trim()?.ifEmpty { null } else null) ?: group

        private fun M3uEntry.toStation(source: CuratedSource, english: Boolean): DataRadioStation {
            return DataRadioStation(
                Name = (if (english) attributes["x-name-en"]?.trim()?.ifEmpty { null } else null) ?: title,
                StationUuid = CustomStationManager.generateUuidFromUrl(url),
                StreamUrl = url,
                IconUrl = logo.orEmpty(),
                CountryCode = attributes["x-country"]?.trim()?.uppercase()?.ifEmpty { null } ?: source.countryCode,
                // The group doubles as the tag line; a non-empty tag is also what
                // PlayStationTask requires before it records a station in History.
                TagsAll = displayGroup(english).orEmpty()
            )
        }
    }
}
