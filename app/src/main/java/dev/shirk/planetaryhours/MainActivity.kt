package dev.shirk.planetaryhours

import android.Manifest
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            refreshLocationAndTriggerUpdates()
        } else {
            findViewById<TextView>(R.id.status_text).text = "Permission denied.\nApp cannot function."
            lifecycleScope.launch {
                delay(2000)
                triggerUpdates()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        if (PlanetaryHourCalculator.hasLocationPermission(this)) {
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

    private fun refreshLocationAndTriggerUpdates() {
        val statusText = findViewById<TextView>(R.id.status_text)
        statusText.text = "Fetching location..."
        
        lifecycleScope.launch {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                
                // 1. Try last known location first
                val lastLoc = try { fusedLocationClient.lastLocation.await() } catch (e: Exception) { null }
                if (lastLoc != null) {
                    Log.d("MainActivity", "Got lastKnownLocation: ${lastLoc.latitude}, ${lastLoc.longitude}")
                    PlanetaryHourCalculator.saveLocation(this@MainActivity, lastLoc.latitude, lastLoc.longitude)
                }

                // 2. Request fresh location fix
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(10000)
                    .build()
                
                val freshLoc = try { fusedLocationClient.getCurrentLocation(request, null).await() } catch (e: Exception) { null }
                if (freshLoc != null) {
                    Log.d("MainActivity", "Got fresh location: ${freshLoc.latitude}, ${freshLoc.longitude}")
                    PlanetaryHourCalculator.saveLocation(this@MainActivity, freshLoc.latitude, freshLoc.longitude)
                    statusText.text = "Location updated!"
                } else if (lastLoc == null) {
                    statusText.text = "Could not get location.\nCheck GPS settings."
                } else {
                    statusText.text = "Location synchronized."
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to refresh location", e)
                statusText.text = "Error: ${e.message}"
            } finally {
                delay(1500)
                triggerUpdates()
                finish()
            }
        }
    }

    private fun triggerUpdates() {
        Log.d("MainActivity", "Triggering updates for Tile and Complication")
        // Force an update for Complications
        val component = ComponentName(this, PlanetaryComplicationService::class.java)
        ComplicationDataSourceUpdateRequester.create(this, component).requestUpdateAll()
        
        // Force an update for Tiles
        TileService.getUpdater(this)
            .requestUpdate(PlanetaryTileService::class.java)
    }
}
