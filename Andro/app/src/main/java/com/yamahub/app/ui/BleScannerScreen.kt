package com.yamahub.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class BleDeviceItem(val address: String, val name: String)

@SuppressLint("MissingPermission")
@Composable
fun BleScannerScreen(
    onDeviceSelected: (address: String, name: String) -> Unit
) {
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BleDeviceItem>>(emptyList()) }
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val scanner = remember { adapter?.bluetoothLeScanner }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
                val address = device.address ?: return
                if (devices.none { it.address == address }) {
                    devices = devices + BleDeviceItem(address, name)
                }
            }
        }
    }

    fun startScan() {
        devices = emptyList()
        scanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        scanning = false
        scanner?.stopScan(scanCallback)
    }

    DisposableEffect(Unit) {
        startScan()
        onDispose { stopScan() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Skanuj urządzenia BLE", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { if (scanning) stopScan() else startScan() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (scanning) "Zatrzymaj skanowanie" else "Skanuj")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { item ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        stopScan()
                        onDeviceSelected(item.address, item.name)
                    }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}