package io.github.akrishna87.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.Memories
import io.github.akrishna87.lumen.Memory

/** For You: Memories, as cards that play a slideshow. */
@Composable
fun ForYouScreen(state: LibraryState, onPlay: (Memory) -> Unit) {
    val apple = LocalApple.current
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 60 } }
    val memories = remember(state.items) { Memories.build(state.items) }

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("For You", showTitle = scrolled, showBackground = scrolled)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "title") { LargeTitle("For You") }
            if (state.loaded && memories.isEmpty()) {
                item(key = "empty") {
                    EmptyState(AppIcons.ForYou, "No Memories Yet", "Memories of a month, or of this day in years past, appear here once you have enough photos.")
                }
            } else if (memories.isNotEmpty()) {
                item(key = "header") { SectionHeader("Memories") }
                items(memories, key = { it.key }) { memory -> MemoryCard(memory) { onPlay(memory) } }
            }
        }
    }
}

/** A big card: the memory's photo, with its title in white over a dark fade at the bottom. */
@Composable
private fun MemoryCard(memory: Memory, onClick: () -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1.05f)
            .shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(apple.placeholder)
            .clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "Memory, ${memory.title}" },
    ) {
        ThumbImage(memory.cover, Modifier.fillMaxSize())
        AsyncImage(
            model = remember(memory.cover.id) { ImageRequest.Builder(context).data(memory.cover.uri).size(1280).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(2f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(memory.title, style = AppleType.title1, color = Color.White)
            Text(memory.subtitle.uppercase(), style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f))
        }
    }
}
