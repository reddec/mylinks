package net.reddec.mylinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.ui.AddSheet
import net.reddec.mylinks.ui.DetailScreen
import net.reddec.mylinks.ui.LibraryScreen
import net.reddec.mylinks.ui.LoginScreen
import net.reddec.mylinks.ui.MyLinksTheme
import net.reddec.mylinks.ui.SettingsScreen
import net.reddec.mylinks.ui.TagsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MyLinksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation or dark-mode change recreates the activity with the same intent; the share
        // must only be consumed once or the save sheet would pop back up on every recreation.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MyLinksTheme {
                MyLinksApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        viewModel.onSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}

private sealed interface Screen {
    data object Library : Screen
    data class Detail(val rowId: Long) : Screen
    data object Tags : Screen
    data object Settings : Screen
}

@Composable
private fun MyLinksApp(viewModel: MyLinksViewModel) {
    val state = viewModel.state
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val opened = (screen as? Screen.Detail)?.let { detail ->
        state.bookmarks.firstOrNull { it.rowId == detail.rowId }
    }

    LaunchedEffect(screen, opened) {
        if (screen is Screen.Detail && opened == null) screen = Screen.Library
    }

    BackHandler(enabled = screen != Screen.Library) {
        if (screen is Screen.Detail) viewModel.closeDetail()
        screen = Screen.Library
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when {
                state.booting -> Unit
                !state.signedIn -> LoginScreen(state, viewModel::signIn)

                screen == Screen.Tags -> TagsScreen(
                    tags = state.tags,
                    selected = state.selectedTags,
                    matching = state.bookmarks.size,
                    onToggle = viewModel::toggleTag,
                    onClear = viewModel::clearTags,
                    onDone = { screen = Screen.Library },
                    onBack = { screen = Screen.Library },
                )

                screen == Screen.Settings -> SettingsScreen(
                    state = state,
                    onBack = { screen = Screen.Library },
                    onSyncNow = { viewModel.sync(manual = true) },
                    onBackgroundSync = viewModel::setBackgroundSync,
                    onOfflineText = viewModel::setOfflineText,
                    onSignOut = {
                        viewModel.signOut()
                        screen = Screen.Library
                    },
                )

                opened != null -> DetailScreen(
                    bookmark = opened,
                    knownTags = state.tags,
                    text = state.detailText,
                    textLoading = state.detailTextLoading,
                    onBack = {
                        viewModel.closeDetail()
                        screen = Screen.Library
                    },
                    onOpen = { openUrl(context, opened.url) },
                    onShare = { shareUrl(context, opened.title, opened.url) },
                    onAddTag = { viewModel.addTag(opened.rowId, it) },
                    onDelete = {
                        viewModel.delete(opened.rowId)
                        screen = Screen.Library
                    },
                )

                else -> LibraryScreen(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onToggleTag = viewModel::toggleTag,
                    onOpenTags = { screen = Screen.Tags },
                    onDateFilter = viewModel::setDateFilter,
                    onSync = { viewModel.sync(manual = true) },
                    onAdd = { viewModel.openAdd() },
                    onOpen = {
                        screen = Screen.Detail(it.rowId)
                        viewModel.openDetail(it.rowId)
                    },
                    onSettings = { screen = Screen.Settings },
                    onClearFilters = viewModel::clearFilters,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(2.dp),
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            )
        }
    }

    if (state.signedIn && state.addRequest != null) {
        AddSheet(
            prefill = state.addRequest.orEmpty(),
            tags = state.tags,
            onDismiss = viewModel::dismissAdd,
            onSave = { url, title, tags -> viewModel.saveLink(url, title, tags) },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing sensible to do.
    }
}

private fun shareUrl(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, url)
    try {
        context.startActivity(Intent.createChooser(intent, "Share link"))
    } catch (_: ActivityNotFoundException) {
        // ignore
    }
}
