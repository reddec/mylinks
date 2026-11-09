package net.reddec.mylinks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.UiState
import net.reddec.mylinks.data.exactServer

@Composable
fun LoginScreen(state: UiState, onSignIn: (server: String, username: String, password: String) -> Unit) {
    var server by remember(state.server) { mutableStateOf(state.server) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }

    // The address is used exactly as typed: no scheme guessing, so typos surface immediately.
    val serverProblem = when {
        server.isBlank() -> null
        exactServer(server) == null -> "Include http:// or https://"
        else -> null
    }
    val canSignIn = !state.signingIn && server.isNotBlank() && username.isNotBlank() &&
        password.isNotBlank() && serverProblem == null

    fun submit() {
        if (canSignIn) onSignIn(server, username, password)
    }

    val suggestions = state.knownServers
        .filter { it != server.trim() }
        .filter { server.isBlank() || it.contains(server.trim(), ignoreCase = true) }
        .take(3)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(64.dp))
        Text("MYLINKS", style = Editorial.brand, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        Text(
            "Your library,\nin your pocket.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "A quiet companion for your self-hosted Shiori. Reads offline, syncs when it can.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(34.dp))
        EditorialField(
            value = server,
            onValueChange = { server = it },
            label = "Shiori address",
            placeholder = "https://shiori.example.com",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
        )

        if (serverProblem != null) {
            Text(
                serverProblem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(14.dp))
        }

        if (suggestions.isNotEmpty()) {
            Kicker("Recent")
            Spacer(Modifier.height(6.dp))
            suggestions.forEach { suggestion ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            server = suggestion
                            password = ""
                        }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Meta(suggestion, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    TextAction("Use", color = MaterialTheme.colorScheme.primary) {
                        server = suggestion
                        password = ""
                    }
                }
                Hairline()
            }
            Spacer(Modifier.height(18.dp))
        }

        EditorialField(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            placeholder = "shiori",
            contentType = ContentType.Username,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        EditorialField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "••••••••",
            contentType = ContentType.Password,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.authError != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.authError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(14.dp))
        }

        PrimaryButton(
            text = if (state.signingIn) "Signing in" else "Sign in",
            enabled = canSignIn,
            onClick = { submit() },
        )

        Spacer(Modifier.height(16.dp))
        Row {
            Meta("Full URL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Meta(
                "http://box:8080 or https://shiori.example.com — exactly as you reach it in a browser",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
