package io.github.akrishna87.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.Grouping
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem
import kotlinx.coroutines.launch

enum class LibraryView(val label: String) { YEARS("Years"), MONTHS("Months"), ALL("All Photos") }

/** What All Photos shows, from the filter menu, as in iOS 17. Each is one of the album lists, so the viewer swipes within it. */
enum class LibraryFilter(val label: String, val album: AlbumRef) {
    ALL("All Items", AlbumRef(AlbumRef.RECENTS, "Library")),
    FAVORITES("Favorites", AlbumRef(AlbumRef.FAVORITES, "Favorites")),
    PHOTOS("Photos", AlbumRef(AlbumRef.PHOTOS, "Photos")),
    VIDEOS("Videos", AlbumRef(AlbumRef.VIDEOS, "Videos")),
    EDITED("Edited", AlbumRef(AlbumRef.EDITED, "Edited")),
    SCREENSHOTS("Screenshots", AlbumRef(AlbumRef.SCREENSHOTS, "Screenshots")),
}

/** The Library tab's own state, kept while the person browses other tabs. */
@Stable
class LibraryUi {
    var view by mutableStateOf(LibraryView.ALL)
    var filter by mutableStateOf(LibraryFilter.ALL)
    /** The filter the grid was last scrolled for. */
    var gridFilter = LibraryFilter.ALL
    var columns by mutableIntStateOf(3)
    /** Set when a month or year card is tapped: jump there once the next view is up. */
    var pendingItemId by mutableStateOf<Long?>(null)
    var pendingMonth by mutableStateOf<Pair<Int, Int>?>(null)
    /** Whether the grid has been scrolled to the newest photos yet (it opens at the bottom, like Photos). */
    var scrolledToEnd = false
    val months = LazyListState()
    val years = LazyListState()
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    favorites: Set<Long>,
    ui: LibraryUi,
    gridState: LazyGridState,
    selection: Selection,
    actions: GalleryActions,
    onOpen: (AlbumRef, MediaItem) -> Unit,
    onPreview: (AlbumRef, MediaItem) -> Unit,
    focusId: Long?,
) {
    val apple = LocalApple.current
    val filter = ui.filter
    // The filter applies to All Photos; Years and Months always show everything.
    val items = remember(state, favorites, filter) { filter.album.items(state, favorites) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(apple.background)) {
        when {
            !state.loaded -> Unit
            state.items.isEmpty() -> Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(AppIcons.Photo, "No Photos or Videos", "Photos and videos you take or save on this phone will appear here.")
            }
            ui.view == LibraryView.ALL && items.isEmpty() -> Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(AppIcons.Filter, "No ${filter.label}", "Nothing in your library matches this filter.")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BarText("Show All Items") { ui.filter = LibraryFilter.ALL } }
            }
            ui.view == LibraryView.ALL -> {
                // A new filter opens at its newest items, like the Library itself.
                LaunchedEffect(filter) {
                    if (ui.gridFilter != filter) {
                        ui.gridFilter = filter
                        gridState.scrollToItem(items.size)
                    }
                }
                LaunchedEffect(items.size) {
                    if (!ui.scrolledToEnd && items.isNotEmpty()) {
                        gridState.scrollToItem(items.size) // the count footer, below the newest row
                        ui.scrolledToEnd = true
                    }
                }
                LaunchedEffect(ui.pendingItemId) {
                    val id = ui.pendingItemId ?: return@LaunchedEffect
                    val index = items.indexOfFirst { it.id == id }
                    if (index >= 0) gridState.scrollToItem(index)
                    ui.pendingItemId = null
                }
                MediaGrid(
                    items = items,
                    favorites = favorites,
                    state = gridState,
                    columns = ui.columns,
                    onColumnsChange = { ui.columns = it },
                    selection = selection,
                    onOpen = { onOpen(filter.album, items[it]) },
                    onPreview = { onPreview(filter.album, it) },
                    focusId = focusId,
                    contentPadding = PaddingValues(top = top + 52.dp, bottom = 76.dp),
                    footer = { countFooter(items) },
                )
                val range by remember(items) {
                    derivedStateOf {
                        val visible = gridState.layoutInfo.visibleItemsInfo.filter { it.index < items.size }
                        if (visible.isEmpty()) "" else Format.range(items[visible.first().index].date, items[visible.last().index].date)
                    }
                }
                LibraryHeader(range, selection, filter, onClear = { ui.filter = LibraryFilter.ALL }) {
                    actions.sheet = SheetRequest(
                        message = "Show",
                        actions = LibraryFilter.entries.map { f ->
                            SheetAction(if (f == filter) "${f.label} \u2713" else f.label) { ui.filter = f }
                        },
                    )
                }
            }
            ui.view == LibraryView.MONTHS -> {
                val months = state.months
                LaunchedEffect(months.size, ui.pendingMonth) {
                    val wanted = ui.pendingMonth
                    if (wanted != null) {
                        ui.months.scrollToItem(months.indexOfFirst { it.year == wanted.first && it.month == wanted.second }.coerceAtLeast(0))
                        ui.pendingMonth = null
                    } else if (ui.months.firstVisibleItemIndex == 0) {
                        ui.months.scrollToItem(months.size) // the newest months, at the bottom
                    }
                }
                CardList(ui.months, top) {
                    items(months, key = { "${it.year}-${it.month}" }) { month ->
                        MomentCard(
                            title = month.title,
                            subtitle = Format.counts(month.items.count { !it.isVideo }, month.items.count { it.isVideo }),
                            keyItems = month.items,
                            titleSize = 22,
                        ) {
                            ui.pendingItemId = month.items.first().id
                            ui.filter = LibraryFilter.ALL // the month's first item may not be in the filter
                            ui.view = LibraryView.ALL
                        }
                    }
                }
                PlainHeader("Months")
            }
            else -> {
                val years = remember(state.items) { Grouping.years(state.items) }
                LaunchedEffect(years.size) {
                    if (ui.years.firstVisibleItemIndex == 0) ui.years.scrollToItem(years.size)
                }
                CardList(ui.years, top) {
                    items(years, key = { it.first }) { (year, list) ->
                        MomentCard(title = year.toString(), subtitle = Format.counts(list.count { !it.isVideo }, list.count { it.isVideo }), keyItems = list, titleSize = 30) {
                            ui.pendingMonth = list.last().date.year to list.last().date.monthValue
                            ui.view = LibraryView.MONTHS
                        }
                    }
                }
                PlainHeader("Years")
            }
        }

        if (items.isNotEmpty() && !selection.active) {
            SegmentedControl(
                options = LibraryView.entries.map { it.label },
                selected = ui.view.ordinal,
                onSelect = {
                    val next = LibraryView.entries[it]
                    if (next == ui.view && next == LibraryView.ALL) scope.launch { gridState.animateScrollToItem(items.size) }
                    ui.view = next
                },
                track = apple.material,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).width(300.dp)
                    .shadow(10.dp, RoundedCornerShape(9.dp), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f)),
            )
        }
    }
}

