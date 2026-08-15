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
 * Powiadomienie stałe w status barze / szufladzie.
 *
 * Forma zaakceptowana wcześniej:
 *  - connected    → ta sama sylwetka logo, kolor BIAŁY
 *  - disconnected → ta sama sylwetka logo, kolor CZERWONY
 *  - rozmiar i kształt ikon identyczne (oba drawable = białe logo jako maska alfa)
 *  - barwę nadaje wyłącznie setColor(), nie inny path w XML
 */
object HubNotification {
    private const val CHANNEL_ID = "yamahub_connection_channel"
    const val NOTIFICATION_ID = 1001

    // Biały / czerwony – dokładnie jak w zaakceptowanej wersji
    private const val COLOR_CONNECTED = 0xFFFFFFFF.toInt()
    private const val COLOR_DISCONNECTED = 0xFFF44336.toInt()

    /** Kanał powiadomień (Android 8+); LOW = bez dźwięku przy każdym update. */
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
     * Buduje Notification dla stanu BLE.
     * setSmallIcon = ta sama maska logo; setColor = biały albo czerwony.
     */
    fun build(context: Context, isConnected: Boolean): Notification {
        ensureChannel(context)

        // Klik → MainActivity
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

        val title = if (isConnected) "YamaHub: Połączono" else "YamaHub: Rozłączono"
        val text = if (isConnected) "Moduł aktywny i gotowy do drogi" else "Szukanie urządzenia..."

        // Ta sama sylwetka w obu stanach (białe wektory = maska alfa)
        val iconRes = if (isConnected) {
            R.drawable.ic_ble_connected
        } else {
            R.drawable.ic_ble_disconnected
        }

        // Jedyna różnica wizualna: kolor akcentu / tint ikony
        val color = if (isConnected) COLOR_CONNECTED else COLOR_DISCONNECTED

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Podmienia istniejące powiadomienie na aktualny stan połączenia. */
    fun update(context: Context, isConnected: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, build(context, isConnected))
    }

    /** Usuwa powiadomienie (np. przy shutdown aplikacji). */
    fun cancel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
