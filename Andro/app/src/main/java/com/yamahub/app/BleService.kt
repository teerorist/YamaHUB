package com.yamahub.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BleService : Service() {

    private val binder = LocalBinder()

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.yamahub.app.ACTION_RESTORE_NOTIFICATION" -> {
                    Log.d("BleService", "Przywracanie powiadomienia po swipe...")
                    val ble = BleHub.manager(this@BleService)
                    HubNotification.update(this@BleService, ble.isConnected)
                }
                HubNotification.ACTION_CLOSE_APP -> {
                    Log.d("BleService", "Zamykanie aplikacji z powiadomienia...")
                    val ble = BleHub.manager(this@BleService)
                    if (ble.isConnected) {
                        ble.sendCommand("SHUTDOWN_NOW")
                    }
                    HubNotification.cancel(this@BleService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    // Zakończ proces po krótkim opóźnieniu, aby serwis zdążył się zamknąć
                    Handler(Looper.getMainLooper()).postDelayed({
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }, 300)
                }
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

        val filter = IntentFilter().apply {
            addAction("com.yamahub.app.ACTION_RESTORE_NOTIFICATION")
            addAction(HubNotification.ACTION_CLOSE_APP)
        }

        ContextCompat.registerReceiver(
            this,
            notificationReceiver,
            filter,
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
        unregisterReceiver(notificationReceiver)
    }
}
