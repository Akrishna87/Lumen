package io.github.akrishna87.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Duplicates
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem

/** Utilities → Duplicates: each set of identical photos, with Merge. */
@Composable
fun DuplicatesScreen(state: LibraryState, actions: GalleryActions, onBack: () -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    var sets by remember { mutableStateOf<List<List<MediaItem>>?>(null) }
    LaunchedEffect(state.items) { sets = Duplicates.find(context, state.items) }

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("Duplicates", backLabel = "Albums", onBack = onBack)
        val found = sets
        when {
            found == null -> Text("Looking for duplicates…", style = AppleType.subheadline, color = apple.secondaryLabel, modifier = Modifier.padding(16.dp))
            found.isEmpty() -> {
                LargeTitle("Duplicates")
                EmptyState(AppIcons.Duplicates, "No Duplicates", "Identical copies of a photo appear here, so you can merge them into one.")
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "title") {
                    LargeTitle("Duplicates", subtitle = Format.plural(found.size, "Set"))
                    Text(
                        "Merging keeps one photo, with any heart and albums the others had, and moves the rest to Recently Deleted.",
                        style = AppleType.footnote,
                        color = apple.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                items(found, key = { set -> set.first().id }) { set -> DuplicateSet(set) { actions.merge(set) } }
            }
        }
    }
}

@Composable
private fun DuplicateSet(copies: List<MediaItem>, onMerge: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth()) {
        Hairline(start = 16.dp)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${copies.size} Copies", style = AppleType.headline, color = apple.label)
                Text(Format.size(copies.first().sizeBytes) + " each", style = AppleType.footnote, color = apple.secondaryLabel)
            }
            BarText("Merge", bold = true, onClick = onMerge)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            copies.take(4).forEach { photo ->
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(6.dp)).background(apple.placeholder)) {
                    ThumbImage(photo, Modifier.fillMaxSize())
                }
            }
        }
    }
}
