package net.reddec.mylinks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.R
import net.reddec.mylinks.data.TagCount

/**
 * Save-a-link sheet, opened from the FAB, from a share intent, or from a shared URL.
 * Saving writes locally first; the sync engine forwards it whenever the network is back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    prefill: String,
    tags: List<TagCount>,
    onDismiss: () -> Unit,
    onSave: (url: String, title: String, tags: List<String>) -> Unit,
) {
    var url by remember(prefill) { mutableStateOf(prefill) }
    var title by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var tagInput by remember { mutableStateOf("") }

    fun commitTag() {
        val clean = tagInput.trim()
        if (clean.isNotEmpty()) selected = selected + clean
        tagInput = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // Squared-off corners to match the printed-page language.
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Save a link")
                Spacer(Modifier.weight(1f))
                IconAction(painterResource(R.drawable.ic_ph_x_light), "Close", size = 14.dp, onClick = onDismiss)
            }
            Spacer(Modifier.height(6.dp))

            EditorialField(
                value = url,
                onValueChange = { url = it },
                label = "URL",
                placeholder = "https://",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            EditorialField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                placeholder = "Fetched automatically",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            if (selected.isNotEmpty()) {
                Kicker("Tags")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selected.toList(), key = { it }) { tag ->
                        TagChip(tag, selected = true) { selected = selected - tag }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            EditorialField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = "Add a tag",
                placeholder = "design, reading, later",
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.None,
                ),
            )

            val suggestions = tags.map { it.name }.filter { it !in selected }.take(12)
            if (suggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(suggestions, key = { it }) { tag ->
                        TagChip(tag, selected = false) { selected = selected + tag }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            PrimaryButton("Save", enabled = url.isNotBlank()) {
                commitTag()
                onSave(url, title, selected.toList())
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_mdi_light_check),
                    null,
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Meta("Saved offline first — syncs when you're online")
            }
        }
    }
}
