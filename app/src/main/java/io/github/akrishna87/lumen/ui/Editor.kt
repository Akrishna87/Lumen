package io.github.akrishna87.lumen.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Aspect
import io.github.akrishna87.lumen.EditRenderer
import io.github.akrishna87.lumen.EditStore
import io.github.akrishna87.lumen.Edits
import io.github.akrishna87.lumen.Filter
import io.github.akrishna87.lumen.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private enum class Tab(val label: String) { ADJUST("Adjust"), FILTERS("Filters"), CROP("Crop") }

private enum class Tool(val label: String, val icon: ImageVector) {
    BRIGHTNESS("Brightness", AppIcons.Sun), CONTRAST("Contrast", AppIcons.Contrast),
    SATURATION("Saturation", AppIcons.Drop), WARMTH("Warmth", AppIcons.Thermometer),
}

private const val PREVIEW_MAX = 1280

private operator fun Edits.get(tool: Tool): Float = when (tool) {
    Tool.BRIGHTNESS -> brightness
    Tool.CONTRAST -> contrast
    Tool.SATURATION -> saturation
    Tool.WARMTH -> warmth
}

/**
 * Photos' editor, on black: Cancel / Done, the photo, and Adjust, Filters and Crop. Done saves a
 * new copy and leaves the original as it was.
 */
@Composable
fun Editor(item: MediaItem, onCancel: () -> Unit, onSaved: (Long?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val yellow = Color(0xFFFFD60A) // the editor's accent, as in Photos
    var edits by remember { mutableStateOf(Edits()) }
    var tab by remember { mutableStateOf(Tab.ADJUST) }
    var tool by remember { mutableStateOf(Tool.BRIGHTNESS) }
    var saving by remember { mutableStateOf(false) }

    BackHandler { if (!saving) onCancel() }

    val source by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { EditStore.decode(context, item, PREVIEW_MAX) }
    }
    // The preview goes through the same renderer as the saved copy, a moment after each change.
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, edits) {
        val src = source ?: return@LaunchedEffect
        delay(16)
        preview = withContext(Dispatchers.Default) { EditRenderer.render(src, edits) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { BarText("Cancel", color = Color.White, enabled = !saving, onClick = onCancel) }
            if (edits != Edits() && !saving) {
                Box(Modifier.align(Alignment.Center)) { BarText("Revert", color = yellow) { edits = Edits() } }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                BarText(if (saving) "Saving…" else "Done", bold = true, color = yellow, enabled = !saving && source != null) {
                    saving = true
                    scope.launch { onSaved(EditStore.saveCopy(context, item, edits)) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            (preview ?: source)?.let {
                Image(it.asImageBitmap(), contentDescription = "Edited photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }

        when (tab) {
            Tab.ADJUST -> {
                val value = edits[tool]
                Text(
                    "${tool.label.uppercase(Locale.getDefault())}  ${(value * 100).roundToInt()}",
                    style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tool.entries.forEach { t ->
                        ToolButton(t, selected = t == tool, changed = edits[t] != 0f, yellow = yellow) { tool = t }
                    }
                }
                Ruler(value, range = 1f, yellow = yellow, description = tool.label) { v ->
                    edits = when (tool) {
                        Tool.BRIGHTNESS -> edits.copy(brightness = v)
                        Tool.CONTRAST -> edits.copy(contrast = v)
                        Tool.SATURATION -> edits.copy(saturation = v)
                        Tool.WARMTH -> edits.copy(warmth = v)
                    }
                }
            }
            Tab.FILTERS -> FilterStrip(source, edits.filter, yellow) { edits = edits.copy(filter = it) }
            Tab.CROP -> {
                Text(
                    "STRAIGHTEN  ${edits.straighten.roundToInt()}°",
                    style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Ruler(edits.straighten / 45f, range = 1f, yellow = yellow, description = "Straighten") { edits = edits.copy(straighten = it * 45f) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable { edits = edits.copy(turns = (edits.turns + 1) % 4) }
                            .semantics { contentDescription = "Rotate" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.RotateLeft, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.size(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Aspect.entries) { a ->
                            val on = a == edits.aspect
                            Text(
                                a.label,
                                style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = if (on) Color.Black else Color.White,
                                modifier = Modifier.clip(CircleShape).background(if (on) yellow else Color.White.copy(alpha = 0.14f))
                                    .clickable { edits = edits.copy(aspect = a) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(49.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                Text(
                    t.label,
                    style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = if (t == tab) yellow else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tab = t }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, selected: Boolean, changed: Boolean, yellow: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .border(1.5.dp, if (selected) yellow else Color.White.copy(alpha = 0.35f), CircleShape)
            .background(if (changed && !selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick).semantics { contentDescription = tool.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(tool.icon, null, tint = if (selected) yellow else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Photos' ruler: ticks you slide under a fixed centre line. Drag left or right; 0 sits in the middle. */
@Composable
private fun Ruler(value: Float, range: Float, yellow: Color, description: String, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    Canvas(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp)
            .semantics { contentDescription = description }
            .pointerInput(description) {
                var v = current
                detectHorizontalDragGestures(onDragStart = { v = current }) { change, dx ->
                    change.consume()
                    v = (v - dx / (size.width / 2f) * range).coerceIn(-range, range)
                    onChange(v)
                }
            },
    ) {
        val mid = size.width / 2f
        val spacing = size.width / 40f
        val shift = -value / range * (size.width / 2f)
        for (i in -40..40) {
            val x = mid + shift + i * spacing
            if (x < 0f || x > size.width) continue
            val major = i % 5 == 0
            drawLine(
                Color.White.copy(alpha = if (i == 0) 0.9f else 0.35f),
                Offset(x, size.height * (if (major) 0.25f else 0.38f)),
                Offset(x, size.height * (if (major) 0.75f else 0.62f)),
                strokeWidth = if (i == 0) 3f else 2f,
            )
        }
        drawLine(yellow, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 4f)
    }
}

/** Each filter's name over a small copy of the photo with that filter. */
@Composable
private fun FilterStrip(source: Bitmap?, selected: Filter, yellow: Color, onPick: (Filter) -> Unit) {
    val thumbs by produceState<Map<Filter, Bitmap>>(emptyMap(), source) {
        val src = source ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val scale = 160f / maxOf(src.width, src.height)
            val small = Bitmap.createScaledBitmap(src, maxOf(1, (src.width * scale).roundToInt()), maxOf(1, (src.height * scale).roundToInt()), true)
            Filter.entries.associateWith { EditRenderer.render(small, Edits(filter = it, aspect = Aspect.SQUARE)) }
        }
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Filter.entries) { f ->
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(f) }.semantics(mergeDescendants = true) { contentDescription = f.label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(f.label.uppercase(Locale.getDefault()), style = AppleType.caption2.copy(fontWeight = FontWeight.SemiBold), color = if (f == selected) yellow else Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))
                        .border(if (f == selected) 2.dp else 0.dp, if (f == selected) yellow else Color.Transparent, RoundedCornerShape(6.dp)),
                ) {
                    thumbs[f]?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
