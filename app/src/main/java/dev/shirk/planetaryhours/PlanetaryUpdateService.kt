package dev.shirk.planetaryhours

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class PlanetaryUpdateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PlanetaryUpdateService", "Executing background refresh")
        
        try {
            val component = ComponentName(this, PlanetaryComplicationService::class.java)
            ComplicationDataSourceUpdateRequester.create(this, component).requestUpdateAll()
        } catch (e: Exception) {
            Log.e("PlanetaryUpdateService", "Complication refresh failed", e)
        }

        try {
            TileService.getUpdater(this)
                .requestUpdate(PlanetaryTileService::class.java)
        } catch (e: Exception) {
            Log.e("PlanetaryUpdateService", "Tile refresh failed", e)
        }

        stopSelf()
        return START_NOT_STICKY
    }
}
