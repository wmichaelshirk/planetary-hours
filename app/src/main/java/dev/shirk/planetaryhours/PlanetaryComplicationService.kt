package dev.shirk.planetaryhours

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.*
import java.time.Instant
import java.time.ZonedDateTime

class PlanetaryComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null

        // Make sure the once-a-day recompute exists even if the app UI is never opened.
        PlanetaryRefresh.ensureScheduled(this)

        // Reads the cached location only; no location requests from here.
        val hours = PlanetaryHourCalculator.calculateForCachedLocation(this)
        if (hours.isNullOrEmpty()) {
            val data = if (PlanetaryLocation.hasLocationPermission(this)) {
                createNoDataComplication("No location")
            } else {
                createPermissionRequestData()
            }
            return ComplicationDataTimeline(defaultComplicationData = data, timelineEntries = emptyList())
        }

        val now = Instant.now()

        // One entry per planetary hour (~48+ entries). The watch face switches entries itself at
        // each hour boundary and animates progress from its own clock, so we don't need to be woken.
        val timelineEntries = hours
            .filter { it.endTime.toInstant().isAfter(now) }
            .map { hour ->
                val startInst = hour.startTime.toInstant()
                val endInst = hour.endTime.toInstant()
                TimelineEntry(
                    validity = TimeInterval(startInst, endInst),
                    complicationData = createRangedValueDataForHour(hour, startInst, endInst)
                )
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

    private fun createRangedValueDataForHour(hour: PlanetaryHour, startInst: Instant, endInst: Instant): RangedValueComplicationData {
        val displayIndex = ((hour.index - 1) % 12) + 1
        val text = PlanetaryHourCalculator.romanNumerals[displayIndex] ?: "N/A"
        val planetColor = hour.planet.color.toInt()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // progress = (now - start) / duration, evaluated continuously by the watch face.
        val durationSeconds = (endInst.epochSecond - startInst.epochSecond).coerceAtLeast(1L).toFloat()
        val dynamicProgress = DynamicBuilders.DynamicInstant.withSecondsPrecision(startInst)
            .durationUntil(DynamicBuilders.DynamicInstant.platformTimeWithSecondsPrecision())
            .toIntSeconds()
            .asFloat()
            .div(durationSeconds)

        return RangedValueComplicationData.Builder(
            dynamicValue = dynamicProgress,
            fallbackValue = 0f,
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
        val now = ZonedDateTime.now()
        val hour = PlanetaryHour(Planet.SUN, now, now.plusHours(1), 1)
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
