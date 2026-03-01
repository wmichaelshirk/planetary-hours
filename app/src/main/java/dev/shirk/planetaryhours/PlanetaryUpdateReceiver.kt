package dev.shirk.planetaryhours

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class PlanetaryUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Force an update for our specific service
        val component = ComponentName(context, PlanetaryComplicationService::class.java)
        ComplicationDataSourceUpdateRequester.create(context, component).requestUpdateAll()
    }
}
