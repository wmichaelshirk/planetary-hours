package dev.shirk.planetaryhours

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.*
import java.time.Instant
import java.time.ZoneId

class PlanetaryComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null

        if (!PlanetaryHourCalculator.hasLocationPermission(this)) {
            return ComplicationDataTimeline(
                defaultComplicationData = createPermissionRequestData(),
                timelineEntries = emptyList()
            )
        }

        val hours = PlanetaryHourCalculator.calculatePlanetaryHoursForCurrentLocation(this)
        if (hours == null || hours.isEmpty()) {
            return ComplicationDataTimeline(
                defaultComplicationData = createNoDataComplication("No location"),
                timelineEntries = emptyList()
            )
        }

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        
        // Include hours starting from the current one
        val relevantHours = hours.filter { 
            it.endTime.atZone(zoneId).toInstant().isAfter(now)
        }

        val timelineEntries = relevantHours.flatMap { hour ->
            val startInst = hour.startTime.atZone(zoneId).toInstant()
            val endInst = hour.endTime.atZone(zoneId).toInstant()
            val durationMillis = (endInst.toEpochMilli() - startInst.toEpochMilli()).coerceAtLeast(1L)
            
            // Provide 12 entries per planetary hour (approx every 5-6 minutes)
            // This ensures the progress ring "progresses" visibly without requiring frequent service updates.
            (0 until 12).map { segment ->
                val segmentStart = startInst.plusMillis(segment * durationMillis / 12)
                val segmentEnd = startInst.plusMillis((segment + 1) * durationMillis / 12)
                val progress = segment.toFloat() / 12f
                
                TimelineEntry(
                    validity = TimeInterval(segmentStart, segmentEnd),
                    complicationData = createRangedValueDataForHour(hour, progress)
                )
            }
        }

        return ComplicationDataTimeline(
            defaultComplicationData = createNoDataComplication("No data"),
            timelineEntries = timelineEntries
        )
    }

    private fun createPermissionRequestData(): RangedValueComplicationData {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        return RangedValueComplicationData.Builder(
            value = 0f,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder("Permission required").build()
        )
            .setText(PlainComplicationText.Builder("PRM").build())
            .setTapAction(pendingIntent)
            .build()
    }

    private fun createNoDataComplication(message: String): RangedValueComplicationData {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return RangedValueComplicationData.Builder(
            value = 0f,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder(message).build()
        )
            .setText(PlainComplicationText.Builder("LOC").build())
            .setTapAction(pendingIntent)
            .build()
    }

    private fun createRangedValueDataForHour(hour: PlanetaryHour, progress: Float): RangedValueComplicationData {
        val displayIndex = ((hour.index - 1) % 12) + 1
        val text = PlanetaryHourCalculator.romanNumerals[displayIndex] ?: "N/A"
        val planetColor = hour.planet.color.toInt()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return RangedValueComplicationData.Builder(
            value = progress,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder("Planetary Hour").build()
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, hour.planet.iconResId)).build()
            )
            .setTapAction(pendingIntent)
            .setColorRamp(ColorRamp(intArrayOf(planetColor, planetColor), false))
            .build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        val hour = PlanetaryHour(Planet.SUN, java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(1), 1)
        val displayIndex = ((hour.index - 1) % 12) + 1
        val text = PlanetaryHourCalculator.romanNumerals[displayIndex] ?: "N/A"
        val planetColor = hour.planet.color.toInt()

        return RangedValueComplicationData.Builder(
            value = 0.5f,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder("Planetary Hour").build()
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, hour.planet.iconResId)).build()
            )
            .setColorRamp(ColorRamp(intArrayOf(planetColor, planetColor), false))
            .build()
    }
}
