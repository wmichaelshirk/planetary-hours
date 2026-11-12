package dev.shirk.planetaryhours

import android.content.Context
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object PlanetaryHourCalculator {

    val romanNumerals = mapOf(
        1 to "I", 2 to "II", 3 to "III", 4 to "IV", 5 to "V", 6 to "VI",
        7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X", 11 to "XI", 12 to "XII"
    )

    private val chaldeanOrder = listOf(
        Planet.SATURN, Planet.JUPITER, Planet.MARS, Planet.SUN, Planet.VENUS, Planet.MERCURY, Planet.MOON
    )

    fun getDayOfWeekPlanet(dayOfWeek: Int): Planet {
        return when (dayOfWeek) {
            7 -> Planet.SUN
            1 -> Planet.MOON
            2 -> Planet.MARS
            3 -> Planet.MERCURY
            4 -> Planet.JUPITER
            5 -> Planet.VENUS
            6 -> Planet.SATURN
            else -> Planet.SUN
        }
    }

    /**
     * Pure computation from the cached location; never touches location services, so it is fast and
     * safe to call from the complication/tile. Returns null if no location has ever been cached.
     */
    fun calculateForCachedLocation(context: Context, date: LocalDateTime = LocalDateTime.now()): List<PlanetaryHour>? {
        val (lat, lon) = PlanetaryLocation.getCached(context) ?: return null
        return calculateHours(lat, lon, date)
    }

    fun calculateHours(
        lat: Double,
        lon: Double,
        date: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PlanetaryHour> {
        val midnight = date.atZone(zoneId).toLocalDate().atStartOfDay(zoneId)

        val allHours = mutableListOf<PlanetaryHour>()
        // Yesterday's cycle covers the pre-dawn hours; today + 2 more days guarantees at least
        // ~48h of upcoming hours, so the UI keeps going even if a daily refresh is late or missed.
        for (dayOffset in -1L..2L) {
            val currentMidnight = midnight.plusDays(dayOffset)
            val times = SunTimes.compute().at(lat, lon).timezone(zoneId).on(currentMidnight).execute()
            
            val sunrise = times.rise ?: currentMidnight.withHour(6).withMinute(0).withSecond(0).withNano(0)
            val sunset = times.set ?: sunrise.plusHours(12)
            
            val nextDayTimes = SunTimes.compute().at(lat, lon).timezone(zoneId).on(currentMidnight.plusDays(1)).execute()
            val nextSunrise = nextDayTimes.rise ?: currentMidnight.plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
            
            allHours.addAll(calculatePlanetaryHours(sunrise, sunset, nextSunrise))
        }
        // Return a distinct, sorted list of hours to avoid overlaps in timeline entries
        return allHours.distinctBy { it.startTime }.sortedBy { it.startTime }
    }

    /**
     * Splits sunrise→sunset and sunset→nextSunrise into 12 equal parts each. All arithmetic is on
     * ZonedDateTime, which measures durations and adds time in real elapsed time, so nights that span
     * a daylight-saving change get correctly sized hours.
     */
    fun calculatePlanetaryHours(sunrise: ZonedDateTime, sunset: ZonedDateTime, nextSunrise: ZonedDateTime): List<PlanetaryHour> {
        val dayDuration = Duration.between(sunrise, sunset).toMillis()
        val nightDuration = Duration.between(sunset, nextSunrise).toMillis()
        val dayHourMillis = dayDuration / 12
        val nightHourMillis = nightDuration / 12

        val hours = mutableListOf<PlanetaryHour>()
        val dayStartPlanet = getDayOfWeekPlanet(sunrise.dayOfWeek.value)
        var currentPlanetIndex = chaldeanOrder.indexOf(dayStartPlanet)

        for (i in 0 until 12) {
            val start = sunrise.plusNanos(i * dayHourMillis * 1_000_000)
            val end = sunrise.plusNanos((i + 1) * dayHourMillis * 1_000_000)
            hours.add(PlanetaryHour(chaldeanOrder[currentPlanetIndex], start, end, i + 1))
            currentPlanetIndex = (currentPlanetIndex + 1) % chaldeanOrder.size
        }
        for (i in 0 until 12) {
            val start = sunset.plusNanos(i * nightHourMillis * 1_000_000)
            val end = sunset.plusNanos((i + 1) * nightHourMillis * 1_000_000)
            hours.add(PlanetaryHour(chaldeanOrder[currentPlanetIndex], start, end, i + 13))
            currentPlanetIndex = (currentPlanetIndex + 1) % chaldeanOrder.size
        }
        return hours
    }
}
