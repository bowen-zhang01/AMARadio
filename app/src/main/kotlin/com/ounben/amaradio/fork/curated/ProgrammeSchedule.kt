package com.ounben.amaradio.fork.curated

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One weekly slot of a scheduled programme, in the broadcaster's own time zone. */
data class ScheduleSlot(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val zone: ZoneId
)

/** One concrete broadcast of a programme. */
data class Airing(val start: ZonedDateTime, val end: ZonedDateTime) {
    fun isOnAir(now: ZonedDateTime): Boolean = !now.isBefore(start) && now.isBefore(end)
}

/**
 * Air times of a programme that a station only carries at set times, written in the playlist as
 * `x-schedule="MO-FR 23:10-00:10@Asia/Hong_Kong; SA 21:00-22:00@Asia/Hong_Kong"`.
 *
 * Days are MO TU WE TH FR SA SU, ranges (`MO-FR`), lists (`SA,SU`) or `DAILY`. A slot that
 * ends at or before its start runs past midnight.
 */
object ProgrammeSchedule {

    private val dayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /** Parses a schedule; malformed slots are skipped. */
    fun parse(text: String): List<ScheduleSlot> =
        text.split(';').mapNotNull { part -> runCatching { parseSlot(part.trim()) }.getOrNull() }

    /** The broadcast on air at [now], or else the next one within the coming eight days. */
    fun nextAiring(slots: List<ScheduleSlot>, now: ZonedDateTime): Airing? =
        slots.flatMap { slot -> airingsAround(slot, now) }
            .filter { it.end.isAfter(now) }
            .minByOrNull { it.start.toInstant() }

    private fun airingsAround(slot: ScheduleSlot, now: ZonedDateTime): List<Airing> {
        val today = now.withZoneSameInstant(slot.zone).toLocalDate()
        // Start a day early so that a programme that began yesterday evening is still found.
        return (-1L..7L).map { today.plusDays(it) }
            .filter { it.dayOfWeek in slot.days }
            .map { date ->
                val start = date.atTime(slot.start).atZone(slot.zone)
                val endDate = if (slot.end <= slot.start) date.plusDays(1) else date
                Airing(start, endDate.atTime(slot.end).atZone(slot.zone))
            }
    }

    private fun parseSlot(text: String): ScheduleSlot? {
        if (text.isEmpty()) return null
        val (body, zoneId) = text.split('@').let { it[0].trim() to it.getOrNull(1)?.trim() }
        val (dayPart, timePart) = body.split(Regex("\\s+")).let { it[0] to it[1] }
        val (start, end) = timePart.split('-').let { LocalTime.parse(it[0]) to LocalTime.parse(it[1]) }
        return ScheduleSlot(parseDays(dayPart), start, end, ZoneId.of(zoneId ?: "UTC"))
    }

    private fun parseDays(text: String): Set<DayOfWeek> {
        if (text.equals("DAILY", ignoreCase = true)) return DayOfWeek.entries.toSet()
        return text.uppercase().split(',').flatMap { token ->
            val range = token.split('-')
            val from = dayCodes.indexOf(range[0])
            val to = dayCodes.indexOf(range.getOrElse(1) { range[0] })
            require(from >= 0 && to >= 0) { "Unknown day in $token" }
            (if (from <= to) (from..to) else (from..6) + (0..to)).map { DayOfWeek.of(it + 1) }
        }.toSet()
    }
}
