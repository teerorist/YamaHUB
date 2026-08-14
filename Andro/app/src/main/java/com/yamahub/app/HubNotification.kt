package com.yamahub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object HubNotification {
    private const val CHANNEL_ID = "yamahub_connection_channel"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "YamaHub Status"
            val descriptionText = "Status połączenia z modułem YamaHub"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(context: Context, isConnected: Boolean): Notification {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intencja przywracania po usunięciu (swipe)
        val deleteIntent = Intent("com.yamahub.app.ACTION_RESTORE_NOTIFICATION")
        deleteIntent.setPackage(context.packageName)
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, 1, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnected) "YamaHub: Połączono" else "YamaHub: Rozłączono"
        val text = if (isConnected) "Moduł aktywny i gotowy do drogi" else "Szukanie urządzenia..."

        val iconRes = if (isConnected) {
            R.drawable.ic_ble_connected
        } else {
            R.drawable.ic_ble_disconnected
        }

        val color = if (isConnected) 0xFFFFFFFF.toInt() else 0xFFF44336.toInt()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun update(context: Context, isConnected: Boolean) {
        val notification = build(context, isConnected)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}