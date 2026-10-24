package io.github.akrishna87.lumen.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import io.github.akrishna87.lumen.Access
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.GalleryViewModel
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem
import io.github.akrishna87.lumen.Memory
import io.github.akrishna87.lumen.Place
import io.github.akrishna87.lumen.HiddenLock
import io.github.akrishna87.lumen.UserAlbum
import androidx.fragment.app.FragmentActivity

/** An album-like list of items: a folder, Favorites, a media type, or Recently Deleted. */
data class AlbumRef(val key: String, val title: String) {
    fun items(state: LibraryState, favorites: Set<Long>): List<MediaItem> = when {
        key == RECENTS -> state.items
        key == FAVORITES -> state.items.filter { it.id in favorites }
        key == VIDEOS -> state.videos
        key == SCREENSHOTS -> state.screenshots
        key == PANORAMAS -> state.panoramas
        key == ANIMATED -> state.animated
        key == TRASH -> state.trash
        key.startsWith(FOLDER) -> state.albums.firstOrNull { it.folder == key.removePrefix(FOLDER) }?.items.orEmpty()
        key.startsWith(MONTH) -> key.removePrefix(MONTH).split('-').let { (y, m) ->
            state.items.filter { it.date.year == y.toInt() && it.date.monthValue == m.toInt() }
        }
        key.startsWith(SEARCH) -> searchWords(key.removePrefix(SEARCH)).let { words -> state.items.filter { it.matches(words, state.text[it.id].orEmpty()) } }
        key == HIDDEN -> state.hidden
        key.startsWith(PLACE) -> key.removePrefix(PLACE).split(',').mapNotNull { it.toLongOrNull() }.toSet()
            .let { ids -> state.items.filter { it.id in ids } }
        key.startsWith(USER) -> state.userAlbums.firstOrNull { it.album.id == userAlbumId }?.items.orEmpty()
        else -> emptyList()
    }

    val isTrash: Boolean get() = key == TRASH
    val isHidden: Boolean get() = key == HIDDEN

    /** Set for the person's own albums. */
    val userAlbumId: String? get() = if (key.startsWith(USER)) key.removePrefix(USER) else null

    /** The title to show now: an album the person renamed shows its new name. */
    fun currentTitle(state: LibraryState): String =
        userAlbumId?.let { id -> state.userAlbums.firstOrNull { it.album.id == id }?.album?.name } ?: title

