package io.github.akrishna87.lumen

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Photos taken near one another: within [PlaceIndex.RADIUS_KM] of the first photo found there. */
data class Place(val center: LatLon, val photos: List<MediaItem>) {
    /**
     * "13.08° N, 80.27° E". Lumen doesn't look up city names: that would send your photos'
     * locations to an online service.
     */
    val label: String
        get() = String.format(
            Locale.US, "%.2f° %s, %.2f° %s",
            abs(center.lat), if (center.lat >= 0) "N" else "S", abs(center.lon), if (center.lon >= 0) "E" else "W",
        )
}

/**
 * Where each photo was taken, read from its EXIF data once and cached by id and modified time, so
 * only new or changed photos are read again.
 */
object PlaceIndex {
    const val RADIUS_KM = 25.0
    private const val PREFS = "places"

    /**
     * Locations for [items] (photos only), reading the ones not cached yet. [progress] gets (done,
     * total). Without [canRead] (the location permission) Android hides locations, so nothing is
     * cached then: granting it later must still find them.
     */
    suspend fun locate(context: Context, items: List<MediaItem>, canRead: Boolean, progress: (Int, Int) -> Unit): Map<Long, LatLon> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val photos = items.filter { !it.isVideo }
        val found = HashMap<Long, LatLon>()
        val fresh = prefs.edit()
        photos.forEachIndexed { i, item ->
            // Cached as "<modified>" (no location) or "<modified>:<lat>,<lon>".
            val cached = prefs.getString(item.id.toString(), null)
            val entry = if (cached != null && cached.substringBefore(':') == item.modifiedSec.toString()) cached else {
                val at = read(context, item)
                (item.modifiedSec.toString() + (at?.let { ":${it.lat},${it.lon}" } ?: "")).also { if (canRead) fresh.putString(item.id.toString(), it) }
            }
            entry.substringAfter(':', "").takeIf { it.isNotEmpty() }?.split(',')?.let { (lat, lon) ->
                found[item.id] = LatLon(lat.toDouble(), lon.toDouble())
            }
            if (i % 25 == 0) progress(i, photos.size)
        }
        fresh.apply()
        progress(photos.size, photos.size)
        found
    }

    private fun read(context: Context, item: MediaItem): LatLon? = runCatching {
        // Android 10+ removes the location from photos unless the app asks for the original
        // (which needs the "access photo locations" permission).
        val uri = if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri) else item.uri
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).latLong }
            ?.takeIf { (lat, lon) -> lat != 0.0 || lon != 0.0 }
            ?.let { (lat, lon) -> LatLon(lat, lon) }
    }.getOrNull()

    /** Groups located photos into places, the biggest first. The map and the grid both use this. */
    fun group(items: List<MediaItem>, located: Map<Long, LatLon>): List<Place> {
        val centers = ArrayList<LatLon>()
        val members = ArrayList<MutableList<MediaItem>>()
        for (item in items) {
            val at = located[item.id] ?: continue
            val i = centers.indexOfFirst { distanceKm(it, at) <= RADIUS_KM }
            if (i >= 0) members[i] += item else {
                centers += at
                members += mutableListOf(item)
            }
        }
        return centers.indices.map { Place(centers[it], members[it]) }.sortedByDescending { it.photos.size }
    }

    private fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h))
    }
}
