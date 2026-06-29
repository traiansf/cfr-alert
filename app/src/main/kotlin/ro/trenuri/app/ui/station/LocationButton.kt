package ro.trenuri.app.ui.station

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Returns true if ACCESS_COARSE_LOCATION is already granted. */
internal fun isLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Reads the last-known device location via the platform LocationManager, trying
 * NETWORK_PROVIDER first then GPS_PROVIDER. Returns null if both are unavailable.
 *
 * Caller must have already verified that ACCESS_COARSE_LOCATION is granted.
 */
@SuppressLint("MissingPermission")
internal fun readLastKnownLocation(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = try {
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    } catch (_: SecurityException) {
        null
    }
    return loc?.let { Pair(it.latitude, it.longitude) }
}

/**
 * Returns a lambda that, when invoked by a user action (GPS button tap):
 * - If ACCESS_COARSE_LOCATION is already granted: reads last-known location and calls
 *   [onLocation], or [onDenied] if the last-known location is unavailable.
 * - Otherwise: launches the runtime permission request; on grant reads location and calls
 *   [onLocation] (or [onDenied] if unavailable); on deny calls [onDenied].
 *
 * NEVER calls [onLocation] without explicit user action — call sites must only invoke
 * the returned lambda in response to a GPS button tap.
 */
@Composable
fun rememberLocationRequester(
    onLocation: (lat: Double, lon: Double) -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val loc = readLastKnownLocation(context)
            if (loc != null) onLocation(loc.first, loc.second) else onDenied()
        } else {
            onDenied()
        }
    }
    return remember(context, launcher) {
        {
            if (isLocationGranted(context)) {
                val loc = readLastKnownLocation(context)
                if (loc != null) onLocation(loc.first, loc.second) else onDenied()
            } else {
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }
}