    companion object {
        const val RECENTS = "recents"
        const val FAVORITES = "favorites"
        const val VIDEOS = "videos"
        const val SCREENSHOTS = "screenshots"
        const val PANORAMAS = "panoramas"
        const val ANIMATED = "animated"
        const val TRASH = "trash"
        const val FOLDER = "folder:"
        const val MONTH = "month:"
        const val SEARCH = "search:"
        const val HIDDEN = "hidden"
        const val USER = "user:"
        const val PLACE = "place:"

        fun folder(folder: String, name: String) = AlbumRef(FOLDER + folder, name)
        fun month(year: Int, month: Int, title: String) = AlbumRef("$MONTH$year-$month", title)
        fun search(query: String) = AlbumRef(SEARCH + query, "Search")
        fun user(id: String, name: String) = AlbumRef(USER + id, name)
        fun place(place: Place) = AlbumRef(PLACE + place.photos.joinToString(",") { it.id.toString() }, place.label)

        fun searchWords(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}

sealed interface Route {
    data class AlbumDetail(val album: AlbumRef, val backLabel: String) : Route
    data object AllAlbums : Route
    data object Places : Route
    data object Duplicates : Route
}

/** What the full-screen viewer is showing. */
data class ViewerTarget(val album: AlbumRef, val startId: Long)

/** The tab bar's tabs, in order, as in Photos on iPhone. */
enum class AppTab(val label: String, val icon: ImageVector) {
    LIBRARY("Library", AppIcons.Library),
    FOR_YOU("For You", AppIcons.ForYou),
    ALBUMS("Albums", AppIcons.Albums),
    SEARCH("Search", AppIcons.Search),
}

data class SheetRequest(val message: String?, val actions: List<SheetAction>)

data class NameRequest(val title: String, val message: String, val initial: String, val onSave: (String) -> Unit)

/** Things any screen can do with photos: share, favourite, delete. */
@Stable
class GalleryActions(
    private val vm: GalleryViewModel,
    private val activity: Activity?,
) {
    /** Shows Android's own "Allow Lumen to…?" confirmation; set once the screen is up. */
    var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    /** Before Android 11 there's no system confirmation, so the app asks itself, like Photos does. */
    var pendingDelete by mutableStateOf<List<MediaItem>?>(null)

    /**
     * Items just deleted, recovered or deleted for good. The viewer hides them at once (with an
     * animation) rather than waiting for the library to reload.
     */
    var removed by mutableStateOf(emptySet<Long>())

    /**
     * Set while Android shows something over Lumen on its behalf (the share sheet, a confirmation,
     * the unlock prompt), so the Hidden album doesn't re-lock just because of that.
     */
    var leavingApp = false

    /** An action sheet to show (the ••• menus, album options, confirmations). */
    var sheet by mutableStateOf<SheetRequest?>(null)

    /** Photos waiting for an album to be picked in the Add to Album sheet. */
    var picking by mutableStateOf<List<MediaItem>?>(null)

    /** The New Album / Rename Album dialog. */
    var naming by mutableStateOf<NameRequest?>(null)

    /** What the confirmation on screen is for, so its result can be acted on. */
    private var waiting: List<MediaItem> = emptyList()
    private var retryDelete = false

    fun share(items: List<MediaItem>) {
        val activity = activity ?: return
        if (items.isEmpty()) return
        val type = when {
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val uris = ArrayList<Uri>(items.map { it.uri })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = type
        send.clipData = ClipData.newUri(activity.contentResolver, "Photos", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        leavingApp = true
        activity.startActivity(Intent.createChooser(send, null))
    }

    fun toggleFavorite(item: MediaItem) = vm.toggleFavorite(item)

    fun setHidden(items: List<MediaItem>, hidden: Boolean) {
        vm.setHidden(items, hidden)
        removed = removed + items.map { it.id } // the viewer moves on to the next photo
    }

    fun addToAlbum(albumId: String, items: List<MediaItem>) = vm.addToAlbum(albumId, items)

    fun removeFromAlbum(albumId: String, items: List<MediaItem>) {
        vm.removeFromAlbum(albumId, items)
        removed = removed + items.map { it.id }
    }

    /** Asks for a name, makes the album, then (optionally) does something with it. */
    fun newAlbum(then: ((UserAlbum) -> Unit)? = null) {
        naming = NameRequest("New Album", "Enter a name for this album.", "") { name -> vm.createAlbum(name).also { then?.invoke(it) } }
    }

    /** The ••• menu for some photos: add to an album, hide or unhide, remove from this album. */
    fun more(items: List<MediaItem>, album: AlbumRef?, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        val userAlbum = album?.userAlbumId
        sheet = SheetRequest(
            message = null,
            actions = listOfNotNull(
                SheetAction("Add to Album") { picking = items; after() },
                userAlbum?.let { id -> SheetAction("Remove from Album") { removeFromAlbum(id, items); after() } },
                if (album?.isHidden == true) SheetAction("Unhide") { setHidden(items, false); after() }
                else SheetAction("Hide") { setHidden(items, true); after() },
            ),
        )
    }

    /** The ••• menu on one of the person's albums. */
    fun albumOptions(id: String, name: String) {
        sheet = SheetRequest(
            message = null,
            actions = listOf(
                SheetAction("Rename Album") {
                    naming = NameRequest("Rename Album", "Enter a new name for this album.", name) { vm.renameAlbum(id, it) }
                },
                SheetAction("Delete Album", destructive = true) {
                    sheet = SheetRequest(
                        message = "Delete the album \u201C$name\u201D? The photos in it won't be deleted.",
                        actions = listOf(SheetAction("Delete Album", destructive = true) { vm.deleteAlbum(id) }),
                    )
                },
            ),
        )
    }

    /**
     * Keeps the oldest of a set of duplicates and deletes the rest (after Android's confirmation),
     * first giving the kept photo any heart and albums the others had.
     */
    fun merge(copies: List<MediaItem>) {
        val keep = copies.minByOrNull { it.id } ?: return
        val rest = copies - keep
        if (rest.any { it.id in vm.favorites.value }) vm.setFavorite(listOf(keep), true)
        vm.state.value.userAlbums
            .filter { entry -> rest.any { it.id in entry.album.ids } }
            .forEach { vm.addToAlbum(it.album.id, listOf(keep)) }
        delete(rest)
    }

    /** Puts the photo on the clipboard, for pasting into a chat or a note. */
    fun copy(item: MediaItem) {
        val activity = activity ?: return
        val clipboard = activity.getSystemService(android.content.ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newUri(activity.contentResolver, item.fileName, item.uri)) }
    }

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) = vm.setFavorite(items, favorite)

    fun delete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) launch(items, vm.deleteRequest(items)) else pendingDelete = items
    }

