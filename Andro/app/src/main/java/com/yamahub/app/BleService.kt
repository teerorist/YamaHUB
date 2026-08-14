package com.yamahub.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class BleService : Service() {

    private val binder = LocalBinder()

    private val restoreReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.yamahub.app.ACTION_RESTORE_NOTIFICATION") {
                Log.d("BleService", "Przywracanie powiadomienia po swipe...")
                val ble = BleHub.manager(this@BleService)
                HubNotification.update(this@BleService, ble.isConnected)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d("BleService", "onCreate")

        ContextCompat.registerReceiver(
            this,
            restoreReceiver,
            IntentFilter("com.yamahub.app.ACTION_RESTORE_NOTIFICATION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        val ble = BleHub.manager(this)
        
        // Uruchomienie jako Foreground Service
        val notification = HubNotification.build(this, ble.isConnected)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                HubNotification.NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(HubNotification.NOTIFICATION_ID, notification)
        }

        // Subskrypcja zmian połączenia dla powiadomienia
        val prev = ble.onConnectionChanged
        ble.onConnectionChanged = { connected ->
            HubNotification.update(this, connected)
            prev?.invoke(connected)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BleService", "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BleService", "onDestroy")
        unregisterReceiver(restoreReceiver)
    }
}
