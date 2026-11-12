package dev.shirk.planetaryhours

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object PlanetaryRefresh {
    private const val TAG = "PlanetaryRefresh"
    private const val DAILY_WORK_NAME = "daily_planetary_refresh"

    /**
     * Asks the system to re-query the complication and tile. Uses the application context so it is
     * safe to call from a BroadcastReceiver (receiver contexts cannot bind to services).
     */
    fun requestRefresh(context: Context) {
        val appContext = context.applicationContext
        try {
            ComplicationDataSourceUpdateRequester
                .create(appContext, ComponentName(appContext, PlanetaryComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: Exception) {
            Log.e(TAG, "Complication refresh failed", e)
        }
        try {
            TileService.getUpdater(appContext).requestUpdate(PlanetaryTileService::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Tile refresh failed", e)
        }
    }

    /** Idempotent: schedules the once-a-day recompute if it isn't already scheduled. */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyRefreshWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/**
 * Runs once a day. Checks the last known location (no GPS), re-registers passive location updates
 * (they're dropped on reboot/app update), and pushes a fresh multi-day timeline to the UI.
 */
class DailyRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PlanetaryLocation.refreshFromLastKnown(applicationContext)
        PlanetaryLocation.registerPassiveUpdates(applicationContext)
        PlanetaryRefresh.requestRefresh(applicationContext)
        return Result.success()
    }
}
