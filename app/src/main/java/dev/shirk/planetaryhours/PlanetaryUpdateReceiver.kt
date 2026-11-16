package dev.shirk.planetaryhours

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Recomputes when the clock or time zone changes (both are exempt from the Android 8+ limits on
 * manifest-declared receivers).
 */
class PlanetaryUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received broadcast: ${intent.action}")
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    // A new time zone almost always means we travelled; sunrise/sunset can shift by up
                    // to ~an hour per zone. Check the last known fix (no GPS) before recomputing.
                    // Bounded well under the ~10s goAsync() budget.
                    val moved = withTimeoutOrNull(5_000) { PlanetaryLocation.refreshFromLastKnown(appContext) }
                    Log.i(TAG, "Time zone changed; location updated=$moved")
                }
                PlanetaryRefresh.requestRefresh(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PlanetaryUpdateReceiver"
    }
}
