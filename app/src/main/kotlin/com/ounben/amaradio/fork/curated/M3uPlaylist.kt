package com.ounben.amaradio.fork.curated

/**
 * One playable entry of an extended M3U playlist.
 *
 * [group] comes from `group-title="..."` (or a preceding `#EXTGRP:` line) and [logo] from
 * `tvg-logo="..."`; both are what the Chinese IPTV/radio list ecosystem uses.
 */
data class M3uEntry(
    val title: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Lenient parser for extended M3U playlists.
 *
 * Unlike [com.ounben.amaradio.playlist.PlaylistM3U], which only resolves the first stream
 * of a station playlist, this keeps every entry together with its metadata.
 */
object M3uPlaylist {

    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var pendingTitle: String? = null
        var pendingAttributes: Map<String, String> = emptyMap()
        var pendingGroup: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val (attributes, title) = parseExtInf(line)
                    pendingAttributes = attributes
                    pendingTitle = title
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifEmpty { null }
                }
                line.startsWith("#") -> Unit
                isStreamUrl(line) -> {
                    val group = pendingAttributes["group-title"]?.trim()?.ifEmpty { null } ?: pendingGroup
                    val title = pendingTitle?.ifBlank { null }
                        ?: pendingAttributes["tvg-name"]?.ifBlank { null }
                        ?: fallbackTitle(line)
                    entries += M3uEntry(
                        title = title,
                        url = line,
                        group = group,
                        logo = pendingAttributes["tvg-logo"]?.trim()?.takeIf { isStreamUrl(it) },
                        attributes = pendingAttributes
                    )
                    pendingTitle = null
                    pendingAttributes = emptyMap()
                    pendingGroup = null
                }
                else -> Unit
            }
        }
        return entries
    }

    /** Splits `#EXTINF:-1 key="v, w",Title, with comma` into attributes and title. */
    internal fun parseExtInf(line: String): Pair<Map<String, String>, String> {
        val body = line.substringAfter(':', "")
        var inQuotes = false
        var separator = -1
        for ((index, c) in body.withIndex()) {
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    separator = index
                    break
                }
            }
        }
        val head = if (separator >= 0) body.substring(0, separator) else body
        val title = if (separator >= 0) body.substring(separator + 1).trim() else ""
        val attributes = attributeRegex.findAll(head).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2]
        }
        return attributes to title
    }

    private fun isStreamUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    private fun fallbackTitle(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { url }
}
