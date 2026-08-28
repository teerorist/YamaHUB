package com.yamahub.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BleService : Service() {

    private val binder = LocalBinder()
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

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
                    or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(HubNotification.NOTIFICATION_ID, notification)
        }

        startSpeedUpdates()

        // Subskrypcja zmian połączenia dla powiadomienia
        val prev = ble.onConnectionChanged
        ble.onConnectionChanged = { connected ->
            HubNotification.update(this, connected)
            prev?.invoke(connected)
        }
    }

    private fun startSpeedUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (BleHub.manager(this@BleService).isConnected) {
                    BleHub.manager(this@BleService).sendSpeed(location.speed * 3.6f)
                }
            }
        }
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 100L, 0f, locationListener!!
            )
        } catch (_: SecurityException) {
            locationListener = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BleService", "onStartCommand")
        if (intent != null) {
            locationListener?.let { listener ->
                try { locationManager?.removeUpdates(listener) } catch (_: SecurityException) { }
            }
            locationListener = null
            startSpeedUpdates()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        locationListener?.let { listener ->
            try { locationManager?.removeUpdates(listener) } catch (_: SecurityException) { }
        }
        locationListener = null
        super.onDestroy()
        Log.d("BleService", "onDestroy")
        unregisterReceiver(notificationReceiver)
    }
}
