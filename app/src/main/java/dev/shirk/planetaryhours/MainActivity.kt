package dev.shirk.planetaryhours

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The only place that actively requests a location fix. Opened on first run, or by tapping the
 * complication/tile when there's no data. Everything else uses the cached location.
 */
class MainActivity : ComponentActivity() {

    private val statusText: TextView by lazy { findViewById(R.id.status_text) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            refreshLocationAndTriggerUpdates()
        } else {
            statusText.text = "Permission denied.\nApp cannot function."
            finishAfterDelay()
        }
    }

    // Background location lets passive "you moved" updates arrive while the app is closed.
    // Optional: without it, location only updates when the app is opened.
    private val requestBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(TAG, "Background location granted: $granted")
        if (granted) PlanetaryLocation.registerPassiveUpdates(this)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (PlanetaryLocation.hasLocationPermission(this)) {
            refreshLocationAndTriggerUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshLocationAndTriggerUpdates() {
        statusText.text = "Fetching location..."

        lifecycleScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(this@MainActivity)

                val lastLoc = try { client.lastLocation.await() } catch (e: Exception) { null }
                lastLoc?.let { PlanetaryLocation.save(this@MainActivity, it) }

                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(10000)
                    .build()
                val freshLoc = try { client.getCurrentLocation(request, null).await() } catch (e: Exception) { null }
                freshLoc?.let { PlanetaryLocation.save(this@MainActivity, it) }

                statusText.text = when {
                    freshLoc != null -> "Location updated!"
                    lastLoc != null -> "Location synchronized."
                    else -> "Could not get location.\nCheck GPS settings."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh location", e)
                statusText.text = "Error: ${e.message}"
            }

            PlanetaryLocation.registerPassiveUpdates(this@MainActivity)
            PlanetaryRefresh.ensureScheduled(this@MainActivity)
            PlanetaryRefresh.requestRefresh(this@MainActivity)

            delay(1500)
            maybeRequestBackgroundThenFinish()
        }
    }

    /** Asks for background location at most once, ever, so tapping the complication stays quick. */
    private fun maybeRequestBackgroundThenFinish() {
        if (PlanetaryLocation.hasBackgroundPermission(this) || PlanetaryLocation.hasAskedForBackground(this)) {
            finish()
            return
        }
        PlanetaryLocation.markAskedForBackground(this)
        requestBackgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun finishAfterDelay() {
        lifecycleScope.launch {
            delay(2000)
            finish()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
