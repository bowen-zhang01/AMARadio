package com.ounben.amaradio.fork

import com.ounben.amaradio.fork.curated.ProgrammeSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal class ProgrammeScheduleTest {

    private val hongKong = ZoneId.of("Asia/Hong_Kong")
    private val melbourne = ZoneId.of("Australia/Melbourne")
    private val weeknights = ProgrammeSchedule.parse("MO-FR 23:10-00:10@Asia/Hong_Kong")

    @Test
    fun parsesDaysRangesListsAndZones() {
        assertEquals(1, weeknights.size)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), weeknights[0].days)
        assertEquals(LocalTime.of(23, 10), weeknights[0].start)
        assertEquals(hongKong, weeknights[0].zone)

        val mixed = ProgrammeSchedule.parse("DAILY 21:00-22:00@Asia/Shanghai; SA,SU 10:00-11:00@America/Toronto; FR-MO 01:00-02:00@UTC; nonsense")
        assertEquals(3, mixed.size)
        assertEquals(7, mixed[0].days.size)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), mixed[1].days)
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY), mixed[2].days)
    }

    @Test
    fun nextAiringIsShownInTheListenersTimeZone() {
        // Thursday 20:00 in Melbourne is 18:00 in Hong Kong: tonight's show starts 23:10 HKT.
        val now = ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, melbourne)
        val airing = ProgrammeSchedule.nextAiring(weeknights, now)!!

        assertFalse(airing.isOnAir(now))
        val local = airing.start.withZoneSameInstant(melbourne)
        assertEquals(DayOfWeek.FRIDAY, local.dayOfWeek)
        assertEquals(LocalTime.of(1, 10), local.toLocalTime())
    }

    @Test
    fun aShowThatRunsPastMidnightIsOnAirAfterMidnight() {
        val now = ZonedDateTime.of(2026, 10, 2, 0, 5, 0, 0, hongKong) // Friday 00:05, Thursday's show
        val airing = ProgrammeSchedule.nextAiring(weeknights, now)!!

        assertTrue(airing.isOnAir(now))
        assertEquals(DayOfWeek.THURSDAY, airing.start.dayOfWeek)
    }

    @Test
    fun weekendsSkipToMonday() {
        val now = ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, hongKong) // Saturday noon
        val airing = ProgrammeSchedule.nextAiring(weeknights, now)!!

        assertEquals(DayOfWeek.MONDAY, airing.start.dayOfWeek)
        assertEquals(LocalTime.of(23, 10), airing.start.toLocalTime())
    }
}
