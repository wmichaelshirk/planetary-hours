package dev.shirk.planetaryhours

import java.time.ZonedDateTime

enum class Planet(val symbol: String, val color: Long, val iconResId: Int) {
    SATURN("♄︎", 0xFF8E8E8E, R.drawable.ic_saturn_symbol),    // Lead grey
    JUPITER("♃︎", 0xFF4169E1, R.drawable.ic_jupiter_symbol),   // Royal Blue
    MARS("♂︎", 0xFFFF4500, R.drawable.ic_mars_symbol),      // Orange-red
    SUN("☉︎", 0xFFFFD700, R.drawable.ic_sun_symbol),       // Yellow-gold
    VENUS("♀︎", 0xFF43B3AE, R.drawable.ic_venus_symbol),     // Copper Green
    MERCURY("☿︎", 0xFFFFA500, R.drawable.ic_mercury_symbol),   // Orange
    MOON("☽︎", 0xFFFFFFFF, R.drawable.ic_moon_symbol)       // White
}

/**
 * Times are zoned so that durations and arithmetic happen in real elapsed time. With LocalDateTime,
 * a night spanning a daylight-saving change would be treated as an hour shorter or longer than it is.
 */
data class PlanetaryHour(
    val planet: Planet,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val index: Int // 1 to 12 for day, 13 to 24 for night
)
