package net.reddec.mylinks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.reddec.mylinks.R
import net.reddec.mylinks.data.Bookmark
import net.reddec.mylinks.data.fullDate
import net.reddec.mylinks.data.hostOf

/**
 * Reads Shiori's extracted article text, straight from the local mirror — this is the screen that
 * works with the radio off.
 */
@Composable
fun ReaderScreen(
    bookmark: Bookmark,
    text: String,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val paragraphs = remember(text) {
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val words = remember(text) { if (text.isBlank()) 0 else text.split(Regex("\\s+")).size }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(painterResource(R.drawable.ic_mdi_light_arrow_left), "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Kicker("Reading")
            Spacer(Modifier.weight(1f))
            IconAction(painterResource(R.drawable.ic_mdi_light_refresh), "Refresh text", onClick = onRefresh)
        }

        when {
            loading && paragraphs.isEmpty() -> {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Spacer(Modifier.height(20.dp))
                    Meta("Fetching the article text…")
                }
            }

            paragraphs.isEmpty() -> {
                EmptyState(
                    title = "No text, only the link.",
                    body = "Shiori has not stored a readable copy of this page. Open it in a browser, " +
                        "or refresh once it is reachable.",
                    actionLabel = "Try again",
                    onAction = onRefresh,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "head") {
                        Column {
                            Text(
                                bookmark.title.ifBlank { bookmark.url },
                                style = MaterialTheme.typography.headlineMedium,
                                color = scheme.onBackground,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Meta(hostOf(bookmark.url))
                                Meta("  ·  ")
                                Meta(fullDate(bookmark.createdAt))
                            }
                            Spacer(Modifier.height(16.dp))
                            Hairline()
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    items(paragraphs, key = { it.hashCode() }) { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                            color = scheme.onSurface,
                        )
                    }
                    item(key = "foot") {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Hairline()
                            Spacer(Modifier.height(10.dp))
                            Meta("$words words · saved for offline reading")
                        }
                    }
                }
            }
        }
    }
}
