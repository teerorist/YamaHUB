package com.yamahub.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yamahub.app.ui.BleScannerScreen
import com.yamahub.app.ui.DashboardScreen
import com.yamahub.app.ui.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zapytanie o uprawnienia do powiadomień na Androidzie 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YamaHubRoot()
                }
            }
        }
    }

    // Wyłapuje zamknięcie aplikacji (np. swipe-away w ostatnich aplikacjach) i czyści powiadomienie
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            HubNotification.cancel(this)
        }
    }
}

private enum class AppScreen { Boot, Scan, Main }

@Composable
fun YamaHubRoot() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val ble = remember { BleHub.manager(context) }

    var screen by remember { mutableStateOf(AppScreen.Boot) }
    var showSettings by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(ble.isConnected) }
    var hadConnection by remember { mutableStateOf(false) }
    var settingsBeforeDrop by remember { mutableStateOf(false) }

    // Inicjalizacja kanału i zarządzanie powiadomieniem przy zmianach połączenia
    DisposableEffect(Unit) {
        HubNotification.ensureChannel(context)
        HubNotification.update(context, ble.isConnected)

        val prev = ble.onConnectionChanged
        ble.onConnectionChanged = { c ->
            connected = c
            if (c) hadConnection = true
            HubNotification.update(context, c)
            prev?.invoke(c)
        }
        onDispose {
            ble.onConnectionChanged = prev
        }
    }

    LaunchedEffect(connected) {
        HubNotification.update(context, connected)
    }

    LaunchedEffect(connected, screen) {
        if (screen != AppScreen.Main) return@LaunchedEffect
        if (!connected && hadConnection) {
            settingsBeforeDrop = showSettings
            delay(2500)
            if (!connected) {
                showSettings = true
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected && hadConnection && screen == AppScreen.Main) {
            showSettings = settingsBeforeDrop
        }
    }

    LaunchedEffect(Unit) {
        val mac = prefs.lastDeviceAddress
        if (mac.isNullOrBlank()) {
            screen = AppScreen.Scan
            return@LaunchedEffect
        }
        screen = AppScreen.Boot
        ble.connect(mac)
        val start = System.currentTimeMillis()
        var secondTry = false
        while (!connected && System.currentTimeMillis() - start < 12000) {
            delay(150)
            if (!secondTry && System.currentTimeMillis() - start > 5000) {
                secondTry = true
                ble.connect(mac)
            }
        }
        screen = if (connected) AppScreen.Main else AppScreen.Scan
    }

    LaunchedEffect(screen, connected) {
        if (screen != AppScreen.Main) return@LaunchedEffect
        while (true) {
            if (!connected) {
                val mac = prefs.lastDeviceAddress
                if (!mac.isNullOrBlank()) ble.connect(mac)
            }
            delay(3000)
        }
    }

    when (screen) {
        AppScreen.Boot -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Łączenie z YamaHub…")
                    Text(
                        prefs.lastDeviceAddress ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        ble.disconnect()
                        HubNotification.cancel(context)
                        screen = AppScreen.Scan
                    }) { Text("Anuluj") }
                }
            }
        }

        AppScreen.Scan -> {
            BackHandler {
                val mac = prefs.lastDeviceAddress
                if (!mac.isNullOrBlank()) {
                    ble.connect(mac)
                    showSettings = true
                    screen = AppScreen.Main
                }
            }
            BleScannerScreen(
                onDeviceSelected = { address: String, name: String ->
                    prefs.lastDeviceAddress = address
                    prefs.lastDeviceName = name
                    ble.connect(address)
                    showSettings = false
                    screen = AppScreen.Main
                }
            )
        }

        AppScreen.Main -> {
            BackHandler(enabled = showSettings) {
                showSettings = false
            }

            Scaffold { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            connected = connected,
                            onChangeDevice = {
                                ble.disconnect()
                                screen = AppScreen.Scan
                            }
                        )
                    } else if (connected) {
                        DashboardScreen(
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}