/** Photos' Library header: the dates on screen in bold over a soft scrim, with the Select capsule. */
@Composable
private fun LibraryHeader(range: String, selection: Selection, filter: LibraryFilter, onClear: () -> Unit, onFilter: () -> Unit) {
    val apple = LocalApple.current
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.18f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 26.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(end = 130.dp)) {
            Text(range, style = AppleType.title2.copy(fontSize = 21.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (filter != LibraryFilter.ALL) {
                // Which filter is on; tapping it shows everything again.
                Text(
                    "${filter.label}  \u2715",
                    style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(apple.tint).tap(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 3.dp).semantics { contentDescription = "Showing ${filter.label}" },
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            if (!selection.active) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (filter != LibraryFilter.ALL) apple.tint else Color.Black.copy(alpha = 0.32f))
                        .tap(onClick = onFilter).semantics { contentDescription = "Filter" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Filter, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            CapsuleButton(if (selection.active) "Cancel" else "Select", overMedia = true) {
                if (selection.active) selection.end() else selection.start()
            }
        }
    }
}

/** Years and Months: a header that blends into the system background like a large title. */
@Composable
private fun PlainHeader(title: String) {
    val apple = LocalApple.current
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(apple.background, apple.background.copy(alpha = 0.85f), apple.background.copy(alpha = 0f))))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
    ) {
        Text(title, style = AppleType.title1, color = apple.label)
    }
}

@Composable
private fun CardList(state: LazyListState, top: androidx.compose.ui.unit.Dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 56.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        item(key = "__end") { Spacer(Modifier.height(1.dp)) }
    }
}

/** A big rounded card with a key photo and a bold title, like Photos' Years and Months. */
@Composable
private fun MomentCard(title: String, subtitle: String, keyItems: List<MediaItem>, titleSize: Int, onClick: () -> Unit) {
    val apple = LocalApple.current
    val key = remember(keyItems) { keyItems.lastOrNull { !it.isVideo } ?: keyItems.last() }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.18f).shadow(6.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .background(apple.placeholder).clickable(onClick = onClick),
    ) {
        if (key.isVideo) {
            ThumbImage(key, Modifier.fillMaxSize())
        } else {
            AsyncImage(model = key.uri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.4f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
        Column(Modifier.padding(16.dp)) {
            Text(title, style = AppleType.title1.copy(fontSize = titleSize.sp, lineHeight = (titleSize + 6).sp), color = Color.White)
            Text(subtitle, style = AppleType.subheadline.copy(fontWeight = FontWeight.Medium), color = Color.White.copy(alpha = 0.9f))
        }
        if (keyItems.size > 1) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A strip of a few more photos from the period, as Photos shows on its cards.
                keyItems.asReversed().filter { it.id != key.id }.take(5).forEach {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(apple.placeholder)) {
                        ThumbImage(it, Modifier.fillMaxSize())
                    }
                }
                repeat((5 - (keyItems.size - 1)).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
