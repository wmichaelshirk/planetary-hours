package dev.shirk.planetaryhours

import android.graphics.drawable.Icon
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val romanNumerals = mapOf(
    1 to "I", 2 to "II", 3 to "III", 4 to "IV", 5 to "V", 6 to "VI",
    7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X", 11 to "XI", 12 to "XII"
)

class PlanetaryComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null

        val hours = PlanetaryHourCalculator.calculatePlanetaryHoursForCurrentLocation(this)

        if (hours.isEmpty()) {
            val data = createNoDataComplication("Location not available")
            return ComplicationDataTimeline(
                defaultComplicationData = data,
                timelineEntries = listOf(TimelineEntry(TimeInterval(Instant.EPOCH, Instant.MAX), data))
            )
        }

        val now = Instant.now()
        val upcomingHours = hours.filter { it.endTime.atZone(ZoneId.systemDefault()).toInstant().isAfter(now) }

        if (upcomingHours.isEmpty()) {
            val data = createNoDataComplication("No upcoming hours")
            return ComplicationDataTimeline(
                defaultComplicationData = data,
                timelineEntries = listOf(TimelineEntry(TimeInterval(Instant.EPOCH, Instant.MAX), data))
            )
        }

        val timelineEntries = upcomingHours.take(6).map { hour ->
            val startInst = hour.startTime.atZone(ZoneId.systemDefault()).toInstant()
            val endInst = hour.endTime.atZone(ZoneId.systemDefault()).toInstant()

            TimelineEntry(
                validity = TimeInterval(startInst, endInst),
                complicationData = createDynamicRangedValueData(hour, startInst, endInst)
            )
        }

        // Use a static version for default to be safe during serialization
        val defaultData = createStaticRangedValueData(upcomingHours.first())

        return ComplicationDataTimeline(
            defaultComplicationData = defaultData,
            timelineEntries = timelineEntries
        )
    }

    private fun createNoDataComplication(message: String): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = 0f,
            min = 0f,
            max = 1f,
            contentDescription = PlainComplicationText.Builder(message).build()
        )
            .setText(PlainComplicationText.Builder("N/A").build())
            .build()
    }

    private fun createStaticRangedValueData(hour: PlanetaryHour, value: Float = 0f): RangedValueComplicationData {
        val displayIndex = ((hour.index - 1) % 12) + 1
        val contentDescription = PlainComplicationText.Builder("Planetary Hour").build()
        val text = romanNumerals[displayIndex] ?: "N/A"
        val planetColor = hour.planet.color.toInt()

        return RangedValueComplicationData.Builder(
            value = value,
            min = 0f,
            max = 1f,
            contentDescription = contentDescription
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, hour.planet.iconResId)).build()
            )
            .setColorRamp(ColorRamp(intArrayOf(planetColor, planetColor), false))
            .build()
    }

    private fun createDynamicRangedValueData(hour: PlanetaryHour, startInst: Instant, endInst: Instant): ComplicationData {
        val durationSeconds = (endInst.epochSecond - startInst.epochSecond).coerceAtLeast(1L).toFloat()

        val nowDynamic = DynamicBuilders.DynamicInstant.platformTimeWithSecondsPrecision()
        val startDynamic = DynamicBuilders.DynamicInstant.withSecondsPrecision(startInst)
        
        // Progress: (now - start) / duration
        val dynamicProgress = startDynamic.durationUntil(nowDynamic)
            .toIntSeconds()
            .asFloat()
            .div(durationSeconds)

        val planetColor = hour.planet.color.toInt()
        val displayIndex = ((hour.index - 1) % 12) + 1
        val contentDescription = PlainComplicationText.Builder("Planetary Hour").build()
        val text = romanNumerals[displayIndex] ?: "N/A"

        return RangedValueComplicationData.Builder(
            dynamicValue = dynamicProgress,
            fallbackValue = 0f,
            min = 0f,
            max = 1f,
            contentDescription = contentDescription
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, hour.planet.iconResId)).build()
            )
            .setColorRamp(ColorRamp(intArrayOf(planetColor, planetColor), false))
            .build()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        val now = LocalDateTime.now()
        // Preview data MUST NOT contain dynamic values.
        return createStaticRangedValueData(
            PlanetaryHour(Planet.SUN, now, now.plusHours(1), 1),
            value = 0.5f // Show some progress in the preview
        )
    }
}
