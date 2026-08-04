package com.yamahub.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.HubNotification // <--- Dodany brakujący import
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()
    val logListState = rememberLazyListState()

    var speedKmh by remember { mutableFloatStateOf(0f) }
    var rpm by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    // Zarządzanie powiadomieniem systemowym w tle/na widoku
    LaunchedEffect(isConnected) {
        HubNotification.update(context, isConnected)
    }

    DisposableEffect(Unit) {
        // Pokazujemy powiadomienie od razu po wejściu do ekranu
        HubNotification.update(context, ble.isConnected)

        val prevConn = ble.onConnectionChanged
        val prevRaw = ble.onRawMessage
        ble.onConnectionChanged = { c ->
            isConnected = c
            HubNotification.update(context, c)
            prevConn?.invoke(c)
        }
        ble.onRawMessage = { msg ->
            when {
                msg.startsWith("LOG:") -> {
                    val line = msg.removePrefix("LOG:")
                    logs = (logs + line).takeLast(80)
                }
                msg.startsWith("RPM:") -> {
                    rpm = msg.removePrefix("RPM:").trim().toIntOrNull() ?: 0
                }
            }
            prevRaw?.invoke(msg)
        }
        onDispose {
            ble.onConnectionChanged = prevConn
            ble.onRawMessage = prevRaw
            // Jeśli użytkownik wychodzi z composable, ale aplikacja żyje w tle,
            // powiadomienie pozostaje zaktualizowane o aktualny stan BLE.
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        // Zawsze aktualizuj stan powiadomienia przy wejściu/odświeżeniu ekranu Dashboardu
        HubNotification.update(context, ble.isConnected)

        val prevConn = ble.onConnectionChanged
        val prevRaw = ble.onRawMessage
        ble.onConnectionChanged = { c ->
            isConnected = c
            HubNotification.update(context, c)
            prevConn?.invoke(c)
        }
        ble.onRawMessage = { msg ->
            when {
                msg.startsWith("LOG:") -> {
                    val line = msg.removePrefix("LOG:")
                    logs = (logs + line).takeLast(80)
                }
                msg.startsWith("RPM:") -> {
                    rpm = msg.removePrefix("RPM:").trim().toIntOrNull() ?: 0
                }
            }
            prevRaw?.invoke(msg)
        }
        onDispose {
            ble.onConnectionChanged = prevConn
            ble.onRawMessage = prevRaw
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Górny pasek: Wyłączenie (lewa) i Ustawienia (prawa) ---
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showShutdownDialog = true },
                enabled = isConnected
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Wyłącz HUB",
                    tint = Color(0xFFF44336)
                )
            }

            IconButton(
                onClick = onSettingsClick
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Ustawienia"
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Gauge(
            value = speedKmh,
            max = 200f,
            label = "${speedKmh.toInt()}",
            unit = "km/h",
            title = "",
            color = Color(0xFF4CAF50)
        )

        Spacer(Modifier.height(16.dp))

        Gauge(
            value = rpm.toFloat(),
            max = 10000f,
            label = if (rpm > 0) "$rpm" else "—",
            unit = "rpm",
            title = "",
            color = Color(0xFFFF9800)
        )

        Spacer(Modifier.weight(1f))

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(160.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            ble.sendCommand("IN10:1")
                            try {
                                awaitRelease()
                            } finally {
                                pressed = false
                                scope.launch {
                                    delay(40)
                                    ble.sendCommand("IN10:0")
                                    delay(40)
                                    ble.sendCommand("IN10:0")
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (pressed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxSize()
            ) {}
            Text(if (pressed) "ON" else "START", fontWeight = FontWeight.Bold)
        }
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text("Wyłączyć HUB?") },
            text = {
                Text("Aplikacja zostanie zamknięta.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShutdownDialog = false
                        if (isConnected) {
                            ble.sendCommand("SHUTDOWN_NOW")
                        }

                        // Natychmiastowo usuwamy powiadomienie przyciskiem z dashboardu
                        HubNotification.cancel(context)

                        Handler(Looper.getMainLooper()).postDelayed({
                            activity.finishAffinity()
                            Process.killProcess(Process.myPid())
                        }, 400)
                    }
                ) {
                    Text("Wyłącz", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
private fun Gauge(
    value: Float,
    max: Float,
    label: String,
    unit: String,
    title: String,
    color: Color
) {
    val sweep = (value / max).coerceIn(0f, 1f) * 270f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val pad = stroke / 2
                drawArc(
                    color = Color.DarkGray,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(unit, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}