package net.reddec.mylinks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.reddec.mylinks.R
import net.reddec.mylinks.data.Bookmark
import net.reddec.mylinks.data.TagCount
import net.reddec.mylinks.data.fullDate
import net.reddec.mylinks.data.hostOf

/**
 * One page per bookmark: the metadata, the actions, and — right below them — Shiori's extracted
 * article text, so reading costs no extra tap and works with the radio off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    bookmark: Bookmark,
    knownTags: List<TagCount>,
    text: String,
    textLoading: Boolean,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAddTag: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var addingTag by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val tagFocus = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme

    val paragraphs = remember(text) {
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val words = remember(text) { if (text.isBlank()) 0 else text.split(Regex("\\s+")).size }

    LaunchedEffect(addingTag) {
        if (addingTag) {
            withFrameNanos { }
            runCatching { tagFocus.requestFocus() }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(painterResource(R.drawable.ic_mdi_light_arrow_left), "Back", onClick = onBack)
                    Spacer(Modifier.width(4.dp))
                    Kicker("Bookmark")
                    Spacer(Modifier.weight(1f))
                    if (bookmark.serverId == null) {
                        Meta("queued", color = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconAction(painterResource(R.drawable.ic_mdi_light_share_variant), "Share", onClick = onShare)
                    IconAction(
                        painterResource(R.drawable.ic_mdi_light_delete),
                        "Delete",
                        onClick = { confirmDelete = true },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    bookmark.title.ifBlank { bookmark.url },
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meta(hostOf(bookmark.url))
                    Meta("  ·  ")
                    Meta(fullDate(bookmark.createdAt))
                }
                Spacer(Modifier.height(6.dp))
                Meta(
                    bookmark.url,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                Hairline()

                if (bookmark.excerpt.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(bookmark.excerpt, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                }

                Spacer(Modifier.height(24.dp))
                Kicker("Tags")
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bookmark.tags.forEach { tag -> TagChip(tag, selected = true, onClick = {}) }
                    TagChip(if (addingTag) "adding…" else "+ tag", selected = false) {
                        addingTag = !addingTag
                        tagInput = ""
                    }
                }

                if (addingTag) {
                    Spacer(Modifier.height(14.dp))
                    EditorialField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = "New tag",
                        placeholder = "Type and press enter",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        focusRequester = tagFocus,
                    )
                    Row {
                        TextAction("Add tag") {
                            if (tagInput.isNotBlank()) {
                                onAddTag(tagInput)
                                tagInput = ""
                                addingTag = false
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        val suggestion = knownTags.map { it.name }
                            .firstOrNull {
                                tagInput.isNotBlank() &&
                                    it.startsWith(tagInput, ignoreCase = true) &&
                                    it !in bookmark.tags
                            }
                        if (suggestion != null) {
                            TextAction(suggestion, color = scheme.onSurfaceVariant) {
                                onAddTag(suggestion)
                                tagInput = ""
                                addingTag = false
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                TextAction("Open in browser", color = scheme.primary, onClick = onOpen)

                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(18.dp))
            }
        }

        if (textLoading && paragraphs.isEmpty()) {
            item(key = "loading") {
                Meta("Fetching the article text…", modifier = Modifier.padding(horizontal = 22.dp))
            }
        }

        items(paragraphs, key = { it.hashCode() }) { paragraph ->
            Text(
                paragraph,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                color = scheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 7.dp),
            )
        }

        if (paragraphs.isNotEmpty()) {
            item(key = "foot") {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Spacer(Modifier.height(18.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Meta("$words words from the Shiori copy · readable offline")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = scheme.background,
            titleContentColor = scheme.onBackground,
            textContentColor = scheme.onSurfaceVariant,
            title = { Text("Delete this link?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("It is removed from your library and from the server on the next sync.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = scheme.onSurfaceVariant) }
            },
        )
    }
}
