package net.reddec.mylinks.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.reddec.mylinks.UiState

@Composable
fun LoginScreen(state: UiState, onSignIn: (server: String, username: String, password: String) -> Unit) {
    var server by remember(state.server) { mutableStateOf(state.server) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(72.dp))
        Text("MYLINKS", style = Editorial.brand, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(26.dp))
        Text(
            "Your library,\nin your pocket.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "A quiet companion for your self-hosted Shiori. Reads offline, syncs when it can.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(38.dp))
        EditorialField(
            value = server,
            onValueChange = { server = it },
            label = "Shiori address",
            placeholder = "shiori.example.com or 192.168.1.5:8080",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        EditorialField(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            placeholder = "shiori",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        EditorialField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "••••••••",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
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
            enabled = !state.signingIn && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            onClick = { onSignIn(server, username, password) },
        )

        Spacer(Modifier.height(18.dp))
        Row {
            Meta("https://", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Meta(
                "or plain http on your LAN — both are tried",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
