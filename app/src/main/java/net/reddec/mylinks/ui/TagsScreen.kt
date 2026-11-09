package net.reddec.mylinks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.reddec.mylinks.R
import net.reddec.mylinks.data.TagCount

/**
 * The full tag list, because a horizontally scrolling strip stops working past a dozen tags.
 * Alphabetical with counts, searchable, and multi-select — the selection is the library filter.
 */
@Composable
fun TagsScreen(
    tags: List<TagCount>,
    selected: Set<String>,
    matching: Int,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    val visible = remember(tags, query) {
        val needle = query.trim()
        tags.filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(painterResource(R.drawable.ic_mdi_light_arrow_left), "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Kicker("Tags")
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                TextAction("Clear", color = scheme.primary, onClick = onClear)
            }
        }

        Spacer(Modifier.height(10.dp))
        SearchField(
            query = query,
            placeholder = "${tags.size} tags",
            onQuery = { query = it },
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Spacer(Modifier.height(14.dp))

        if (visible.isEmpty()) {
            EmptyState(
                title = "No tag by that name.",
                body = if (tags.isEmpty()) "Tags appear here once your library has some." else "Try a shorter word.",
                actionLabel = if (query.isNotEmpty()) "Clear search" else null,
                onAction = { query = "" },
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(visible, key = { it.name }) { tag ->
                    val isSelected = tag.name in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(tag.name) }
                            .padding(horizontal = 22.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) scheme.primary else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) scheme.primary else scheme.outlineVariant,
                                    RoundedCornerShape(2.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    painterResource(R.drawable.ic_mdi_light_check),
                                    "Selected",
                                    Modifier.size(11.dp),
                                    tint = scheme.onPrimary,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            tag.name,
                            style = Editorial.meta.copy(fontSize = 13.sp),
                            color = if (isSelected) scheme.primary else scheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Meta(tag.count.toString())
                    }
                    Hairline(Modifier.padding(start = 51.dp))
                }
            }

            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
            ) {
                Hairline()
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (selected.isEmpty()) "Show all $matching links" else "Show $matching links",
                    onClick = onDone,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
