package net.reddec.mylinks.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.R
import net.reddec.mylinks.UiState
import net.reddec.mylinks.data.Bookmark
import net.reddec.mylinks.data.DateFilter
import net.reddec.mylinks.data.dayHeader
import net.reddec.mylinks.data.hostOf
import net.reddec.mylinks.data.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onOpenTags: () -> Unit,
    onDateFilter: (DateFilter?) -> Unit,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    onSettings: () -> Unit,
    onClearFilters: () -> Unit,
) {
    var showDateSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LibraryHeader(state, onSync, onSettings)
            Spacer(Modifier.height(10.dp))
            SearchField(
                query = state.query,
                placeholder = if (state.total > 0) "Search ${state.total} links" else "Search your library",
                onQuery = onQuery,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            if (state.selectedTags.isNotEmpty()) {
                SelectedTagStrip(state.selectedTags, onToggleTag)
            }
            FilterRow(
                state = state,
                onOpenTags = onOpenTags,
                onOpenDate = { showDateSheet = true },
                onClearFilters = onClearFilters,
            )
            Hairline()

            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = onSync,
                modifier = Modifier.weight(1f),
            ) {
                BookmarkList(state, onOpen, onClearFilters)
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
            shape = RoundedCornerShape(3.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            // Flat, print-like: no drop shadow on the paper.
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Icon(painterResource(R.drawable.ic_mdi_light_plus), "Save a link", Modifier.size(22.dp))
        }
    }

    if (showDateSheet) {
        DateSheet(
            current = state.dateFilter,
            onDismiss = { showDateSheet = false },
            onSelect = {
                showDateSheet = false
                onDateFilter(it)
            },
        )
    }
}

@Composable
private fun LibraryHeader(state: UiState, onSync: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MYLINKS", style = Editorial.brand, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        SyncStatus(state)
        IconAction(painterResource(R.drawable.ic_mdi_light_refresh), "Sync now", onClick = onSync)
        IconAction(painterResource(R.drawable.ic_mdi_light_cog), "Settings", onClick = onSettings)
    }
}

@Composable
private fun SyncStatus(state: UiState) {
    val scheme = MaterialTheme.colorScheme
    val text = when {
        state.syncing -> "syncing"
        state.downloadingText -> "${state.cachedArticles} articles"
        !state.online && state.pending > 0 -> "${state.pending} queued"
        !state.online -> "offline"
        state.pending > 0 -> "${state.pending} queued"
        state.lastSync > 0L -> when (val age = relativeTime(state.lastSync)) {
            "now" -> "synced"
            else -> "synced $age"
        }

        else -> "not synced"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // An accent dot only when something needs attention; a quiet library shows plain text.
        when {
            !state.online -> Icon(
                painterResource(R.drawable.ic_ph_cloud_slash_light),
                "Offline",
                Modifier.size(13.dp),
                tint = scheme.onSurfaceVariant,
            )

            state.syncing || state.pending > 0 || state.downloadingText -> Box(
                Modifier
                    .size(5.dp)
                    .background(scheme.primary, CircleShape)
            )

            else -> Unit
        }
        Spacer(Modifier.width(6.dp))
        Meta(text, color = scheme.onSurfaceVariant)
    }
}

/** Only the active filters live on the list; the full tag list is its own screen. */
@Composable
private fun SelectedTagStrip(selected: Set<String>, onToggle: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(selected.sorted(), key = { it }) { tag ->
            TagChip(tag, selected = true) { onToggle(tag) }
        }
    }
}

@Composable
private fun FilterRow(
    state: UiState,
    onOpenTags: () -> Unit,
    onOpenDate: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val filter = state.dateFilter
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(
                    1.dp,
                    if (state.selectedTags.isEmpty()) scheme.outlineVariant else scheme.primary,
                    RoundedCornerShape(2.dp),
                )
                .clickable(onClick = onOpenTags)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_mdi_light_tag),
                null,
                Modifier.size(13.dp),
                tint = if (state.selectedTags.isEmpty()) scheme.onSurfaceVariant else scheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Meta(
                if (state.selectedTags.isEmpty()) "Tags" else "Tags · ${state.selectedTags.size}",
                color = if (state.selectedTags.isEmpty()) scheme.onSurfaceVariant else scheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, if (filter == null) scheme.outlineVariant else scheme.primary, RoundedCornerShape(2.dp))
                .clickable(onClick = onOpenDate)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_mdi_light_calendar),
                null,
                Modifier.size(13.dp),
                tint = if (filter == null) scheme.onSurfaceVariant else scheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Meta(
                filter?.label ?: "Any date",
                color = if (filter == null) scheme.onSurfaceVariant else scheme.primary,
            )
        }
        if (filter != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.ic_ph_x_light),
                "Clear date filter",
                Modifier
                    .size(13.dp)
                    .clickable(onClick = onClearFilters),
                tint = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Meta(
            if (state.bookmarks.size == state.total) "${state.total} links"
            else "${state.bookmarks.size} of ${state.total}",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkList(state: UiState, onOpen: (Bookmark) -> Unit, onClearFilters: () -> Unit) {
    val filtered = state.query.isNotEmpty() || state.selectedTags.isNotEmpty() || state.dateFilter != null

    if (state.bookmarks.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                if (state.total == 0) {
                    EmptyState(
                        title = "A quiet shelf.",
                        body = "Share any link to MyLinks and it lands here — no connection needed.",
                    )
                } else {
                    EmptyState(
                        title = "Nothing matches.",
                        body = "Try another word, or loosen the tag and date filters.",
                        actionLabel = if (filtered) "Clear filters" else null,
                        onAction = onClearFilters,
                    )
                }
            }
        }
        return
    }

    val grouped = remember(state.bookmarks) { state.bookmarks.groupBy { dayHeader(it.createdAt) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        grouped.forEach { (day, dayBookmarks) ->
            stickyHeader(key = "header-$day") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Kicker(day)
                            Spacer(Modifier.weight(1f))
                            Meta(dayBookmarks.size.toString())
                        }
                        Hairline()
                    }
                }
            }
            items(dayBookmarks, key = { it.rowId }) { bookmark ->
                BookmarkRow(bookmark, onClick = { onOpen(bookmark) })
                Hairline()
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            text = bookmark.title.ifBlank { bookmark.url },
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Meta(hostOf(bookmark.url))
            Meta("  ·  ")
            Meta(relativeTime(bookmark.createdAt))
            if (bookmark.serverId == null) {
                Spacer(Modifier.width(8.dp))
                QueuedDot()
            }
        }
        if (bookmark.tags.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                bookmark.tags.joinToString("   "),
                style = Editorial.meta,
                color = scheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSheet(
    current: DateFilter?,
    onDismiss: () -> Unit,
    onSelect: (DateFilter?) -> Unit,
) {
    var showRange by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
        ) {
            Kicker("Date added")
            Spacer(Modifier.height(14.dp))

            datePresets().forEach { preset ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(if (preset.from == null) null else preset) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        preset.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (current != null && current.label == preset.label) {
                        Icon(
                            painterResource(R.drawable.ic_mdi_light_check),
                            "Selected",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Hairline()
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showRange = true }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_mdi_light_calendar),
                    null,
                    Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Custom range…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showRange) {
        RangePickerDialog(
            onDismiss = { showRange = false },
            onPick = {
                showRange = false
                onSelect(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(onDismiss: () -> Unit, onPick: (DateFilter) -> Unit) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = state.selectedStartDateMillis
                    val to = state.selectedEndDateMillis ?: from
                    if (from != null && to != null) onPick(rangeFilter(from, to))
                },
            ) {
                Text("Apply", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
}
