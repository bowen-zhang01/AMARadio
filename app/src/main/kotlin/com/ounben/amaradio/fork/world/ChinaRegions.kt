package com.ounben.amaradio.fork.world

import com.ounben.amaradio.database.RegionCount
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

/** A region chip on a country page and the catalog `Subcountry` values it stands for. */
data class RegionFilter(
    val id: String,
    val label: String,
    val labelZh: String?,
    val stationCount: Int,
    val catalogNames: List<String>
)

@Serializable
private data class ChinaRegionFile(val regions: List<ChinaRegion>)

/**
 * Maps radio-browser `Subcountry` values of Chinese stations to province-level regions.
 *
 * The values mix pinyin (Zhejiang), postal romanization (Chekiang, Kiangsu), Chinese (黑龙江),
 * city names (Dalian, 成都) and case variants, so the same province appears under several
 * spellings. The table lives in `assets/curated/cn-regions.json`.
 */
class ChinaRegions(val regions: List<ChinaRegion>) {

    private val byName: Map<String, ChinaRegion> = buildMap {
        regions.forEach { region ->
            (listOf(region.key, region.en, region.zh) + region.aliases).forEach { name ->
                put(normalize(name), region)
            }
        }
    }

    /**
     * Region filters for the China page: catalog spellings merged per province, largest
     * first. Unknown values (cities elsewhere, genres, typos) only appear under "All".
     */
    fun filters(counts: List<RegionCount>): List<RegionFilter> =
        counts.mapNotNull { count -> match(count.name)?.let { region -> region to count } }
            .groupBy({ it.first }, { it.second })
            .map { (region, matches) ->
                RegionFilter(
                    id = region.key,
                    label = region.en,
                    labelZh = region.zh,
                    stationCount = matches.sumOf { it.count },
                    catalogNames = matches.map { it.name }
                )
            }
            .sortedByDescending { it.stationCount }

    /** The region a `Subcountry` value refers to, or null for empty and unknown values. */
    fun match(subcountry: String): ChinaRegion? {
        val name = normalize(subcountry)
        if (name.isEmpty()) return null
        return byName[name] ?: byName[stripSuffixes(name)]
    }

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

        private fun normalize(value: String): String =
            value.trim().lowercase().replace(Regex("\\s+"), " ")

        private fun stripSuffixes(value: String): String {
            val suffix = suffixes.firstOrNull { value.endsWith(it) && value.length > it.length }
            return if (suffix == null) value else value.removeSuffix(suffix).trim()
        }
    }
}