    /** After the app's own confirmation (Android 8–10). */
    fun confirmDelete(items: List<MediaItem>) {
        val sender = runCatching { vm.deleteRequest(items) }.getOrNull()
        // Android 10 may still ask for permission for photos other apps made; the delete is retried after.
        if (sender == null) markRemoved(items) else launch(items, sender, retry = true)
    }

    fun recover(items: List<MediaItem>) = launch(items, vm.recoverRequest(items))

    fun deleteForever(items: List<MediaItem>) = launch(items, vm.deleteForeverRequest(items))

    /** Called with the answer to Android's confirmation. */
    fun onResult(allowed: Boolean) {
        leavingApp = false
        val items = waiting
        waiting = emptyList()
        if (!allowed || items.isEmpty()) return
        if (retryDelete) {
            retryDelete = false
            confirmDelete(items)
        } else {
            markRemoved(items)
        }
    }

    /** Closing the viewer forgets what it hid; the reloaded library has caught up by then. */
    fun forgetRemoved() {
        removed = emptySet()
    }

    private fun markRemoved(items: List<MediaItem>) {
        removed = removed + items.map { it.id }
        vm.refresh()
    }

    private fun launch(items: List<MediaItem>, sender: IntentSender?, retry: Boolean = false) {
        val launcher = launcher ?: return
        if (sender == null) return
        waiting = items
        retryDelete = retry
        leavingApp = true
        launcher.launch(IntentSenderRequest.Builder(sender).build())
    }
}

