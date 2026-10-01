package com.ounben.amaradio.utils

import java.util.*

/**
 * Utility class to generate dynamic placeholders for radio stations.
 */
object StationPlaceholderUtils {

    // Defined Material Design color palette (AARRGGBB)
    private val MATERIAL_PALETTE = longArrayOf(
        0xFF2196F3, // Blue
        0xFFF44336, // Red
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFF9C27B0, // Purple
        0xFF009688, // Teal
        0xFFE91E63  // Pink
    )

    /**
     * Extracts the appropriate text for the placeholder based on the station name.
     */
    fun extractPlaceholderText(name: String?): String {
        val input = name?.trim() ?: return "?"
        if (input.isEmpty()) return "?"

        // 0. CJK names: frequencies and "City + Category + Broadcast" patterns make the
        // generic rules below produce identical labels (e.g. every Beijing station would
        // read the same), so use the distinctive core of the name instead.
        if (CJK_REGEX.containsMatchIn(input)) {
            extractCjkCore(input)?.let { return it }
        }

        // 1. Search for the first contiguous number
        val numberRegex = Regex("""(\d+)""")
        val numberMatch = numberRegex.find(input)
        if (numberMatch != null) {
            return numberMatch.value
        }

        // 2. Extraction of initials or word parts
        val words = input.split(Regex("""\s+""")).filter { it.isNotBlank() }
        
        return if (words.size >= 2) {
            // Initials of the first two words
            (words[0].take(1) + words[1].take(1)).uppercase(Locale.ROOT)
        } else {
            // First two letters of the single word
            input.take(2).uppercase(Locale.ROOT)
        }
    }

    private val CJK_REGEX = Regex("[\\u3400-\\u9FFF]")

    // Broadcaster and city prefixes that many station names share.
    private val CJK_PREFIXES = listOf(
        "\u4E2D\u592E\u4EBA\u6C11\u5E7F\u64AD\u7535\u53F0", // central people's broadcasting
        "\u4E2D\u56FD\u56FD\u9645\u5E7F\u64AD\u7535\u53F0", // China Radio International
        "\u4E2D\u592E\u5E7F\u64AD\u7535\u89C6\u603B\u53F0", // China Media Group
        "CNR", "CRI", "CMG", "BRTV",
        "\u5317\u4EAC", // Beijing
        "\u4E2D\u56FD"  // China
    )

    // Generic "radio / voice / station / frequency / channel" suffixes.
    private val CJK_SUFFIXES = listOf(
        "\u4EBA\u6C11\u5E7F\u64AD\u7535\u53F0", "\u5E7F\u64AD\u7535\u53F0",
        "\u5E7F\u64AD", "\u4E4B\u58F0", "\u7535\u53F0", "\u9891\u7387", "\u9891\u9053"
    )

    private fun cjkCount(text: String) = text.count { CJK_REGEX.matches(it.toString()) }

    private fun stripCjkSuffix(text: String): String {
        val suffix = CJK_SUFFIXES.firstOrNull { text.endsWith(it) } ?: return text
        val stripped = text.removeSuffix(suffix)
        return if (cjkCount(stripped) >= 2) stripped else text
    }

    internal fun extractCjkCore(input: String): String? {
        var core = input
            .replace(Regex("[\uFF08(\\[].*?[\uFF09)\\]]"), "")          // (notes)
            .replace(Regex("(?i)(FM|AM)\\s*\\d+(\\.\\d+)?"), "")   // FM94.5
            .replace(Regex("[\\s\\p{Punct}\u00B7]"), "")
        for (prefix in CJK_PREFIXES) {
            if (core.startsWith(prefix, ignoreCase = true)) {
                val rest = core.substring(prefix.length)
                // Keep the prefix when only a generic suffix would remain ("China" + "Voice").
                val suffix = CJK_SUFFIXES.firstOrNull { rest.endsWith(it) }
                val distinctive = if (suffix != null) rest.removeSuffix(suffix) else rest
                if (cjkCount(distinctive) >= 2) core = rest
            }
        }
        val chars = stripCjkSuffix(core).filter { CJK_REGEX.matches(it.toString()) }
        if (chars.isEmpty()) return null
        return if (chars.length == 3) chars else chars.take(2)
    }

    /**
     * Determines a deterministic color based on the stationUuid.
     */
    fun getPlaceholderColor(stationUuid: String?): Long {
        if (stationUuid.isNullOrEmpty()) return MATERIAL_PALETTE[0]

        // Ensure a positive index using bitwise AND with 0x7FFFFFFF
        val hash = stationUuid.hashCode() and 0x7FFFFFFF
        val index = hash % MATERIAL_PALETTE.size
        
        return MATERIAL_PALETTE[index]
    }

    /**
     * Creates a placeholder bitmap with text and background color.
     */
    fun createPlaceholderBitmap(name: String, uuid: String, size: Int = 512): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background color
        val color = getPlaceholderColor(uuid).toInt()
        canvas.drawColor(color)
        
        // Text
        val text = extractPlaceholderText(name)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            this.textSize = size / 2.5f
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paint)
        
        return bitmap
    }
}
