package com.ounben.amaradio.fork.curated

import android.content.Context
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
    val backupUuids: Set<String> = emptySet()
) {
    val primaryStations: List<DataRadioStation>
        get() = stations.filterNot { it.StationUuid in backupUuids }
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

    suspend fun load(source: CuratedSource, forceRefresh: Boolean = false): Result<CuratedPlaylist> =
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
                toPlaylist(source, text, fetchedAt)
            }
        }

    /** Synchronous read of the bundled copy, used for first-run seeding. */
    fun loadBundled(source: CuratedSource): CuratedPlaylist? =
        source.assetPath?.let { toPlaylist(source, readAsset(it), null) }

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

        fun toPlaylist(source: CuratedSource, text: String, fetchedAt: Long?): CuratedPlaylist {
            val entries = M3uPlaylist.parse(text).distinctBy { it.url }
            val stations = entries.map { it.toStation(source) }
            val groups = entries.mapNotNull { it.group }.distinct()
            val backups = entries.filter { it.attributes["x-role"] == "backup" }
                .map { CustomStationManager.generateUuidFromUrl(it.url) }
                .toSet()
            return CuratedPlaylist(source, stations, groups, fetchedAt, backups)
        }

        private fun M3uEntry.toStation(source: CuratedSource): DataRadioStation {
            val lowerUrl = url.lowercase()
            val codec = when {
                ".m3u8" in lowerUrl -> "HLS"
                lowerUrl.substringBefore('?').endsWith(".mp3") -> "MP3"
                lowerUrl.substringBefore('?').endsWith(".aac") -> "AAC"
                else -> ""
            }
            return DataRadioStation(
                Name = title,
                StationUuid = CustomStationManager.generateUuidFromUrl(url),
                StreamUrl = url,
                IconUrl = logo.orEmpty(),
                CountryCode = source.countryCode,
                // The group doubles as the tag line; a non-empty tag is also what
                // PlayStationTask requires before it records a station in History.
                TagsAll = group.orEmpty(),
                Codec = codec
            )
        }
    }
}
