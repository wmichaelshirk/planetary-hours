package dev.shirk.planetaryhours

import androidx.wear.protolayout.ActionBuilders
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
import java.time.Instant
import java.time.format.DateTimeFormatter

class PlanetaryTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val RESOURCES_VERSION = "127"
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        
        serviceScope.launch {
            try {
                PlanetaryRefresh.ensureScheduled(this@PlanetaryTileService)
                // Reads the cached location only; no location requests from here.
                val hours = PlanetaryHourCalculator.calculateForCachedLocation(this@PlanetaryTileService)
                when {
                    hours != null -> future.set(createTile(hours))
                    !PlanetaryLocation.hasLocationPermission(this@PlanetaryTileService) ->
                        future.set(createMessageTile("Permission required", "Tap to grant location permission in the app"))
                    else ->
                        future.set(createMessageTile("Location Missing", "Could not determine location. Tap to open app."))
                }
            } catch (e: Exception) {
                future.set(createMessageTile("Error", e.message ?: "Unknown error"))
            }
        }
        
        return future
    }

    private fun createMessageTile(title: String, subtitle: String): TileBuilders.Tile {
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                .setClassName(MainActivity::class.java.name)
                .setPackageName(packageName)
                .build())
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_app_msg")
            .setOnClick(launchAction)
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable)
                .setBackground(ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                    .build())
                .build())
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(title)
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(16f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                .build())
                            .build()
                    )
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(subtitle)
                            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                                .setPadding(ModifiersBuilders.Padding.Builder().setStart(DimensionBuilders.dp(16f)).setEnd(DimensionBuilders.dp(16f)).build())
                                .build())
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(12f))
                                .setItalic(true)
                                .setColor(ColorBuilders.argb(0xFF6495ED.toInt()))
                                .build())
                            .setMaxLines(3)
                            .build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                    .build())
                .build())
            .build()
    }

    private fun createTwilightIndicator(
        timeText: String,
        isSunrise: Boolean
    ): LayoutElementBuilders.LayoutElement {
        val arrowWidth = 7f
        val arrowHeight = 3.5f
        val twilightSize = 14f
        
        val iconColumn = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.dp(twilightSize))

        val arrow = LayoutElementBuilders.Image.Builder()
            .setResourceId(if (isSunrise) "ic_arrow_up" else "ic_arrow_down")
            .setWidth(DimensionBuilders.dp(arrowWidth))
            .setHeight(DimensionBuilders.dp(arrowHeight))
            .setColorFilter(LayoutElementBuilders.ColorFilter.Builder()
                .setTint(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                .build())
            .build()

        val twilight = LayoutElementBuilders.Image.Builder()
            .setResourceId("ic_twilight")
            .setWidth(DimensionBuilders.dp(twilightSize))
            .setHeight(DimensionBuilders.dp(twilightSize))
            .setColorFilter(LayoutElementBuilders.ColorFilter.Builder()
                .setTint(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                .build())
            .build()

        val textElement = LayoutElementBuilders.Text.Builder()
            .setText(timeText)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(10f))
                .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                .build())
            .build()

        val textColumn = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)

        if (isSunrise) {
            iconColumn.addContent(arrow)
            iconColumn.addContent(twilight)
            
            textColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(arrowHeight)).build())
            textColumn.addContent(textElement)
            
            return LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
                .addContent(iconColumn.build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                .addContent(textColumn.build())
                .build()
        } else {
            iconColumn.addContent(twilight)
            iconColumn.addContent(arrow)
            
            textColumn.addContent(textElement)
            textColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(arrowHeight)).build())
            
            return LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(iconColumn.build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                .addContent(textColumn.build())
                .build()
        }
    }

    private fun createTile(hours: List<PlanetaryHour>): TileBuilders.Tile {
        val timelineBuilder = TimelineBuilders.Timeline.Builder()
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        val now = Instant.now()
        val startIndex = hours.indexOfFirst { it.endTime.toInstant().isAfter(now) }.coerceAtLeast(0)
        
        // Take a window of 24 planetary hours for the timeline
        val window = hours.drop(startIndex).take(24)
        
        for (hour in window) {
            val i = hours.indexOf(hour)
            val cycleStart = i - (hour.index - 1)
            
            // Safety check for cycle range
            val sunriseTime = hours.getOrNull(cycleStart)?.startTime ?: hour.startTime
            val sunsetTime = hours.getOrNull(cycleStart + 12)?.startTime ?: hour.startTime.plusHours(12)
            val nextSunriseTime = hours.getOrNull(cycleStart + 23)?.endTime ?: hour.endTime.plusHours(12)

            val backgroundColor = hour.planet.color.toInt()
            val contentColor = if (isLight(backgroundColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

            val displayIndex = ((hour.index - 1) % 12) + 1
            val romanNumeralText = PlanetaryHourCalculator.romanNumerals[displayIndex] ?: ""

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

            val leftCard = LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(72f))
                .setHeight(DimensionBuilders.dp(100f))
                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(backgroundColor))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                        .build())
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(8f)).build())
                    .setClickable(ModifiersBuilders.Clickable.Builder()
                        .setId("open_app_main_${hour.startTime.hashCode()}")
                        .setOnClick(ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                                .setClassName(MainActivity::class.java.name)
                                .setPackageName(packageName)
                                .build())
                            .build())
                        .build())
                    .build())
                .addContent(
                    LayoutElementBuilders.Column.Builder()
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(LayoutElementBuilders.Text.Builder()
                            .setText(hour.startTime.format(timeFormatter))
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(12f))
                                .setColor(ColorBuilders.argb(contentColor))
                                .build())
                            .build())
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                        .addContent(symbolElement)
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                        .addContent(LayoutElementBuilders.Text.Builder()
                            .setText(hour.endTime.format(timeFormatter))
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(12f))
                                .setColor(ColorBuilders.argb(contentColor))
                                .build())
                            .build())
                        .build()
                )
                .build()

            val isDay = hour.index <= 12
            val leftColumn = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(if (isDay) createTwilightIndicator(sunriseTime.format(timeFormatter), true) 
                            else createTwilightIndicator(sunsetTime.format(timeFormatter), false))
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
                .addContent(leftCard)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
                .addContent(if (isDay) createTwilightIndicator(sunsetTime.format(timeFormatter), false) 
                            else createTwilightIndicator(nextSunriseTime.format(timeFormatter), true))
                .build()

            val nextHoursColumn = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

            for (j in 1..3) {
                val nextHour = hours.getOrNull(i + j) ?: break
                val nextDisplayIndex = ((nextHour.index - 1) % 12) + 1
                val nextRoman = PlanetaryHourCalculator.romanNumerals[nextDisplayIndex] ?: ""
                val nextColor = nextHour.planet.color.toInt()
                val nextContent = if (isLight(nextColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

                nextHoursColumn.addContent(LayoutElementBuilders.Text.Builder()
                    .setText(nextHour.startTime.format(timeFormatter))
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(10f))
                        .setColor(ColorBuilders.argb(0xBBFFFFFF.toInt()))
                        .build())
                    .build())
                nextHoursColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
                nextHoursColumn.addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.dp(72f))
                        .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setBackground(ModifiersBuilders.Background.Builder()
                                .setColor(ColorBuilders.argb(nextColor))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                                .build())
                            .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(4f)).build())
                            .build())
                        .addContent(LayoutElementBuilders.Row.Builder()
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(LayoutElementBuilders.Text.Builder().setText(nextRoman)
                                .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).setColor(ColorBuilders.argb(nextContent)).build()).build())
                            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                            .addContent(LayoutElementBuilders.Image.Builder().setResourceId(nextHour.planet.name).setWidth(DimensionBuilders.dp(28f)).setHeight(DimensionBuilders.dp(28f))
                                .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(ColorBuilders.argb(nextContent)).build()).build())
                            .build())
                        .build()
                )
                nextHoursColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
            }

            val root = LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .addContent(LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.weight(1f)).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).addContent(leftColumn).build())
                    .addContent(LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.weight(1f)).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).addContent(nextHoursColumn.build()).build())
                    .build())
                .build()

            timelineBuilder.addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                .setValidity(TimelineBuilders.TimeInterval.Builder()
                    .setStartMillis(hour.startTime.toInstant().toEpochMilli())
                    .setEndMillis(hour.endTime.toInstant().toEpochMilli())
                    .build())
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                .build())
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
        val resourcesBuilder = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        Planet.entries.forEach { planet ->
            resourcesBuilder.addIdToImageMapping(planet.name, ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(planet.iconResId).build()).build())
        }
        listOf("ic_twilight" to R.drawable.ic_twilight, "ic_arrow_up" to R.drawable.ic_arrow_up, "ic_arrow_down" to R.drawable.ic_arrow_down).forEach { (id, res) ->
            resourcesBuilder.addIdToImageMapping(id, ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build()).build())
        }
        val future = SettableFuture.create<ResourceBuilders.Resources>()
        future.set(resourcesBuilder.build())
        return future
    }
}
