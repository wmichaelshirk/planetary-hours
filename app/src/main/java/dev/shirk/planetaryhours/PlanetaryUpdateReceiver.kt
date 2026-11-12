package dev.shirk.planetaryhours

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Recomputes when the clock or time zone changes (both are exempt from the Android 8+ limits on
 * manifest-declared receivers). A time zone change is also a good hint that we travelled.
 */
class PlanetaryUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("PlanetaryUpdateReceiver", "Received broadcast: ${intent.action}")
        // requestRefresh uses the application context, which (unlike a receiver context) may bind.
        PlanetaryRefresh.requestRefresh(context)
    }
}
