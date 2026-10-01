package com.ounben.amaradio.fork.world

import androidx.annotation.StringRes
import com.ounben.amaradio.R
import java.util.Locale

/** Sections of the World tab, in display order. */
enum class Continent(@StringRes val titleRes: Int) {
    ASIA(R.string.fork_continent_asia),
    EUROPE(R.string.fork_continent_europe),
    NORTH_AMERICA(R.string.fork_continent_north_america),
    SOUTH_AMERICA(R.string.fork_continent_south_america),
    OCEANIA(R.string.fork_continent_oceania),
    AFRICA(R.string.fork_continent_africa),
    OTHER(R.string.fork_continent_other)
}

object Countries {

    /** Pseudo country code of the "Popular worldwide" page. */
    const val WORLD = "WORLD"

    /** The country's name in [locale], falling back to the catalog's (English) name. */
    fun displayName(code: String, locale: Locale, fallback: String? = null): String {
        val localized = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrNull()
        return localized?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: code
    }

    fun continentOf(code: String): Continent = continents[code.uppercase()] ?: Continent.OTHER

    /**
     * UN M49 continents from CLDR's territory containment (common/supplemental/
     * supplementalData.xml); the Americas are split into North (with Central America and
     * the Caribbean) and South. Android does not expose ICU's Region class to apps.
     */
    private val continents: Map<String, Continent> = mapOf(
        Continent.ASIA to "AE AF AM AZ BD BH BN BT CN CY GE HK ID IL IN IQ IR JO JP KG KH KP KR KW KZ LA LB LK " +
            "MM MN MO MV MY NP OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE",
        Continent.EUROPE to "AD AL AT AX BA BE BG BY CH CQ CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT " +
            "JE LI LT LU LV MC MD ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA XK",
        Continent.NORTH_AMERICA to "AG AI AW BB BL BM BQ BS BZ CA CR CU CW DM DO GD GL GP GT HN HT JM KN KY LC MF " +
            "MQ MS MX NI PA PM PR SV SX TC TT US VC VG VI",
        Continent.SOUTH_AMERICA to "AR BO BR BV CL CO EC FK GF GS GY PE PY SR UY VE",
        Continent.OCEANIA to "AC AQ AS AU CC CK CP CX DG FJ FM GU HM KI MH MP NC NF NR NU NZ PF PG PN PW SB TA " +
            "TK TO TV UM VU WF WS",
        Continent.AFRICA to "AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EA EG EH ER ET GA GH GM GN GQ GW IC IO KE KM " +
            "LR LS LY MA MG ML MR MU MW MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TF TG TN TZ UG YT ZA ZM ZW"
    ).flatMap { (continent, codes) -> codes.split(' ').map { it to continent } }.toMap()

    /** Countries suggested from the network/SIM country and the interface language. */
    fun suggested(deviceCountry: String?, locale: Locale): List<String> = buildList {
        deviceCountry?.uppercase()?.let(::add)
        locale.country.uppercase().takeIf { it.length == 2 }?.let(::add)
        // A Simplified Chinese interface suggests China even when the device is abroad.
        if (locale.language == "zh" && locale.script != "Hant" && locale.country.uppercase() !in setOf("TW", "HK", "MO")) {
            add("CN")
        }
    }.distinct()
}
