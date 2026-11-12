package dev.shirk.planetaryhours

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import kotlinx.coroutines.tasks.await
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object PlanetaryHourCalculator {
    private const val TAG = "PlanetaryHourCalc"
    private const val PREFS_NAME = "location_prefs"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"

    val romanNumerals = mapOf(
        1 to "I", 2 to "II", 3 to "III", 4 to "IV", 5 to "V", 6 to "VI",
        7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X", 11 to "XI", 12 to "XII"
    )

    private val chaldeanOrder = listOf(
        Planet.SATURN, Planet.JUPITER, Planet.MARS, Planet.SUN, Planet.VENUS, Planet.MERCURY, Planet.MOON
    )

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun saveLocation(context: Context, latitude: Double, longitude: Double) {
        Log.i(TAG, "Saving location: $latitude, $longitude")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LAT, latitude.toFloat())
            .putFloat(KEY_LON, longitude.toFloat())
            .commit()
    }

    private fun getCachedLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT)) return null
        return Pair(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LON, 0f).toDouble())
    }

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
    suspend fun calculatePlanetaryHoursForCurrentLocation(context: Context, date: LocalDateTime = LocalDateTime.now()): List<PlanetaryHour>? {
        val cached = getCachedLocation(context)
        
        if (!hasLocationPermission(context)) {
            return if (cached != null) calculateHours(cached.first, cached.second, date) else null
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        var location: Location? = try { fusedLocationClient.lastLocation.await() } catch (e: Exception) { null }

        if (location == null && cached != null) {
            return calculateHours(cached.first, cached.second, date)
        }

        if (location == null) {
            location = try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(8000)
                    .build()
                fusedLocationClient.getCurrentLocation(request, null).await()
            } catch (e: Exception) { null }
        }

        if (location != null) {
            saveLocation(context, location.latitude, location.longitude)
            return calculateHours(location.latitude, location.longitude, date)
        } else if (cached != null) {
            return calculateHours(cached.first, cached.second, date)
        }

        return null
    }

    private fun calculateHours(lat: Double, lon: Double, date: LocalDateTime): List<PlanetaryHour> {
        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = date.atZone(zoneId)
        val midnight = zonedDateTime.toLocalDate().atStartOfDay(zoneId)

        val allHours = mutableListOf<PlanetaryHour>()
        // Yesterday, Today, Tomorrow covers any possible current planetary hour and a good range for a timeline
        for (dayOffset in -1L..1L) {
            val currentMidnight = midnight.plusDays(dayOffset)
            val times = SunTimes.compute().at(lat, lon).timezone(zoneId).on(currentMidnight).execute()
            
            val sunrise = times.rise ?: currentMidnight.withHour(6).withMinute(0).withSecond(0).withNano(0)
            val sunset = times.set ?: sunrise.plusHours(12)
            
            val nextDayTimes = SunTimes.compute().at(lat, lon).timezone(zoneId).on(currentMidnight.plusDays(1)).execute()
            val nextSunrise = nextDayTimes.rise ?: currentMidnight.plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
            
            allHours.addAll(calculatePlanetaryHours(sunrise.toLocalDateTime(), sunset.toLocalDateTime(), nextSunrise.toLocalDateTime()))
        }
        // Return a distinct, sorted list of hours to avoid overlaps in timeline entries
        return allHours.distinctBy { it.startTime }.sortedBy { it.startTime }
    }

    fun calculatePlanetaryHours(sunrise: LocalDateTime, sunset: LocalDateTime, nextSunrise: LocalDateTime): List<PlanetaryHour> {
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
