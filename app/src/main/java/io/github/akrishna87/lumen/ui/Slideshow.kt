package io.github.akrishna87.lumen.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.akrishna87.lumen.Memory
import kotlinx.coroutines.delay

private const val SLIDE_MS = 3500

/**
 * A memory's photos full screen, each slowly zooming (the "Ken Burns" look), cross-fading to the
 * next. Tap for the controls: Close, Pause/Play and Music (a song from your phone, remembered).
 */
@Composable
fun Slideshow(memory: Memory, onClose: () -> Unit) {
    val context = LocalContext.current
    val photos = memory.photos
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(true) }
    var music by remember { mutableStateOf(MusicChoice.get(context)) }

    BackHandler(onBack = onClose)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { paused = true }

    LaunchedEffect(Unit) {
        delay(SLIDE_MS.toLong())
        title = false
    }
    LaunchedEffect(index, paused) {
        if (paused || photos.size < 2) return@LaunchedEffect
        delay(SLIDE_MS.toLong())
        index = (index + 1) % photos.size
    }

    // The chosen song plays (looping) while the slideshow does.
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(music) {
        val song = music ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(song))
        player.prepare()
    }
    LaunchedEffect(paused, music) { player.playWhenReady = !paused && music != null }

    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            MusicChoice.set(context, uri)
            music = uri
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).tap { controls = !controls; if (controls) paused = true }
            .semantics { contentDescription = "Slideshow, ${memory.title}" },
    ) {
        Crossfade(targetState = index, animationSpec = tween(900), label = "slide") { i ->
            KenBurns(photos[i], moving = !paused, flip = i % 2 == 1)
        }

        AnimatedVisibility(title && !controls, enter = fadeIn(), exit = fadeOut(tween(800)), modifier = Modifier.align(Alignment.BottomStart)) {
            Column(Modifier.navigationBarsPadding().padding(24.dp)) {
                Text(memory.title, style = AppleType.largeTitle, color = Color.White)
                Text(memory.subtitle, style = AppleType.headline, color = Color.White.copy(alpha = 0.85f))
            }
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    BarText("Close", color = Color.White, onClick = onClose)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarIcon(if (paused) AppIcons.Play else AppIcons.Pause, if (paused) "Play" else "Pause", tint = Color.White) {
                        paused = !paused
                        if (!paused) controls = false
                    }
                    Box(Modifier.weight(1f))
                    BarText(if (music == null) "Add Music" else "Change Music", color = Color.White) { pickMusic.launch(arrayOf("audio/*")) }
                }
            }
        }
    }
}

/** One photo filling the screen, zooming in slowly and drifting sideways while [moving]. */
@Composable
private fun KenBurns(photo: io.github.akrishna87.lumen.MediaItem, moving: Boolean, flip: Boolean) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (moving) progress.animateTo(1f, tween(((1f - progress.value) * (SLIDE_MS + 1500)).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            scaleX = 1f + 0.12f * p
            scaleY = 1f + 0.12f * p
            translationX = (if (flip) -1f else 1f) * size.width * 0.03f * p
        },
    ) {
        ThumbImage(photo, Modifier.fillMaxSize())
        AsyncImage(
            model = remember(photo.id) { ImageRequest.Builder(context).data(photo.uri).size(2048).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The song chosen for slideshows, kept (with permission to read it) across restarts. */
private object MusicChoice {
    private const val KEY = "slideshow_music"

    fun get(context: Context): Uri? =
        context.getSharedPreferences("slideshow", Context.MODE_PRIVATE).getString(KEY, null)?.let(Uri::parse)

    fun set(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.getSharedPreferences("slideshow", Context.MODE_PRIVATE).edit().putString(KEY, uri.toString()).apply()
    }
}
