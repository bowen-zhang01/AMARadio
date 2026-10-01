package com.ounben.amaradio.fork

import com.ounben.amaradio.database.RegionCount
import com.ounben.amaradio.fork.world.ChinaRegions
import com.ounben.amaradio.fork.world.Continent
import com.ounben.amaradio.fork.world.Countries
import com.ounben.amaradio.fork.world.CountryItem
import com.ounben.amaradio.fork.world.WorldUiState
import com.ounben.amaradio.fork.ui.StationDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale

internal class WorldCatalogTest {

    private val regions = ChinaRegions.parse(File("src/main/assets/${ChinaRegions.ASSET_PATH}").readText())

    @Test
    fun regionTableCoversEveryProvinceLevelDivision() {
        // 22 provinces, 5 autonomous regions, 4 municipalities, 2 special administrative regions.
        assertEquals(33, regions.regions.size)
        assertEquals(regions.regions.size, regions.regions.map { it.key }.toSet().size)
    }

    @Test
    fun catalogSpellingsMapToProvinces() {
        // Subcountry values as they appear in the radio-browser catalog.
        val cases = mapOf(
            "Kiangsu" to "jiangsu", "Chekiang" to "zhejiang", "Zhejiang" to "zhejiang", "Shantung" to "shandong",
            "Hopei" to "hebei", "Honan" to "henan", "Kwangtung" to "guangdong", "Szechuan" to "sichuan",
            "Sinkiang" to "xinjiang", "Shansi" to "shanxi", "Shensi" to "shaanxi", "Inner Mongolia" to "inner-mongolia",
            "Amur River" to "heilongjiang", "黑龙江" to "heilongjiang", "jilin" to "jilin", "Dalian" to "liaoning",
            "Chungking" to "chongqing", "Tientsin" to "tianjin", "xizang" to "tibet", "Tibet" to "tibet",
            "Shanghai Direct Administered Municipality" to "shanghai", "上海" to "shanghai", "成都" to "sichuan",
            "安丘市" to "shandong", "Beijing" to "beijing", "Hong Kong" to "hong-kong", "Guangdong Province" to "guangdong",
            "广西壮族自治区" to "guangxi", "北京市" to "beijing"
        )
        cases.forEach { (subcountry, key) -> assertEquals(key, regions.match(subcountry)?.key, subcountry) }
        listOf("", "Music", "Maule", "hun", "Taiwan Hongkong ").forEach { assertNull(regions.match(it), it) }
    }

    @Test
    fun filtersMergeSpellingsPerProvince() {
        val counts = listOf(
            RegionCount("Chekiang", 115), RegionCount("Kiangsu", 118), RegionCount("Zhejiang", 100),
            RegionCount("chekiang", 1), RegionCount("Music", 2), RegionCount("Beijing", 37)
        )

        val filters = regions.filters(counts)

        assertEquals(listOf("zhejiang", "jiangsu", "beijing"), filters.map { it.id })
        assertEquals(216, filters[0].stationCount)
        assertEquals(setOf("Chekiang", "Zhejiang", "chekiang"), filters[0].catalogNames.toSet())
        assertEquals("浙江", filters[0].labelZh)
        assertEquals("Zhejiang", filters[0].label)
    }

    @Test
    fun countriesFallIntoContinents() {
        assertEquals(Continent.ASIA, Countries.continentOf("CN"))
        assertEquals(Continent.ASIA, Countries.continentOf("tw"))
        assertEquals(Continent.OCEANIA, Countries.continentOf("AU"))
        assertEquals(Continent.EUROPE, Countries.continentOf("RU"))
        assertEquals(Continent.NORTH_AMERICA, Countries.continentOf("MX"))
        assertEquals(Continent.NORTH_AMERICA, Countries.continentOf("JM"))
        assertEquals(Continent.SOUTH_AMERICA, Countries.continentOf("BR"))
        assertEquals(Continent.AFRICA, Countries.continentOf("EG"))
        assertEquals(Continent.OTHER, Countries.continentOf("XX"))
    }

    @Test
    fun suggestionsUseDeviceCountryAndChineseInterface() {
        assertEquals(listOf("AU", "CN"), Countries.suggested("au", Locale.forLanguageTag("zh-Hans-AU")))
        assertEquals(listOf("AU", "CN"), Countries.suggested("au", Locale.SIMPLIFIED_CHINESE))
        assertEquals(listOf("AU", "TW"), Countries.suggested("au", Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals(listOf("AU"), Countries.suggested("au", Locale.forLanguageTag("en-AU")))
        assertEquals(listOf("US"), Countries.suggested(null, Locale.US))
    }

    @Test
    fun countryNamesAreLocalized() {
        assertEquals("中国", Countries.displayName("CN", Locale.SIMPLIFIED_CHINESE))
        assertEquals("Australia", Countries.displayName("AU", Locale.ENGLISH))
        assertTrue(Countries.displayName("XX", Locale.ENGLISH, "Unknown").isNotBlank())
    }

    @Test
    fun languagesAreShownInTheInterfaceLanguage() {
        val zh = Locale.SIMPLIFIED_CHINESE
        assertEquals("中文", StationDetails.languages("chinese", zh))
        assertEquals("英语、德语", StationDetails.languages("english,german", zh))
        assertEquals("英语", StationDetails.languages("american english,english", zh))
        assertEquals("粤语", StationDetails.languages("Cantonese", zh))
        assertEquals("English, German", StationDetails.languages("english,german", Locale.ENGLISH))
        assertEquals("español mexico", StationDetails.languages("español mexico", Locale.ENGLISH))
        assertEquals("", StationDetails.languages("", zh))
    }

    @Test
    fun countrySearchPrefersCodesAndPrefixes() {
        fun item(code: String, name: String, english: String, count: Int) =
            CountryItem(code, name, english, count, Countries.continentOf(code))
        val state = WorldUiState(
            isLoading = false,
            countries = listOf(
                item("CZ", "捷克", "Czechia", 300), item("CN", "中国", "China", 2118),
                item("CL", "智利", "Chile", 400), item("CH", "瑞士", "Switzerland", 500)
            )
        )

        assertEquals(listOf("CH", "CN", "CL", "CZ"), state.search("ch").map { it.code })
        assertEquals(listOf("CN"), state.search("中国").map { it.code })
        assertEquals(emptyList<String>(), state.search("  ").map { it.code })
    }
}
