package io.github.akrishna87.lumen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.MediaItem
import io.github.akrishna87.lumen.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Which items are picked while "Select" is on. */
@Stable
class Selection {
    var active by mutableStateOf(false)
    val ids = mutableStateListOf<Long>()

    fun toggle(item: MediaItem) {
        if (!ids.remove(item.id)) ids += item.id
    }

    fun start() { active = true; ids.clear() }

    fun end() { active = false; ids.clear() }

    fun items(all: List<MediaItem>): List<MediaItem> = ids.toSet().let { picked -> all.filter { it.id in picked } }

    fun replace(picked: Set<Long>) {
        ids.clear()
        ids.addAll(picked)
    }
}

/**
 * Where each thumbnail is on screen, so the viewer can grow a photo out of its thumbnail and shrink
 * it back. A plain map, not Compose state: it changes on every scroll frame and nothing should
 * redraw because of it.
 */
object ThumbBounds {
    private class Entry(val owner: Any, val rect: Rect)

    private val map = HashMap<Long, Entry>()

    fun put(id: Long, owner: Any, rect: Rect) {
        map[id] = Entry(owner, rect)
    }

    fun remove(id: Long, owner: Any) {
        if (map[id]?.owner === owner) map.remove(id)
    }

    /** The thumbnail's rectangle in the window, if it's on screen right now. */
    fun visible(id: Long, windowHeight: Float): Rect? =
        map[id]?.rect?.takeIf { it.width > 0f && it.bottom > 0f && it.top < windowHeight }
}

/** The grid sizes Photos steps through when you pinch: 3 across by default. */
val GridSizes = listOf(3, 5, 7)

/**
 * The square photo grid with 1.5dp gaps. Tapping opens the viewer (or picks, while selecting);
 * long-pressing starts selecting; pinching steps through [GridSizes].
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    favorites: Set<Long>,
    state: LazyGridState,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: Selection,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (LazyGridScope.() -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
    badge: ((MediaItem) -> String)? = null,
    onPreview: ((MediaItem) -> Unit)? = null,
    /** The photo open in the viewer: the grid scrolls to it, so closing can shrink it back into place. */
    focusId: Long? = null,
    /** Items [header] adds before the photos, so scrolling to one lands on the right row. */
    leadingItems: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(focusId) {
        val id = focusId ?: return@LaunchedEffect
        val info = state.layoutInfo
        val cell = info.visibleItemsInfo.firstOrNull { it.key == id }
        val inView = cell != null && cell.offset.y >= info.viewportStartOffset && cell.offset.y + cell.size.height <= info.viewportEndOffset
        if (!inView) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) state.scrollToItem(index + leadingItems)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize().pinchToResize(columns, onColumnsChange)
            .dragToSelect(selection.active, state, items, selection, scope),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            Thumb(
                item = item,
                favorite = item.id in favorites,
                selecting = selection.active,
                selected = selection.active && item.id in selection.ids,
                small = columns >= 5,
                badge = badge?.invoke(item),
                onClick = { if (selection.active) selection.toggle(item) else onOpen(index) },
                // Press and hold shows a preview with a menu, as in Photos (not while selecting).
                onLongClick = if (onPreview != null && !selection.active) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPreview(item)
                    }
                } else null,
            )
        }
        footer?.invoke(this)
    }
}

/** "1,234 Photos, 56 Videos" under the last row, as in Photos. */
fun LazyGridScope.countFooter(items: List<MediaItem>) {
    item(key = "__count", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
        val apple = LocalApple.current
        Text(
            Format.counts(items.count { !it.isVideo }, items.count { it.isVideo }),
            style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = apple.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        )
    }
}

