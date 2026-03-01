package dev.shirk.planetaryhours

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

enum class Planet(val symbol: String, val color: Long, val iconResId: Int) {
    SATURN("♄\uFE0E", 0xFF8E8E8E, R.drawable.ic_saturn_symbol),    // Lead grey
    JUPITER("♃\uFE0E", 0xFF6495ED, R.drawable.ic_jupiter_symbol),   // Cornflower blue
    MARS("♂\uFE0E", 0xFFFF4500, R.drawable.ic_mars_symbol),      // Orange-red
    SUN("☉\uFE0E", 0xFFFFD700, R.drawable.ic_sun_symbol),       // Yellow-gold
    VENUS("♀\uFE0E", 0xFF00FF00, R.drawable.ic_venus_symbol),     // Lime green
    MERCURY("☿\uFE0E", 0xFFFFA500, R.drawable.ic_mercury_symbol),   // Orange
    MOON("☽\uFE0E", 0xFFFFFFFF, R.drawable.ic_moon_symbol)       // White
}

data class PlanetaryHour(
    val planet: Planet,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val index: Int // 1 to 12 for day, 13 to 24 for night
)

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

    @SuppressLint("MissingPermission")
    suspend fun calculatePlanetaryHoursForCurrentLocation(context: Context, date: LocalDateTime = LocalDateTime.now()): List<PlanetaryHour> {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }

        val sunrise: LocalDateTime
        val sunset: LocalDateTime
        val nextSunrise: LocalDateTime

        if (location == null) {
            sunrise = date.withHour(6).withMinute(0).withSecond(0).withNano(0)
            sunset = date.withHour(18).withMinute(0).withSecond(0).withNano(0)
            nextSunrise = date.plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
        } else {
            val lat = location.latitude
            val lon = location.longitude
            val zoneId = ZoneId.systemDefault()

            val todaySunTimes = SunTimes.compute()
                .at(lat, lon)
                .timezone(zoneId)
                .on(date)
                .execute()

            val tomorrowSunTimes = SunTimes.compute()
                .at(lat, lon)
                .timezone(zoneId)
                .on(date.plusDays(1))
                .execute()

            sunrise = todaySunTimes.rise?.toLocalDateTime() ?: date.withHour(6).withMinute(0).withSecond(0).withNano(0)
            sunset = todaySunTimes.set?.toLocalDateTime() ?: date.withHour(18).withMinute(0).withSecond(0).withNano(0)
            nextSunrise = tomorrowSunTimes.rise?.toLocalDateTime() ?: date.plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
        }

        return calculatePlanetaryHours(sunrise, sunset, nextSunrise)
    }

    fun calculatePlanetaryHours(
        sunrise: LocalDateTime,
        sunset: LocalDateTime,
        nextSunrise: LocalDateTime
    ): List<PlanetaryHour> {
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
