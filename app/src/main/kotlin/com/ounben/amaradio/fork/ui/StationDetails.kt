package com.ounben.amaradio.fork.ui

import android.content.Context
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import java.text.NumberFormat
import java.util.Locale

/**
 * The detail line of station lists, in the interface language: "Chinese · AAC+ · 64 kbps ·
 * 1,234 clicks" becomes "中文 · AAC+ · 64 kbps · 1,234 次点击" in Chinese.
 */
object StationDetails {

    fun short(station: DataRadioStation, context: Context, locale: Locale): String {
        val parts = buildList {
            languages(station.Language, locale).takeIf { it.isNotEmpty() }?.let(::add)
            station.Codec.split(',').firstOrNull()?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("UNKNOWN", ignoreCase = true) }
                ?.let(::add)
            if (station.Bitrate > 0) add("${station.Bitrate} kbps")
            if (station.ClickCount > 0) {
                val clicks = NumberFormat.getIntegerInstance(locale).format(station.ClickCount)
                add(context.resources.getQuantityString(R.plurals.fork_station_clicks, station.ClickCount, clicks))
            }
        }
        return parts.joinToString(" · ")
    }

    /**
     * radio-browser languages are free text, mostly lower-case English names ("chinese",
     * "english,german", "brazilian portuguese"). Known names are shown in [locale]; anything
     * else is kept as written.
     */
    fun languages(raw: String, locale: Locale): String {
        val names = raw.split(',', ';', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { name ->
                languageTags[name.lowercase()]
                    ?.let { tag -> Locale.forLanguageTag(tag).getDisplayLanguage(locale) }
                    ?.takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.titlecase(locale) }
                    ?: name
            }
            .distinct()
        return names.joinToString(if (locale.language == "zh") "、" else ", ")
    }

    /** English language name (lower case) to BCP 47 tag. */
    private val languageTags: Map<String, String> by lazy {
        val iso = Locale.getISOLanguages().associateBy { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase()
        }
        iso + mapOf(
            "mandarin" to "zh", "chinese mandarin" to "zh", "cantonese" to "yue", "hokkien" to "nan",
            "min nan" to "nan", "taiwanese" to "nan", "hakka" to "hak", "shanghainese" to "wuu",
            "filipino" to "fil", "american english" to "en", "british english" to "en", "english uk" to "en",
            "engilsh" to "en", "brazilian portuguese" to "pt", "castellano" to "es", "farsi" to "fa"
        )
    }
}
