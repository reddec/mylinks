package net.reddec.mylinks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.R
import net.reddec.mylinks.data.Bookmark
import net.reddec.mylinks.data.TagCount
import net.reddec.mylinks.data.fullDate
import net.reddec.mylinks.data.hostOf

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    bookmark: Bookmark,
    knownTags: List<TagCount>,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAddTag: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var addingTag by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    val tagFocus = remember { FocusRequester() }

    // Opening the inline field should also raise the keyboard, or the tap does nothing visible.
    LaunchedEffect(addingTag) {
        if (addingTag) {
            withFrameNanos { }
            runCatching { tagFocus.requestFocus() }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 4.dp),
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
            IconAction(painterResource(R.drawable.ic_mdi_light_delete), "Delete", onClick = { confirmDelete = true })
        }

        Column(Modifier.padding(horizontal = 22.dp)) {
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
                Text(
                    bookmark.excerpt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
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
                        .firstOrNull { tagInput.isNotBlank() && it.startsWith(tagInput, ignoreCase = true) && it !in bookmark.tags }
                    if (suggestion != null) {
                        TextAction(suggestion, color = scheme.onSurfaceVariant) {
                            onAddTag(suggestion)
                            tagInput = ""
                            addingTag = false
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            PrimaryButton("Open in browser", onClick = onOpen)

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Share", color = scheme.onSurfaceVariant, onClick = onShare)
                Spacer(Modifier.width(22.dp))
                TextAction("Delete", color = scheme.error) { confirmDelete = true }
            }
            Spacer(Modifier.height(30.dp))
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
