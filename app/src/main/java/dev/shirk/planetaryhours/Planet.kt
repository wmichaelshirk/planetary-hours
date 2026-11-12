package dev.shirk.planetaryhours

import java.time.LocalDateTime

enum class Planet(val symbol: String, val color: Long, val iconResId: Int) {
    SATURN("♄\uFE0E", 0xFF8E8E8E, R.drawable.ic_saturn_symbol),    // Lead grey
    JUPITER("♃\uFE0E", 0xFF4169E1, R.drawable.ic_jupiter_symbol),   // Royal Blue
    MARS("♂\uFE0E", 0xFFFF4500, R.drawable.ic_mars_symbol),      // Orange-red
    SUN("☉\uFE0E", 0xFFFFD700, R.drawable.ic_sun_symbol),       // Yellow-gold
    VENUS("♀\uFE0E", 0xFF43B3AE, R.drawable.ic_venus_symbol),     // Copper Green
    MERCURY("☿\uFE0E", 0xFFFFA500, R.drawable.ic_mercury_symbol),   // Orange
    MOON("☽\uFE0E", 0xFFFFFFFF, R.drawable.ic_moon_symbol)       // White
}

data class PlanetaryHour(
    val planet: Planet,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val index: Int // 1 to 12 for day, 13 to 24 for night
)
