package net.reddec.mylinks.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.BuildConfig
import net.reddec.mylinks.R
import net.reddec.mylinks.UiState
import net.reddec.mylinks.data.fullDate
import net.reddec.mylinks.data.relativeTime

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onBackgroundSync: (Boolean) -> Unit,
    onOfflineText: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
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
            Kicker("Settings")
        }

        Column(Modifier.padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(18.dp))

            SettingRow("Server", state.server.ifBlank { "not configured" })
            SettingRow("Signed in as", state.username.ifBlank { "—" })
            SettingRow(
                "Last sync",
                when {
                    state.lastSync <= 0L -> "never"
                    else -> "${relativeTime(state.lastSync)} ago · ${fullDate(state.lastSync)}"
                },
            )
            SettingRow("Queued changes", state.pending.toString())
            SettingRow("Mirrored links", state.total.toString())
            SettingRow("Articles offline", "${state.cachedArticles} of ${state.total}")

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Kicker("Article text offline")
                    Spacer(Modifier.height(4.dp))
                    Meta("Downloads a few articles per sync for offline reading")
                }
                Switch(
                    checked = state.offlineText,
                    onCheckedChange = onOfflineText,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary,
                        uncheckedThumbColor = scheme.onSurfaceVariant,
                        uncheckedTrackColor = scheme.surfaceVariant,
                    ),
                )
            }
            Hairline()

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Kicker("Background sync")
                    Spacer(Modifier.height(4.dp))
                    Meta("Every 6 hours, when a network is available")
                }
                Switch(
                    checked = state.backgroundSync,
                    onCheckedChange = onBackgroundSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary,
                        uncheckedThumbColor = scheme.onSurfaceVariant,
                        uncheckedTrackColor = scheme.surfaceVariant,
                    ),
                )
            }
            Hairline()

            Spacer(Modifier.height(24.dp))
            PrimaryButton("Sync now", onClick = onSyncNow)

            Spacer(Modifier.height(20.dp))
            TextAction("Sign out", color = scheme.onSurfaceVariant) { confirmSignOut = true }

            Spacer(Modifier.height(30.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Meta("MyLinks ${BuildConfig.VERSION_NAME} · companion for Shiori")
            Spacer(Modifier.height(6.dp))
            Meta("No Google services. No analytics. Your links stay on your server.")
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = scheme.background,
            titleContentColor = scheme.onBackground,
            textContentColor = scheme.onSurfaceVariant,
            title = { Text("Sign out?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Your mirrored links stay on this device and in the queue until you sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Stay", color = scheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Kicker(label)
        Spacer(Modifier.height(5.dp))
        Meta(value, color = MaterialTheme.colorScheme.onBackground)
    }
    Hairline()
}
