package dev.shirk.planetaryhours

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Owns the cached location used for all planetary-hour calculations.
 *
 * The complication and tile never query location themselves; they only read the cache.
 * The cache is updated by: opening the app, the daily refresh worker (last-known location only,
 * no GPS), and passive location updates that piggyback on other apps' location requests.
 */
object PlanetaryLocation {
    private const val TAG = "PlanetaryLocation"
    private const val PREFS_NAME = "location_prefs"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_ASKED_BACKGROUND = "asked_background"

    /** Moving less than this doesn't meaningfully change sunrise/sunset (~1 minute per 20 km). */
    private const val SIGNIFICANT_DISTANCE_METERS = 20_000f

    fun hasLocationPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun getCached(context: Context): Pair<Double, Double>? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAT)) return null
        return prefs.getFloat(KEY_LAT, 0f).toDouble() to prefs.getFloat(KEY_LON, 0f).toDouble()
    }

    fun save(context: Context, location: Location) {
        Log.i(TAG, "Saving location: ${location.latitude}, ${location.longitude}")
        prefs(context).edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .apply()
    }

    /** Saves [location] only if it is significantly far from the cached one. Returns true if saved. */
    fun updateIfMoved(context: Context, location: Location): Boolean {
        val cached = getCached(context)
        if (cached != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(cached.first, cached.second, location.latitude, location.longitude, distance)
            if (distance[0] < SIGNIFICANT_DISTANCE_METERS) return false
        }
        save(context, location)
        return true
    }

    /** Cheap check using the last known fix (never powers up GPS). Returns true if the cache changed. */
    @SuppressLint("MissingPermission")
    suspend fun refreshFromLastKnown(context: Context): Boolean {
        if (!hasLocationPermission(context)) return false
        val location = try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "lastLocation failed", e)
            null
        } ?: return false
        return updateIfMoved(context, location)
    }

    /**
     * Registers for passive location updates: we never request a fix ourselves, we just get told
     * when another app's fix shows we've moved [SIGNIFICANT_DISTANCE_METERS]. Delivery while the app
     * is in the background requires ACCESS_BACKGROUND_LOCATION. Re-registering is idempotent.
     */
    @SuppressLint("MissingPermission")
    fun registerPassiveUpdates(context: Context) {
        if (!hasLocationPermission(context)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, TimeUnit.HOURS.toMillis(1))
            .setMinUpdateDistanceMeters(SIGNIFICANT_DISTANCE_METERS)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, LocationUpdateReceiver::class.java),
            // Must be mutable so Fused Location can attach the LocationResult.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(request, pendingIntent)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not register passive updates", e)
        }
    }

    fun hasAskedForBackground(context: Context) = prefs(context).getBoolean(KEY_ASKED_BACKGROUND, false)

    fun markAskedForBackground(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED_BACKGROUND, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** Receives passive location updates registered in [PlanetaryLocation.registerPassiveUpdates]. */
class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val location = LocationResult.extractResult(intent)?.lastLocation ?: return
        if (PlanetaryLocation.updateIfMoved(context, location)) {
            Log.i("LocationUpdateReceiver", "Moved significantly; refreshing")
            PlanetaryRefresh.requestRefresh(context)
        }
    }
}