/** Two-finger pinch: spread to show fewer, bigger photos, pinch in for more. One finger still scrolls. */
private fun Modifier.pinchToResize(columns: Int, onChange: (Int) -> Unit): Modifier = pointerInput(columns) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var done = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2 && !done) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                val i = GridSizes.indexOf(columns).coerceAtLeast(0)
                when {
                    zoom > 1.3f && i > 0 -> { onChange(GridSizes[i - 1]); done = true }
                    zoom < 0.77f && i < GridSizes.lastIndex -> { onChange(GridSizes[i + 1]); done = true }
                }
            } else if (done) {
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * While selecting, a sideways drag picks (or un-picks) every photo from where it started to where
 * the finger is, scrolling when the finger nears the top or bottom. Up-and-down drags still scroll.
 */
private fun Modifier.dragToSelect(
    enabled: Boolean,
    grid: LazyGridState,
    items: List<MediaItem>,
    selection: Selection,
    scope: CoroutineScope,
): Modifier = if (!enabled) this else pointerInput(items) {
    val slop = viewConfiguration.touchSlop
    val edge = 72.dp.toPx()
    val maxSpeed = 22.dp.toPx()
    val indexOf = HashMap<Long, Int>(items.size * 2).apply { items.forEachIndexed { i, item -> put(item.id, i) } }

    /** The photo under a point in the grid. Cell offsets count from the start of the content, so add the top padding back. */
    fun hit(p: Offset): Int? {
        val info = grid.layoutInfo
        val y = p.y + info.viewportStartOffset
        val cell = info.visibleItemsInfo.firstOrNull {
            it.key is Long && p.x >= it.offset.x && p.x < it.offset.x + it.size.width && y >= it.offset.y && y < it.offset.y + it.size.height
        } ?: return null
        return indexOf[cell.key as Long]
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var travel = Offset.Zero
        var anchor = -1
        var adding = true
        var before: Set<Long> = emptySet()
        var finger = down.position
        var speed = 0f
        var scroller: Job? = null

        fun pick() {
            val at = hit(finger) ?: return
            val range = items.subList(min(anchor, at), max(anchor, at) + 1).mapTo(HashSet()) { it.id }
            selection.replace(if (adding) before + range else before - range)
        }

        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) break // a pinch
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (anchor < 0) {
                    travel += change.positionChange()
                    if (travel.getDistance() < slop) continue
                    if (abs(travel.x) <= abs(travel.y)) break // scrolling
                    anchor = hit(down.position) ?: break
                    before = selection.ids.toSet()
                    adding = items[anchor].id !in before
                }
                finger = change.position
                pick()
                event.changes.forEach { it.consume() }
                speed = when {
                    finger.y < edge -> -maxSpeed * (1f - finger.y / edge).coerceIn(0f, 1f)
                    finger.y > size.height - edge -> maxSpeed * ((finger.y - (size.height - edge)) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (speed != 0f && scroller == null) {
                    scroller = scope.launch {
                        while (isActive && speed != 0f) {
                            grid.scrollBy(speed)
                            pick() // the finger is still, but the photos under it have moved
                            delay(16)
                        }
                        scroller = null
                    }
                }
            }
        } finally {
            scroller?.cancel()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    favorite: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val apple = LocalApple.current
    val owner = remember { Any() }
    DisposableEffect(item.id) { onDispose { ThumbBounds.remove(item.id, owner) } }
    Box(
        modifier.aspectRatio(1f).background(apple.placeholder)
            .onGloballyPositioned { ThumbBounds.put(item.id, owner, it.boundsInWindow()) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = item.description; if (selecting) this.selected = selected },
    ) {
        ThumbImage(item, Modifier.fillMaxSize())
        if ((item.isVideo || favorite || badge != null) && !small) {
            // A soft scrim so the white duration and heart read over bright photos.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(2.6f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)))),
            )
        }
        if (favorite && !small) {
            Icon(AppIcons.HeartFill, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(13.dp))
        }
        if (item.isVideo && !selecting && !small) {
            Text(
                Format.duration(item.durationMs),
                style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold, shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 4f)),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
            )
        }
        if (badge != null && !small) {
            Text(
                badge,
                style = AppleType.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 4.dp),
            )
        }
        if (selecting) {
            if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
            SelectionBadge(selected, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
    }
}

/** The blue check circle on a picked photo (an empty ring on the others). */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) apple.tint else Color.Black.copy(alpha = 0.12f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(AppIcons.Checkmark, null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

/** The media library's thumbnail, filling (and by default cropping to) its box. */
@Composable
fun ThumbImage(item: MediaItem, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(Thumbnails.cached(item), item.id) {
        if (value == null) value = Thumbnails.load(context, item)
    }
    bitmap?.let {
        Image(it.asImageBitmap(), null, contentScale = contentScale, modifier = modifier)
    }
}
