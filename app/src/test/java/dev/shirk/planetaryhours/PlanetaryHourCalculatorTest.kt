package dev.shirk.planetaryhours

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlanetaryHourCalculatorTest {

    private val chaldeanOrder = listOf(
        Planet.SATURN, Planet.JUPITER, Planet.MARS, Planet.SUN, Planet.VENUS, Planet.MERCURY, Planet.MOON
    )

    // St. Louis, MO
    private val stlLat = 38.6270
    private val stlLon = -90.1994
    private val chicago: ZoneId = ZoneId.of("America/Chicago")

    private fun stl(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, chicago)

    private fun hoursAt(now: ZonedDateTime) =
        PlanetaryHourCalculator.calculateHours(stlLat, stlLon, now.toLocalDateTime(), chicago)

    private fun nextInChaldeanOrder(planet: Planet) =
        chaldeanOrder[(chaldeanOrder.indexOf(planet) + 1) % chaldeanOrder.size]

    private fun currentHour(hours: List<PlanetaryHour>, at: ZonedDateTime) =
        hours.single { !it.startTime.isAfter(at) && it.endTime.isAfter(at) }

    private val PlanetaryHour.length: Duration get() = Duration.between(startTime, endTime)

    // --- Day rulers ---------------------------------------------------------------------------

    @Test
    fun dayRulers_matchTraditionalWeekdays() {
        val expected = mapOf(
            DayOfWeek.SUNDAY to Planet.SUN,
            DayOfWeek.MONDAY to Planet.MOON,
            DayOfWeek.TUESDAY to Planet.MARS,
            DayOfWeek.WEDNESDAY to Planet.MERCURY,
            DayOfWeek.THURSDAY to Planet.JUPITER,
            DayOfWeek.FRIDAY to Planet.VENUS,
            DayOfWeek.SATURDAY to Planet.SATURN,
        )
        expected.forEach { (day, planet) ->
            assertEquals("Ruler of $day", planet, PlanetaryHourCalculator.getDayOfWeekPlanet(day.value))
        }
    }

    // --- Single-day calculation with fixed sun times ------------------------------------------

    private val sunrise = stl(2026, 11, 12, 6, 40)   // a Thursday
    private val sunset = stl(2026, 11, 12, 16, 50)
    private val nextSunrise = stl(2026, 11, 13, 6, 41)

    private val oneDay get() = PlanetaryHourCalculator.calculatePlanetaryHours(sunrise, sunset, nextSunrise)

    @Test
    fun calculatePlanetaryHours_produces12DayAnd12NightHoursIndexed1To24() {
        val hours = oneDay
        assertEquals(24, hours.size)
        assertEquals((1..24).toList(), hours.map { it.index })
    }

    @Test
    fun calculatePlanetaryHours_firstHourIsRuledByTheDayRuler() {
        assertEquals(Planet.JUPITER, oneDay.first().planet)
    }

    @Test
    fun calculatePlanetaryHours_followsChaldeanOrder() {
        oneDay.zipWithNext().forEach { (a, b) ->
            assertEquals("After hour ${a.index}", nextInChaldeanOrder(a.planet), b.planet)
        }
    }

    @Test
    fun calculatePlanetaryHours_hoursAreContiguousAndSpanSunriseToNextSunrise() {
        val hours = oneDay
        assertEquals(sunrise, hours.first().startTime)
        assertEquals(sunset, hours[12].startTime)
        hours.zipWithNext().forEach { (a, b) -> assertEquals(a.endTime, b.startTime) }
        // Integer division of the durations may lose a few ms at the end; never more than that.
        val drift = Duration.between(hours.last().endTime, nextSunrise).abs()
        assertTrue("Last hour ends ${drift.toMillis()}ms from next sunrise", drift < Duration.ofSeconds(1))
    }

    @Test
    fun calculatePlanetaryHours_dayAndNightHoursHaveUnequalButConsistentLengths() {
        val hours = oneDay
        val dayLength = Duration.between(sunrise, sunset).dividedBy(12)
        val nightLength = Duration.between(sunset, nextSunrise).dividedBy(12)
        hours.take(12).forEach { assertEquals(dayLength.toMillis(), it.length.toMillis()) }
        hours.drop(12).forEach { assertEquals(nightLength.toMillis(), it.length.toMillis()) }
        assertTrue("November days are shorter than nights", dayLength < nightLength)
    }

    @Test
    fun theHourAfterTheLastNightHourIsTomorrowsDayRuler() {
        // The classic property that gives the weekday order: 24 hours on from Jupiter lands on Venus.
        assertEquals(Planet.VENUS, nextInChaldeanOrder(oneDay.last().planet))
    }

    // --- Multi-day calculation with real sun times (St. Louis) ---------------------------------

    @Test
    fun calculateHours_isContiguousAndInChaldeanOrderAcrossDayBoundaries() {
        val hours = hoursAt(stl(2026, 11, 12, 14, 34))
        hours.zipWithNext().forEach { (a, b) ->
            val gap = Duration.between(a.endTime, b.startTime).abs()
            assertTrue("Gap/overlap of ${gap.toMillis()}ms between ${a.endTime} and ${b.startTime}", gap < Duration.ofSeconds(1))
            assertEquals("Order broken at ${b.startTime}", nextInChaldeanOrder(a.planet), b.planet)
        }
    }

    @Test
    fun calculateHours_eachDayCycleStartsWithItsDayRuler() {
        val hours = hoursAt(stl(2026, 11, 12, 14, 34))
        hours.filter { it.index == 1 }.forEach {
            assertEquals(
                "Day starting ${it.startTime}",
                PlanetaryHourCalculator.getDayOfWeekPlanet(it.startTime.dayOfWeek.value),
                it.planet
            )
        }
    }

    @Test
    fun calculateHours_coversAtLeast48HoursAhead() {
        val now = stl(2026, 11, 12, 14, 34)
        val hours = hoursAt(now)
        assertTrue(!hours.first().startTime.isAfter(now))
        assertTrue("Covers until ${hours.last().endTime}", hours.last().endTime.isAfter(now.plusHours(48)))
    }

    @Test
    fun calculateHours_midafternoonThursdayInStLouisIsTheTenthHourOfTheSun() {
        // Sanity check against the value observed on the watch on 2026-11-12 at 2:34 PM CST.
        val now = stl(2026, 11, 12, 14, 34)
        val hour = currentHour(hoursAt(now), now)
        assertEquals(10, hour.index)
        assertEquals(Planet.SUN, hour.planet)
    }

    @Test
    fun calculateHours_beforeSunriseBelongsToPreviousDaysNight() {
        // 3 AM Thursday is still Wednesday's (Mercury's) planetary night.
        val now = stl(2026, 11, 12, 3, 0)
        val hours = hoursAt(now)
        val hour = currentHour(hours, now)
        assertTrue("Expected a night hour, got ${hour.index}", hour.index in 13..24)

        val cycleStart = hours.last { it.index == 1 && !it.startTime.isAfter(now) }
        assertEquals(LocalDate.of(2026, 11, 11), cycleStart.startTime.toLocalDate())
        assertEquals(Planet.MERCURY, cycleStart.planet)
    }

    // --- Daylight-saving transitions ----------------------------------------------------------

    /** The 12 night hours of the cycle that starts at sunrise on [day]. */
    private fun nightHoursOf(day: LocalDate): List<PlanetaryHour> {
        val hours = hoursAt(day.atTime(12, 0).atZone(chicago))
        val dayStart = hours.first { it.index == 1 && it.startTime.toLocalDate() == day }
        val i = hours.indexOf(dayStart)
        return hours.subList(i + 12, i + 24)
    }

    private fun assertNightHoursAreEqualInRealTime(night: List<PlanetaryHour>) {
        val realNight = Duration.between(night.first().startTime, night.last().endTime)
        val expected = realNight.dividedBy(12).toMillis()
        night.forEach { h ->
            assertTrue(
                "Hour ${h.index} is ${h.length.toMinutes()} min, expected ~${expected / 60_000} min",
                Math.abs(h.length.toMillis() - expected) <= 1
            )
        }
    }

    @Test
    fun fallBackNight_hoursAreEqualInRealTime() {
        // Clocks went back at 2 AM on 2026-11-01, so the night of Oct 31 is an hour longer in real time than the
        // wall-clock difference suggests. Every hour must still be 1/12 of the real night.
        val night = nightHoursOf(LocalDate.of(2026, 10, 31))
        assertNightHoursAreEqualInRealTime(night)

        val wallClock = Duration.between(night.first().startTime.toLocalDateTime(), night.last().endTime.toLocalDateTime())
        val real = Duration.between(night.first().startTime, night.last().endTime)
        assertEquals("Real night is an hour longer than the wall clock shows", wallClock.plusHours(1).toMinutes(), real.toMinutes())
    }

    @Test
    fun springForwardNight_hoursAreEqualInRealTime() {
        // Clocks went forward at 2 AM on 2026-03-08: the night of Mar 7 is an hour shorter in real time.
        val night = nightHoursOf(LocalDate.of(2026, 3, 7))
        assertNightHoursAreEqualInRealTime(night)

        val wallClock = Duration.between(night.first().startTime.toLocalDateTime(), night.last().endTime.toLocalDateTime())
        val real = Duration.between(night.first().startTime, night.last().endTime)
        assertEquals("Real night is an hour shorter than the wall clock shows", wallClock.minusHours(1).toMinutes(), real.toMinutes())
    }

    @Test
    fun fallBackNight_repeatedOneOClockHourIsUnambiguous() {
        // 1:30 AM happens twice on 2026-11-01; each occurrence must fall in exactly one planetary hour.
        val firstOneThirty = LocalDateTime.of(2026, 11, 1, 1, 30).atZone(chicago).withEarlierOffsetAtOverlap()
        val secondOneThirty = firstOneThirty.withLaterOffsetAtOverlap()
        assertEquals(Duration.ofHours(1), Duration.between(firstOneThirty, secondOneThirty))

        val hours = hoursAt(firstOneThirty)
        val a = currentHour(hours, firstOneThirty)
        val b = currentHour(hours, secondOneThirty)
        assertTrue("Later 1:30 should be in the same or a later hour", b.startTime >= a.startTime)
    }
}
