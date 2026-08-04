package com.yamahub.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log

class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private var isScanning = false

    var onDeviceFound: ((name: String, address: String) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Nieznane urządzenie"
            val address = device.address
            Log.d("BleScanner", "Znaleziono: $name ($address)")
            onDeviceFound?.invoke(name, address)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "Skanowanie nieudane: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning || scanner == null) return
        isScanning = true
        scanner.startScan(scanCallback)
        Log.d("BleScanner", "Skanowanie rozpoczęte")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning || scanner == null) return
        isScanning = false
        scanner.stopScan(scanCallback)
        Log.d("BleScanner", "Skanowanie zatrzymane")
    }
}