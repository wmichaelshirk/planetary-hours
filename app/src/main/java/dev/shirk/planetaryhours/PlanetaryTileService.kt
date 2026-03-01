package dev.shirk.planetaryhours

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PlanetaryTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val RESOURCES_VERSION = "106"
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        
        serviceScope.launch {
            try {
                val hours = PlanetaryHourCalculator.calculatePlanetaryHoursForCurrentLocation(this@PlanetaryTileService)
                val tile = createTile(hours)
                future.set(tile)
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        
        return future
    }

    private fun createTile(hours: List<PlanetaryHour>): TileBuilders.Tile {
        val zoneId = ZoneId.systemDefault()
        val timelineBuilder = TimelineBuilders.Timeline.Builder()
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        // Anchor points for sunrise and sunset
        val sunriseTime = hours[0].startTime
        val sunsetTime = hours[12].startTime
        val nextSunriseTime = hours[23].endTime

        for (i in hours.indices) {
            val hour = hours[i]
            val backgroundColor = hour.planet.color.toInt()
            val contentColor = if (isLight(backgroundColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

            val displayIndex = ((hour.index - 1) % 12) + 1
            val romanNumeralText = PlanetaryHourCalculator.romanNumerals[displayIndex] ?: ""

            val startTimeElement = LayoutElementBuilders.Text.Builder()
                .setText(hour.startTime.format(timeFormatter))
                .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(12f))
                    .setColor(ColorBuilders.argb(contentColor))
                    .build())
                .build()

            // Symbol + Roman Numeral for current hour
            val symbolElement = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(romanNumeralText)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(16f))
                            .setColor(ColorBuilders.argb(contentColor))
                            .build())
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(6f)).build())
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(hour.planet.name)
                        .setWidth(DimensionBuilders.dp(40f))
                        .setHeight(DimensionBuilders.dp(40f))
                        .setColorFilter(LayoutElementBuilders.ColorFilter.Builder()
                            .setTint(ColorBuilders.argb(contentColor))
                            .build())
                        .build()
                )
                .build()

            val endTimeElement = LayoutElementBuilders.Text.Builder()
                .setText(hour.endTime.format(timeFormatter))
                .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(12f))
                    .setColor(ColorBuilders.argb(contentColor))
                    .build())
                .build()

            // Left Card (Current Hour) - Adjusted height and width to prevent clipping
            val leftCard = LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(72f))
                .setHeight(DimensionBuilders.dp(100f))
                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(backgroundColor))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                        .build())
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(8f)).build())
                    .build())
                .addContent(
                    LayoutElementBuilders.Column.Builder()
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(startTimeElement)
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                        .addContent(symbolElement)
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                        .addContent(endTimeElement)
                        .build()
                )
                .build()

            // Left Column with Sunrise/Sunset indicators
            val isDay = hour.index <= 12
            val headerText = if (isDay) "Sunrise ${sunriseTime.format(timeFormatter)}" else "Sunset ${sunsetTime.format(timeFormatter)}"
            val footerText = if (isDay) "Sunset ${sunsetTime.format(timeFormatter)}" else "Sunrise ${nextSunriseTime.format(timeFormatter)}"

            val leftColumn = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(headerText)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(10f))
                            .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                            .build())
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                .addContent(leftCard)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(footerText)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(10f))
                            .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                            .build())
                        .build()
                )
                .build()

            // Right column: Upcoming hours in a timeline style
            val nextHoursColumn = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

            for (j in 1..3) {
                val nextHour = hours[(i + j) % hours.size]
                val nextBackgroundColor = nextHour.planet.color.toInt()
                val nextContentColor = if (isLight(nextBackgroundColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                val nextDisplayIndex = ((nextHour.index - 1) % 12) + 1
                val nextRomanNumeralText = PlanetaryHourCalculator.romanNumerals[nextDisplayIndex] ?: ""

                // Show the start time "above" the chip, smaller
                nextHoursColumn.addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(nextHour.startTime.format(timeFormatter))
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(10f))
                            .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                            .build())
                        .build()
                )

                nextHoursColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())

                // Chip with Roman Numeral and Planet Icon
                nextHoursColumn.addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.dp(72f))
                        .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setBackground(ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(nextBackgroundColor))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                                .build())
                            .setPadding(ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(4f))
                                .setEnd(DimensionBuilders.dp(4f))
                                .setTop(DimensionBuilders.dp(4f))
                                .setBottom(DimensionBuilders.dp(4f))
                                .build())
                            .build())
                        .addContent(
                            LayoutElementBuilders.Row.Builder()
                                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                                .addContent(
                                    LayoutElementBuilders.Box.Builder()
                                        .setWidth(DimensionBuilders.dp(28f))
                                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                        .addContent(
                                            LayoutElementBuilders.Text.Builder()
                                                .setText(nextRomanNumeralText)
                                                .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(DimensionBuilders.sp(14f))
                                                    .setColor(ColorBuilders.argb(nextContentColor))
                                                    .build())
                                                .build()
                                        )
                                        .build()
                                )
                                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                                .addContent(
                                    LayoutElementBuilders.Image.Builder()
                                        .setResourceId(nextHour.planet.name)
                                        .setWidth(DimensionBuilders.dp(28f))
                                        .setHeight(DimensionBuilders.dp(28f))
                                        .setColorFilter(LayoutElementBuilders.ColorFilter.Builder()
                                            .setTint(ColorBuilders.argb(nextContentColor))
                                            .build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )

                nextHoursColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
            }
            
            // Final end time of the 3rd upcoming hour
            val lastHour = hours[(i + 3) % hours.size]
            nextHoursColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(lastHour.endTime.format(timeFormatter))
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(10f))
                        .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                        .build())
                    .build()
            )

            val root = LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .addContent(
                    LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setPadding(ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(8f))
                                .setEnd(DimensionBuilders.dp(8f))
                                .build())
                            .build())
                        .addContent(
                            LayoutElementBuilders.Box.Builder()
                                .setWidth(DimensionBuilders.weight(1f))
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                .addContent(leftColumn)
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Box.Builder()
                                .setWidth(DimensionBuilders.weight(1f))
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                .addContent(nextHoursColumn.build())
                                .build()
                        )
                        .build()
                )
                .build()

            val startMillis = hour.startTime.atZone(zoneId).toInstant().toEpochMilli()
            val endMillis = hour.endTime.atZone(zoneId).toInstant().toEpochMilli()

            timelineBuilder.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(TimelineBuilders.TimeInterval.Builder()
                        .setStartMillis(startMillis)
                        .setEndMillis(endMillis)
                        .build())
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                    .build()
            )
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timelineBuilder.build())
            .setFreshnessIntervalMillis(60 * 60 * 1000)
            .build()
    }

    private fun isLight(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance > 0.5
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resourcesBuilder = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)

        Planet.entries.forEach { planet ->
            resourcesBuilder.addIdToImageMapping(
                planet.name,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(planet.iconResId)
                            .build()
                    )
                    .build()
            )
        }

        val future = SettableFuture.create<ResourceBuilders.Resources>()
        future.set(resourcesBuilder.build())
        return future
    }
}
