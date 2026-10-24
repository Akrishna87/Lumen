package io.github.akrishna87.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.akrishna87.lumen.MediaItem
import kotlin.math.min

/**
 * Press and hold a photo: a larger preview over the blurred app, with a menu underneath, like
 * Photos' context menu. Tapping the preview opens the photo; tapping outside closes it.
 */
@Composable
fun PreviewOverlay(
    item: MediaItem,
    /** Where it was pressed: in Hidden, the menu offers Unhide. */
    album: AlbumRef,
    favorite: Boolean,
    actions: GalleryActions,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val apple = LocalApple.current
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f), label = "preview")

    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        // The dim layer also stands in for the blur on Android 11 and older, where blur does nothing.
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * pop)).tap(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = when {
            item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
            else -> 1f
        }
        val maxW = maxWidth - 48.dp
        val maxH = maxHeight * 0.5f
        val w = if (maxW / aspect <= maxH) maxW else maxH * aspect
        val h = w / aspect
        Column(
            Modifier.graphicsLayer {
                alpha = min(1f, pop * 1.4f)
                scaleX = 0.88f + 0.12f * pop
                scaleY = 0.88f + 0.12f * pop
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(w, h).shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    .background(apple.placeholder).clickable(onClick = onOpen)
                    .semantics { contentDescription = "Preview, " + item.description },
            ) {
                ThumbImage(item, Modifier.fillMaxSize())
                if (!item.isVideo) {
                    AsyncImage(
                        model = remember(item.id) { ImageRequest.Builder(context).data(item.uri).size(1280).build() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.isVideo) {
                    Icon(AppIcons.Play, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(44.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            val menu = if (apple.dark) Color(0xF0252527) else Color(0xF5F2F2F4)
            Column(
                Modifier.width(250.dp).shadow(14.dp, RoundedCornerShape(13.dp)).clip(RoundedCornerShape(13.dp)).background(menu)
                    .tap { }, // taps between rows shouldn't close the menu
            ) {
                MenuRow("Copy", AppIcons.Copy) { actions.copy(item); onDismiss() }
                MenuDivider()
                MenuRow("Share", AppIcons.Share) { actions.share(listOf(item)); onDismiss() }
                MenuDivider()
                MenuRow(if (favorite) "Unfavorite" else "Favorite", if (favorite) AppIcons.HeartFill else AppIcons.Heart) {
                    actions.toggleFavorite(item)
                    onDismiss()
                }
                MenuDivider()
                MenuRow("Add to Album", AppIcons.Albums) { actions.picking = listOf(item); onDismiss() }
                MenuDivider()
                if (album.isHidden) {
                    MenuRow("Unhide", AppIcons.EyeSlash) { actions.setHidden(listOf(item), false); onDismiss() }
                } else {
                    MenuRow("Hide", AppIcons.EyeSlash) { actions.setHidden(listOf(item), true); onDismiss() }
                }
                // A thicker gap sets the destructive action apart, as iOS menus do.
                Box(Modifier.fillMaxWidth().height(8.dp).background(apple.separator.copy(alpha = 0.15f)))
                MenuRow("Delete", AppIcons.Trash, color = apple.red) { actions.delete(listOf(item)); onDismiss() }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, color: Color? = null, onClick: () -> Unit) {
    val apple = LocalApple.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppleType.body, color = color ?: apple.label)
        Icon(icon, null, tint = color ?: apple.label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MenuDivider() = Hairline()
