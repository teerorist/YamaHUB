package com.yamahub.app.ui

import com.yamahub.app.ui.InputSettingsTab
import com.yamahub.app.ui.BlinkerSettingsTab
import com.yamahub.app.ui.BleSettingsTab
import com.yamahub.app.ui.ControlScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yamahub.app.Prefs

@Composable
fun SettingsScreen(
    connected: Boolean = true,
    onChangeDevice: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var tab by remember {
        mutableIntStateOf(
            if (connected) prefs.lastSettingsTab.coerceIn(0, 3) else 0
        )
    }

    data class TabItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

    val tabs = listOf(
        TabItem(Icons.Default.Bluetooth, "BLE"),
        TabItem(Icons.Default.SyncAlt, "Kierunki"),
        TabItem(Icons.Default.Input, "Wejścia"),
        TabItem(Icons.Default.SettingsRemote, "Sterowanie")
    )

    LaunchedEffect(connected) {
        if (!connected) tab = 0
    }

    LaunchedEffect(tab, connected) {
        if (connected) prefs.lastSettingsTab = tab.coerceIn(0, 3)
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, item ->
                Tab(
                    selected = tab == index,
                    onClick = { if (index == 0 || connected) tab = index },
                    enabled = index == 0 || connected,
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label
                        )
                    }
                )
            }
        }

        when (tab) {
            0 -> BleSettingsTab(connected, onChangeDevice)
            1 -> if (connected) BlinkerSettingsTab()
            2 -> if (connected) InputSettingsTab()
            3 -> if (connected) ControlScreen()
        }
    }
}