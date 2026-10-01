package com.ounben.amaradio.fork.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A province-level region of China with its names and the spellings found in station data. */
@Serializable
data class ChinaRegion(
    val key: String,
    val en: String,
    val zh: String,
    val aliases: List<String> = emptyList()
)

/** A region chip on a country page. */
data class RegionFilter(
    val id: String,
    val label: String,
    val labelZh: String?,
    val stationCount: Int
)

@Serializable
private data class ChinaRegionFile(val regions: List<ChinaRegion>)

/**
 * Assigns Chinese stations to province-level regions.
 *
 * The catalog's `Subcountry` values mix pinyin (Zhejiang), postal romanization (Chekiang,
 * Kiangsu), Chinese (黑龙江) and city names (Dalian, 成都), and many stations have none, so
 * the station name is checked first: "南京交通广播" belongs to Jiangsu whatever its
 * `Subcountry` says. The table lives in `assets/curated/cn-regions.json`, in
 * administrative-division order, which is also the order of the region chips.
 */
class ChinaRegions(val regions: List<ChinaRegion>) {

    private val byName: Map<String, ChinaRegion> = buildMap {
        regions.forEach { region ->
            (listOf(region.key, region.en, region.zh) + region.aliases).forEach { name ->
                put(normalize(name), region)
            }
        }
    }

    /** Names looked for inside station names, longest first so "内蒙古" wins over shorter names. */
    private val nameNeedles: List<Pair<String, ChinaRegion>> = regions
        .flatMap { region -> (listOf(region.zh, region.en) + region.aliases).filter { it.length >= 2 }.map { it to region } }
        .sortedByDescending { it.first.length }

    /**
     * The region of a station: the region or city named first in [name], otherwise the
     * region its [subcountry] refers to, otherwise null.
     */
    fun classify(name: String, subcountry: String): ChinaRegion? {
        var best: ChinaRegion? = null
        var bestIndex = Int.MAX_VALUE
        for ((needle, region) in nameNeedles) {
            val index = indexOfName(name, needle)
            if (index in 0 until bestIndex) {
                best = region
                bestIndex = index
            }
        }
        return best ?: match(subcountry)
    }

    /** The region a `Subcountry` value refers to, or null for empty and unknown values. */
    fun match(subcountry: String): ChinaRegion? {
        val name = normalize(subcountry)
        if (name.isEmpty()) return null
        return byName[name] ?: byName[stripSuffixes(name)]
    }

    /** Region chips for stations already assigned to regions, in table order. */
    fun filters(stationsPerRegion: Map<String, Int>): List<RegionFilter> =
        regions.mapNotNull { region ->
            stationsPerRegion[region.key]?.takeIf { it > 0 }?.let { count ->
                RegionFilter(id = region.key, label = region.en, labelZh = region.zh, stationCount = count)
            }
        }

    fun byKey(key: String): ChinaRegion? = regions.firstOrNull { it.key == key }

    companion object {
        const val ASSET_PATH = "curated/cn-regions.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** Administrative suffixes, longest first, so "广西壮族自治区" loses the whole suffix. */
        private val suffixes = listOf(
            " special administrative region", " autonomous region", " municipality", " province",
            " sheng", " shi", " sar",
            "维吾尔自治区", "壮族自治区", "回族自治区", "特别行政区", "自治区", "省", "市"
        )

        fun parse(text: String): ChinaRegions =
            ChinaRegions(json.decodeFromString<ChinaRegionFile>(text).regions)

        /**
         * Position of [needle] in [name]: Chinese names match anywhere, Latin names only as
         * whole words ("Tibet" does not match "Tibetan"). -1 when absent.
         */
        private fun indexOfName(name: String, needle: String): Int {
            if (needle.first().code >= 0x2E80) return name.indexOf(needle)
            var from = 0
            while (true) {
                val index = name.indexOf(needle, from, ignoreCase = true)
                if (index < 0) return -1
                val before = name.getOrNull(index - 1)
                val after = name.getOrNull(index + needle.length)
                if ((before == null || !before.isLetter()) && (after == null || !after.isLetter())) return index
                from = index + 1
            }
        }

        private fun normalize(value: String): String =
            value.trim().lowercase().replace(Regex("\\s+"), " ")

        private fun stripSuffixes(value: String): String {
            val suffix = suffixes.firstOrNull { value.endsWith(it) && value.length > it.length }
            return if (suffix == null) value else value.removeSuffix(suffix).trim()
        }
    }
}
