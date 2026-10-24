package io.github.akrishna87.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.UserAlbumItems

/** Photos' "Add to Album": New Album… then the person's albums, each with its cover and count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPicker(albums: List<UserAlbumItems>, onPick: (String) -> Unit, onNewAlbum: () -> Unit, onDismiss: () -> Unit) {
    val apple = LocalApple.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = apple.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = { Box(Modifier.padding(top = 6.dp, bottom = 4.dp).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(apple.tertiaryLabel)) },
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("Add to Album", style = AppleType.headline, color = apple.label, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterStart)) { BarText("Cancel", onClick = onDismiss) }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item(key = "new") {
                GroupedSection {
                    PickerRow(title = "New Album…", subtitle = null, onClick = { onDismiss(); onNewAlbum() }) {
                        Box(Modifier.fillMaxSize().background(apple.fill), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Plus, null, tint = apple.tint, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item(key = "header") {
                    Text(
                        "MY ALBUMS",
                        style = AppleType.footnote,
                        color = apple.secondaryLabel,
                        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 7.dp),
                    )
                }
                items(albums, key = { it.album.id }) { entry ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(apple.cell)) {
                            PickerRow(
                                title = entry.album.name,
                                subtitle = Format.counts(entry.items.count { !it.isVideo }, entry.items.count { it.isVideo }),
                                onClick = { onPick(entry.album.id); onDismiss() },
                            ) {
                                val cover = entry.items.lastOrNull()
                                if (cover != null) ThumbImage(cover, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(apple.fill))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String?, onClick: () -> Unit, cover: @Composable () -> Unit) {
    val apple = LocalApple.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(apple.placeholder)) { cover() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppleType.body, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = AppleType.footnote, color = apple.secondaryLabel)
        }
    }
}

/** UIAlertController with a text field: "New Album", "Enter a name for this album.", Cancel / Save. */
@Composable
fun NameDialog(request: NameRequest, onDismiss: () -> Unit) {
    val apple = LocalApple.current
    var name by remember { mutableStateOf(TextFieldValue(request.initial, TextRange(0, request.initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canSave = name.text.isNotBlank()
    val save = {
        if (canSave) {
            onDismiss()
            request.onSave(name.text)
        }
    }
    val card = if (apple.dark) Color(0xFF2C2C2E) else Color(0xF5F2F2F4)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(270.dp).clip(RoundedCornerShape(14.dp)).background(card)) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(request.title, style = AppleType.headline, color = apple.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(request.message, style = AppleType.footnote, color = apple.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = AppleType.subheadline.copy(color = apple.label),
                    cursorBrush = SolidColor(apple.tint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Album name" },
                    decorationBox = { field ->
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (apple.dark) Color(0xFF1C1C1E) else Color.White)
                                .border(0.5.dp, apple.separator, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
                        ) {
                            if (name.text.isEmpty()) Text("Title", style = AppleType.subheadline, color = apple.tertiaryLabel)
                            field()
                        }
                    },
                )
            }
            Hairline()
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                Box(Modifier.weight(1f).fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Cancel", style = AppleType.body, color = apple.tint)
                }
                Box(Modifier.width(0.5.dp).fillMaxSize().background(apple.separator))
                Box(Modifier.weight(1f).fillMaxSize().clickable(enabled = canSave, onClick = save), contentAlignment = Alignment.Center) {
                    Text("Save", style = AppleType.body.copy(fontWeight = FontWeight.SemiBold), color = apple.tint.copy(alpha = if (canSave) 1f else 0.3f))
                }
            }
        }
    }
}
