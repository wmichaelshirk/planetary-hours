package dev.shirk.planetaryhours

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PlanetaryUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("PlanetaryUpdateReceiver", "Received broadcast: ${intent.action}")
        
        // We use a Service to perform the update because requestUpdate can involve binding,
        // which is not allowed directly from a BroadcastReceiver context.
        try {
            val serviceIntent = Intent(context, PlanetaryUpdateService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("PlanetaryUpdateReceiver", "Failed to start update service", e)
        }
    }
}