@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val apple = LocalApple.current
    val access by vm.access.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val textProgress by vm.textProgress.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val requestAccess = { permissionLauncher.launch(GalleryViewModel.permissions) }

    if (access == Access.NONE) {
        WelcomeScreen(onContinue = requestAccess)
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val actions = remember(vm, activity) { GalleryActions(vm, activity) }
    actions.launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        actions.onResult(result.resultCode == Activity.RESULT_OK)
        vm.refresh()
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stacks = remember { List(AppTab.entries.size) { mutableStateListOf<Route>() } }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }
    // The photo open in the viewer, which the grid underneath keeps in view.
    var focusId by remember { mutableStateOf<Long?>(null) }
    // Press-and-hold preview: the photo and the album it was pressed in.
    var preview by remember { mutableStateOf<Pair<AlbumRef, MediaItem>?>(null) }
    // The memory playing as a slideshow.
    var slideshow by remember { mutableStateOf<Memory?>(null) }
    // An edited copy waiting to appear in the library, so the viewer can switch to it.
    var editedCopy by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val selection = remember { Selection() }

    // The Library's own state lives here so it's kept while browsing albums or the viewer.
    val library = remember { LibraryUi() }
    val libraryGrid = rememberLazyGridState()

    val open: (AlbumRef, MediaItem) -> Unit = { album, item ->
        // Forget photos marked as removed outside a viewer (say, unhidden from Select mode), or
        // this viewer would skip them.
        actions.forgetRemoved()
        focusId = item.id
        viewer = ViewerTarget(album, item.id)
    }
    val showPreview: (AlbumRef, MediaItem) -> Unit = { album, item -> preview = album to item }
    val push: (Route) -> Unit = { route ->
        selection.end()
        stacks[tab].add(route)
    }

    // The Hidden album opens after the fingerprint, face or screen lock, and locks again when you
    // leave it or Lumen goes to the background.
    var hiddenUnlocked by remember { mutableStateOf(false) }
    val showingHidden = stacks.any { stack -> stack.any { it is Route.AlbumDetail && it.album.isHidden } } ||
        viewer?.album?.isHidden == true || preview?.first?.isHidden == true
    LaunchedEffect(showingHidden) { if (!showingHidden) hiddenUnlocked = false }
    fun lockHidden() {
        // Only a selection inside Hidden ends; one in the Library survives a trip to the home screen.
        if (selection.active && (stacks[tab].lastOrNull() as? Route.AlbumDetail)?.album?.isHidden == true) selection.end()
        hiddenUnlocked = false
        stacks.forEach { stack -> stack.removeAll { it is Route.AlbumDetail && it.album.isHidden } }
        if (viewer?.album?.isHidden == true) {
            viewer = null
            focusId = null
        }
        if (preview?.first?.isHidden == true) preview = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!actions.leavingApp) lockHidden() }
    // Some of Android's screens (the unlock prompt on Android 11+, delete confirmations) don't stop
    // Lumen at all, so clear the flag on resume and when they answer, not only on start.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { actions.leavingApp = false }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.leavingApp = false }
    val openHidden: () -> Unit = {
        val route = Route.AlbumDetail(AlbumRef(AlbumRef.HIDDEN, "Hidden"), "Albums")
        val fragmentActivity = activity as? FragmentActivity
        if (hiddenUnlocked || !HiddenLock.isSecure(context)) {
            hiddenUnlocked = true
            push(route)
        } else if (fragmentActivity != null) {
            actions.leavingApp = true // the PIN screen (Android 10 and older) stops Lumen
            HiddenLock.unlock(fragmentActivity, onDone = { actions.leavingApp = false }) {
                hiddenUnlocked = true
                push(route)
            }
        }
    }

    BackHandler(enabled = selection.active && viewer == null) { selection.end() }
    BackHandler(enabled = stacks[tab].isNotEmpty() && viewer == null && !selection.active) { stacks[tab].removeAt(stacks[tab].lastIndex) }
    BackHandler(enabled = tab != 0 && stacks[tab].isEmpty() && viewer == null && !selection.active) { tab = 0 }

    Box(Modifier.fillMaxSize().background(apple.background)) {
        // Behind a preview the app blurs (Android 12+; older versions just dim, below).
        Column(Modifier.fillMaxSize().blur(if (preview != null) 18.dp else 0.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val route = stacks[tab].lastOrNull()
                val depth = stacks[tab].size
                AnimatedContent(
                    targetState = Triple(tab, depth, route),
                    transitionSpec = {
                        when {
                            targetState.first != initialState.first -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                            targetState.second > initialState.second ->
                                slideInHorizontally(tween(320)) { it } togetherWith slideOutHorizontally(tween(320)) { -it / 3 }
                            else -> slideInHorizontally(tween(320)) { -it / 3 } togetherWith slideOutHorizontally(tween(320)) { it }
                        }
                    },
                    label = "nav",
                ) { (t, _, r) ->
                    when (r) {
                        is Route.AlbumDetail -> AlbumScreen(
                            album = r.album,
                            backLabel = r.backLabel,
                            state = state,
                            favorites = favorites,
                            selection = selection,
                            actions = actions,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                            onOpen = open,
                            onPreview = showPreview,
                            focusId = focusId,
                        )
                        Route.Places -> PlacesScreen(
                            state = state,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                            onPlace = { push(Route.AlbumDetail(it, "Places")) },
                        )
                        Route.Duplicates -> DuplicatesScreen(
                            state = state,
                            actions = actions,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                        )
                        Route.AllAlbums -> AllAlbumsScreen(
                            state = state,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                            onAlbum = { push(Route.AlbumDetail(it, "My Albums")) },
                        )
                        null -> when (AppTab.entries[t]) {
                            AppTab.LIBRARY -> LibraryScreen(
                                state = state,
                                favorites = favorites,
                                ui = library,
                                gridState = libraryGrid,
                                selection = selection,
                                onOpen = { open(AlbumRef(AlbumRef.RECENTS, "Library"), it) },
                                onPreview = { showPreview(AlbumRef(AlbumRef.RECENTS, "Library"), it) },
                                focusId = focusId,
                            )
                            AppTab.FOR_YOU -> ForYouScreen(state = state, onPlay = { slideshow = it })
                            AppTab.ALBUMS -> AlbumsScreen(
                                state = state,
                                favorites = favorites,
                                access = access,
                                onManageAccess = requestAccess,
                                onAlbum = { push(Route.AlbumDetail(it, "Albums")) },
                                onSeeAll = { push(Route.AllAlbums) },
                                onNewAlbum = { actions.newAlbum() },
                                onHidden = openHidden,
                                onPlaces = { push(Route.Places) },
                                onDuplicates = { push(Route.Duplicates) },
                            )
                            AppTab.SEARCH -> SearchScreen(
                                state = state,
                                favorites = favorites,
                                onAlbum = { push(Route.AlbumDetail(it, "Search")) },
                                onOpen = open,
                                onPreview = showPreview,
                                textProgress = textProgress,
                            )
                        }
                    }
                }
            }
            if (selection.active) {
                SelectionToolbar(state, favorites, selection, actions, album = (stacks[tab].lastOrNull() as? Route.AlbumDetail)?.album)
            } else {
                TabBar(
                    tabs = AppTab.entries.map { TabItem(it.label, it.icon) },
                    selected = tab,
                    onSelect = { i ->
                        // Tapping the current tab again goes back to its first screen, as on iOS.
                        if (i == tab) stacks[i].clear()
                        tab = i
                    },
                )
            }
        }

        viewer?.let { target ->
            key(target) {
                Viewer(
                    target = target,
                    state = state,
                    favorites = favorites,
                    actions = actions,
                    onCurrentChange = { focusId = it },
                    onEdited = { id ->
                        vm.refresh()
                        editedCopy = id
                        note = if (id != null) "Saved as a copy" else "Couldn\u2019t save the edited photo"
                    },
                    onClose = {
                        viewer = null
                        focusId = null
                        actions.forgetRemoved()
                    },
                )
            }
        }

        // Open the copy once the library has it; if it's slow to appear, stay put (the note says it was saved).
        LaunchedEffect(editedCopy, state) {
            val id = editedCopy ?: return@LaunchedEffect
            if (state.items.any { it.id == id }) {
                editedCopy = null
                focusId = id
                viewer = ViewerTarget(AlbumRef(AlbumRef.RECENTS, "Library"), id)
            }
        }
        LaunchedEffect(editedCopy) {
            delay(5_000)
            editedCopy = null
        }
        note?.let { text ->
            LaunchedEffect(text) {
                delay(2_500)
                note = null
            }
            Text(
                text,
                style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 52.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        slideshow?.let { memory ->
            Slideshow(memory, onClose = { slideshow = null })
        }

        preview?.let { (album, item) ->
            PreviewOverlay(
                item = item,
                album = album,
                favorite = item.id in favorites,
                actions = actions,
                onOpen = {
                    preview = null
                    open(album, item)
                },
                onDismiss = { preview = null },
            )
        }

        actions.sheet?.let { request ->
            ActionSheet(message = request.message, actions = request.actions, onDismiss = { actions.sheet = null })
        }
        actions.picking?.let { items ->
            AlbumPicker(
                albums = state.userAlbums,
                onPick = { id -> actions.addToAlbum(id, items) },
                onNewAlbum = { actions.newAlbum { album -> actions.addToAlbum(album.id, items) } },
                onDismiss = { actions.picking = null },
            )
        }
        actions.naming?.let { request ->
            NameDialog(request, onDismiss = { actions.naming = null })
        }

        actions.pendingDelete?.let { items ->
            ActionSheet(
                message = if (items.size == 1) "This ${if (items[0].isVideo) "video" else "photo"} will be deleted from your phone."
                else "These ${items.size} items will be deleted from your phone.",
                actions = listOf(
                    SheetAction(
                        if (items.size == 1) "Delete ${if (items[0].isVideo) "Video" else "Photo"}" else "Delete ${items.size} Items",
                        destructive = true,
                    ) {
                        actions.confirmDelete(items)
                        selection.end()
                    },
                ),
                onDismiss = { actions.pendingDelete = null },
            )
        }
    }
}

/** Replaces the tab bar while selecting: Share · "3 Photos Selected" · Favorite · Delete. */
@Composable
private fun SelectionToolbar(state: LibraryState, favorites: Set<Long>, selection: Selection, actions: GalleryActions, album: AlbumRef?) {
    val apple = LocalApple.current
    val trash = album?.isTrash == true
    // Pick from the album on screen: Hidden's photos (for one) aren't in the Library's list.
    val picked = selection.items(album?.items(state, favorites) ?: state.items)
    Column(Modifier.fillMaxWidth().background(apple.bar)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(49.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (trash) {
                BarText("Delete", color = apple.red, enabled = picked.isNotEmpty()) { actions.deleteForever(picked); selection.end() }
                Text(Format.selected(picked), style = AppleType.headline.copy(fontSize = AppleType.subheadline.fontSize), color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                BarText("Recover", enabled = picked.isNotEmpty()) { actions.recover(picked); selection.end() }
            } else {
                BarIcon(AppIcons.Share, "Share", enabled = picked.isNotEmpty()) { actions.share(picked) }
                Text(Format.selected(picked), style = AppleType.headline.copy(fontSize = AppleType.subheadline.fontSize), color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                val allFavorite = picked.isNotEmpty() && picked.all { it.id in favorites }
                BarIcon(if (allFavorite) AppIcons.HeartFill else AppIcons.Heart, if (allFavorite) "Unfavorite" else "Favorite", enabled = picked.isNotEmpty()) {
                    actions.setFavorite(picked, !allFavorite)
                    selection.end()
                }
                BarIcon(AppIcons.Ellipsis, "More", enabled = picked.isNotEmpty()) {
                    actions.more(picked, album) { selection.end() }
                }
                BarIcon(AppIcons.Trash, "Delete", enabled = picked.isNotEmpty()) {
                    actions.delete(picked)
                    if (Build.VERSION.SDK_INT >= 30) selection.end()
                }
            }
        }
    }
}

/** The first-run screen, in the style of Apple's "What's New" sheets, asking for access to photos. */
@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(apple.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(19.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3FD9), Color(0xFF8E5BE8), Color(0xFFFF8A6B)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Library, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Welcome to Lumen", style = AppleType.largeTitle, color = apple.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Feature(AppIcons.Library, apple.tint, "Your Library", "Every photo and video on your phone, in one place. Nothing is uploaded or copied.")
            Feature(AppIcons.Albums, apple.orange, "Albums", "Camera, Screenshots, WhatsApp and every other folder, plus Favorites and Videos.")
            Feature(AppIcons.Search, apple.purple, "Search", "Find photos by month, year, album or file name.")
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (asked) "Lumen needs access to your photos and videos to show them. You can allow it in Settings."
            else "Lumen will ask to see the photos and videos on this phone.",
            style = AppleType.footnote,
            color = apple.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (asked) {
            FilledButton("Open Settings") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
            Spacer(Modifier.height(4.dp))
            BarText("Try Again") { onContinue() }
        } else {
            FilledButton("Continue") {
                asked = true
                onContinue()
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, text: String) {
    val apple = LocalApple.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = AppleType.headline, color = apple.label)
            Text(text, style = AppleType.subheadline, color = apple.secondaryLabel)
        }
    }
}
