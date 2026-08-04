package com.yamahub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yamahub.app.Prefs

@Composable
fun BleSettingsTab(
    connected: Boolean,
    onChangeDevice: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val mac = prefs.lastDeviceAddress.orEmpty()
    val name = prefs.lastDeviceName.orEmpty().ifBlank { "YamaHub" }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("$name")
        Text("${mac.ifBlank { "—" }}")
        Text(
            if (connected) "połączono" else "rozłączono – łączenie…",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onChangeDevice,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zmień urządzenie")
        }
    }
}