package io.github.akrishna87.lumen.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.LatLon
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.Place
import io.github.akrishna87.lumen.PlaceIndex
import io.github.akrishna87.lumen.Thumbnails
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import java.io.File

/**
 * Places: where photos were taken, on a map (OpenStreetMap; the only part of Lumen that goes online,
 * for map tiles) or as a list. Both show the same groups of photos.
 */
@Composable
fun PlacesScreen(state: LibraryState, onBack: () -> Unit, onPlace: (AlbumRef) -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    var view by rememberSaveable { mutableIntStateOf(0) } // 0 map, 1 grid

    // Android 10+ hides photo locations unless the app may "access photo locations".
    fun allowed() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    var canRead by remember { mutableStateOf(allowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canRead = allowed() }
    LaunchedEffect(Unit) { if (!canRead) ask.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) }

    var located by remember { mutableStateOf<Map<Long, LatLon>?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(state.items, canRead) {
        located = PlaceIndex.locate(context, state.items, canRead) { done, total -> progress = done to total }
    }
    val places = remember(located, state.items) { located?.let { PlaceIndex.group(state.items, it) }.orEmpty() }

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("Places", backLabel = "Albums", onBack = onBack)
        SegmentedControl(listOf("Map", "Grid"), view, { view = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        when {
            located == null -> Text(
                "Finding where your photos were taken… ${progress.first} of ${progress.second}",
                style = AppleType.subheadline,
                color = apple.secondaryLabel,
                modifier = Modifier.padding(16.dp),
            )
            places.isEmpty() -> EmptyState(
                AppIcons.Map,
                "No Places",
                if (canRead) "Photos taken with location turned on in the camera appear here."
                else "Allow Lumen to access photo locations (in Android's settings for Lumen) to see your places.",
            )
            // osmdroid draws its tiles past the map's own edges, over the title bar, unless clipped.
            view == 0 -> Box(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = "Map of ${Format.plural(places.size, "place")}" }) {
                PlacesMap(places) { onPlace(AlbumRef.place(it)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(places, key = { it.photos.first().id }) { place -> PlaceRow(place) { onPlace(AlbumRef.place(place)) } }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    val apple = LocalApple.current
    val count = Format.counts(place.photos.size, 0)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Place, $count" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(apple.placeholder)) {
            ThumbImage(place.photos.last(), Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.label, style = AppleType.body, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = AppleType.footnote, color = apple.secondaryLabel)
        }
        Icon(AppIcons.ChevronRight, null, tint = apple.tertiaryLabel, modifier = Modifier.size(18.dp))
    }
}

/** The OpenStreetMap map, with each place drawn as its newest photo and a count, like Photos' map. */
@Composable
private fun PlacesMap(places: List<Place>, onTap: (Place) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val thumbs = remember { HashMap<Long, Bitmap>() }
    val map = remember { newMap(context) }
    val overlay = remember { PlaceOverlay(context.resources.displayMetrics.density, thumbs) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(places) {
        overlay.places = places
        overlay.onTap = onTap
        fit(map, places)
        map.invalidate()
        places.forEach { place ->
            val cover = place.photos.last()
            Thumbnails.load(context, cover)?.let { thumbs[cover.id] = it; map.invalidate() }
        }
    }
    AndroidView(factory = { map.also { it.overlays.add(overlay) } }, modifier = Modifier.fillMaxSize())
}

private fun newMap(context: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName // OpenStreetMap asks apps to identify themselves
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 2.0
        overlays.add(CopyrightOverlay(context)) // "© OpenStreetMap contributors", as its licence asks
    }
}

/** Zooms to show every place (or one place up close), once the map has a size. */
private fun fit(map: MapView, places: List<Place>) {
    if (places.isEmpty()) return
    val run = {
        val points = places.map { GeoPoint(it.center.lat, it.center.lon) }
        if (points.size == 1) {
            map.controller.setZoom(11.0)
            map.controller.setCenter(points[0])
        } else {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.6f), false)
        }
    }
    if (map.width > 0) run() else map.addOnFirstLayoutListener { _, _, _, _, _ -> run() }
}

/** Draws each place as a white-framed photo with its count, and opens a place when it's tapped. */
private class PlaceOverlay(private val density: Float, private val thumbs: Map<Long, Bitmap>) : Overlay() {
    var places: List<Place> = emptyList()
    var onTap: (Place) -> Unit = {}
    private val frames = HashMap<Place, RectF>()
    private val point = Point()
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; setShadowLayer(6f, 0f, 2f, 0x55000000) }
    private val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5E5EA.toInt() }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF007AFF.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val side = 58 * density
        val border = 3 * density
        val radius = 9 * density
        frames.clear()
        for (place in places) {
            map.projection.toPixels(GeoPoint(place.center.lat, place.center.lon), point)
            val frame = RectF(point.x - side / 2, point.y - side, point.x + side / 2, point.y.toFloat())
            frames[place] = frame
            canvas.drawRoundRect(frame, radius, radius, white)
            val inner = RectF(frame.left + border, frame.top + border, frame.right - border, frame.bottom - border)
            val clip = Path().apply { addRoundRect(inner, radius - border, radius - border, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            val bmp = thumbs[place.photos.last().id]
            if (bmp != null) canvas.drawBitmap(bmp, centerCrop(bmp, inner), inner, photo) else canvas.drawRect(inner, grey)
            canvas.restore()
            // The count, in a blue circle at the top right.
            val r = 11 * density
            canvas.drawCircle(frame.right - 2 * density, frame.top + 2 * density, r, badge)
            text.textSize = 12 * density
            canvas.drawText(place.photos.size.toString(), frame.right - 2 * density, frame.top + 2 * density + 4.2f * density, text)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val hit = frames.entries.lastOrNull { it.value.contains(e.x, e.y) }?.key ?: return false
        onTap(hit)
        return true
    }

    private fun centerCrop(bmp: Bitmap, into: RectF): Rect {
        val ratio = into.width() / into.height()
        return if (bmp.width.toFloat() / bmp.height > ratio) {
            val w = (bmp.height * ratio).toInt()
            Rect((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
        } else {
            val h = (bmp.width / ratio).toInt()
            Rect(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2)
        }
    }
}
