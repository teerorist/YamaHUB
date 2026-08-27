package com.yamahub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Stałe powiadomienie YamaHub (status bar + szuflada).
 *
 * Stan BLE:
 *  - connected    → ic_ble_connected.png (biała)  + setColor biały
 *  - disconnected → ic_ble_disconnected.png (czerwona) + setColor czerwony
 *
 * PNG muszą leżeć w res/drawable/ pod tymi nazwami.
 * XML o tych samych nazwach usuń – inaczej zasłonią PNG.
 */
object HubNotification {
    private const val CHANNEL_ID = "yamahub_connection_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_CLOSE_APP = "com.yamahub.app.ACTION_CLOSE_APP"

    // connected = biały, disconnected = czerwony (zaakceptowana forma)
    private const val COLOR_CONNECTED = 0xFFFFFFFF.toInt()
    private const val COLOR_DISCONNECTED = 0xFFF44336.toInt()

    /** Kanał Android 8+; LOW = bez dźwięku przy każdym update stanu. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YamaHub Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status połączenia z modułem YamaHub"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Buduje Notification dla aktualnego isConnected.
     * setSmallIcon → PNG (biały albo czerwony w pliku),
     * setColor → ten sam kolor jako akcent w szufladzie.
     */
    fun build(context: Context, isConnected: Boolean): Notification {
        ensureChannel(context)

        // Klik w powiadomienie → MainActivity
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Swipe → BleService może przywrócić powiadomienie
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent("com.yamahub.app.ACTION_RESTORE_NOTIFICATION").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Przycisk Zamknij
        val closeIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(ACTION_CLOSE_APP).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnected) "YamaHub: Połączono" else "YamaHub: Rozłączono"
        val text = if (isConnected) {
            "Moduł aktywny i gotowy do drogi"
        } else {
            "Szukanie urządzenia..."
        }

        // PNG: connected = biała ikona, disconnected = czerwona (ten sam kształt)
        val iconRes = if (isConnected) {
            R.drawable.ic_ble_connected
        } else {
            R.drawable.ic_ble_disconnected
        }
        val color = if (isConnected) COLOR_CONNECTED else COLOR_DISCONNECTED

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .addAction(0, "ZAMKNIJ", closeIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Podmienia powiadomienie na aktualny stan BLE (connect / disconnect). */
    fun update(context: Context, isConnected: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, build(context, isConnected))
    }

    /** Usuwa powiadomienie (np. shutdown aplikacji). */
    fun cancel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
