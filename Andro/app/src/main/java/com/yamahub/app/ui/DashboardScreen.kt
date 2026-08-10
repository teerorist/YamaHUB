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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.HubNotification
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val COL_NEUTRAL = Color(0xFF4CAF50)
private val COL_OIL = Color(0xFFF44336)
private val COL_HIBEAM = Color(0xFF2196F3)
private val COL_TURN = Color(0xFFFF9800)
private val COL_DIM = Color(0xFF3A3A3A)

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()

    var speedKmh by remember { mutableFloatStateOf(0f) }
    var rpm by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    var states by remember { mutableStateOf(List(10) { false }) }
    var leftOut by remember { mutableIntStateOf(1) }
    var rightOut by remember { mutableIntStateOf(5) }
    var neutralOut by remember { mutableIntStateOf(0) } // 1..10, 0 = brak
    var hiBeamOut by remember { mutableIntStateOf(0) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }

    // Test / symulacja

    val leftActive = states.getOrElse(leftOut - 1) { false }
    val rightActive = states.getOrElse(rightOut - 1) { false }
    // HAZARD: oba
    val hazard = leftActive && rightActive && leftOut != rightOut
    val leftBlink = leftActive || hazard
    val rightBlink = rightActive || hazard
    val leftLevel = rememberBlinkLevel(leftBlink, fadeSpeed, fadeCurve)
    val rightLevel = rememberBlinkLevel(rightBlink, fadeSpeed, fadeCurve)

    // Docelowo N / OIL = IN (czujniki). Na razie: DashboardTestState (trwały między ekranami).
    val neutralOn = DashboardTestState.neutral
    val oilOn = DashboardTestState.oil
    // HI BEAM: na razie stan wyjścia światła; docelowo też można spiąć z IN jeśli będzie czujnik.
    val hiBeamOn = hiBeamOut in 1..10 && states.getOrElse(hiBeamOut - 1) { false }

    val displaySpeed = if (DashboardTestState.useSimSpeed) DashboardTestState.simSpeed else speedKmh
    val displayRpm = if (DashboardTestState.useSimRpm) DashboardTestState.simRpm.toInt() else rpm

    fun applyCfg(list: List<InputCfgItem>) {
        leftOut = list.firstOrNull { it.mode == 2 }?.outNum?.coerceIn(1, 10) ?: 1
        rightOut = list.firstOrNull { it.mode == 3 }?.outNum?.coerceIn(1, 10) ?: 5
        val neutral = list.firstOrNull {
            it.mode == 1 && it.name.lowercase().contains("neutral")
        }
        neutralOut = neutral?.outNum?.coerceIn(1, 10) ?: 0
        // HI BEAM: LIGHTS_H{n} lub drugi LIGHTS
        val lights = list.filter {
            val n = it.name.lowercase()
            n.contains("lights") || n.contains("beam") || n.contains("light")
        }.sortedBy { it.inNum }
        hiBeamOut = when {
            lights.size >= 2 -> lights[1].outNum.coerceIn(1, 10)
            else -> {
                val hi = lights.firstOrNull()?.let { parseLightsHi(it.name) }
                hi ?: 0
            }
        }
    }

    LaunchedEffect(isConnected) {
        HubNotification.update(context, isConnected)
    }

    DisposableEffect(Unit) {
        HubNotification.update(context, ble.isConnected)
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg
        val prevBlink = ble.onConfigReceived
        val prevRaw = ble.onRawMessage

        ble.onConnectionChanged = { c ->
            isConnected = c
            HubNotification.update(context, c)
            prevConn?.invoke(c)
        }
        ble.onStateReceived = { list ->
            if (list.size >= 10) states = list
            prevState?.invoke(list)
        }
        ble.onInputCfg = { list ->
            if (list.size in 9..10) applyCfg(list)
            prevCfg?.invoke(list)
        }
        ble.onConfigReceived = { fade, _, curve, _ ->
            fadeSpeed = fade.coerceIn(4, 60)
            fadeCurve = curve.coerceIn(0, 2)
            prevBlink?.invoke(fade, 0, curve, 0)
        }
        ble.onRawMessage = { msg ->
            when {
                msg.startsWith("RPM:") -> {
                    if (!DashboardTestState.useSimRpm) {
                        rpm = msg.removePrefix("RPM:").trim().toIntOrNull() ?: 0
                    }
                }
            }
            prevRaw?.invoke(msg)
        }

        if (ble.isConnected) {
            ble.requestState()
            ble.requestInputCfg()
            ble.sendCommand("GET_CFG")
        }
        onDispose {
            ble.onConnectionChanged = prevConn
            ble.onStateReceived = prevState
            ble.onInputCfg = prevCfg
            ble.onConfigReceived = prevBlink
            ble.onRawMessage = prevRaw
        }
    }

    LaunchedEffect(isConnected) {
        while (isConnected) {
            ble.requestState()
            delay(250)
        }
    }

    // GPS → prędkość (gdy nie symulujemy)
    DisposableEffect(isConnected, DashboardTestState.useSimSpeed) {
        if (!isConnected || DashboardTestState.useSimSpeed) return@DisposableEffect onDispose {}
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                speedKmh = location.speed * 3.6f
                ble.sendSpeed(speedKmh)
            }
            @Deprecated("Deprecated")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        } catch (_: SecurityException) {}
        onDispose {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Góra: power | wskaźniki | settings ---
        Row(
            Modifier.fillMaxWidth(),
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

            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TurnArrow(left = true, level = leftLevel)
                Spacer(Modifier.width(10.dp))
                StatusDot(active = neutralOn, color = COL_NEUTRAL)
                Spacer(Modifier.width(8.dp))
                StatusDot(active = oilOn, color = COL_OIL)
                Spacer(Modifier.width(8.dp))
                StatusDot(active = hiBeamOn, color = COL_HIBEAM)
                Spacer(Modifier.width(10.dp))
                TurnArrow(left = false, level = rightLevel)
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
            }
        }

        Spacer(Modifier.height(8.dp))

        Gauge(
            value = displaySpeed,
            max = 200f,
            label = "${displaySpeed.toInt()}",
            unit = "km/h",
            color = Color(0xFF4CAF50)
        )

        Spacer(Modifier.height(8.dp))

        Gauge(
            value = displayRpm.toFloat(),
            max = 10000f,
            label = if (displayRpm > 0) "$displayRpm" else "—",
            unit = "rpm",
            color = Color(0xFFFF9800)
        )

        Spacer(Modifier.height(12.dp))

        // Starter
        Box(
            modifier = Modifier
                .size(120.dp)
                .pointerInput(isConnected) {
                    detectTapGestures(
                        onPress = {
                            if (!isConnected) return@detectTapGestures
                            if (!DashboardTestState.neutral) return@detectTapGestures
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
                color = when {
                    pressed -> MaterialTheme.colorScheme.primary
                    !DashboardTestState.neutral -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.fillMaxSize()
            ) {}
            Text(
                when {
                    pressed -> "ON"
                    !DashboardTestState.neutral -> "N?"
                    else -> "START"
                },
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))

        // --- Panel testowy (kompakt) ---
        Text(
            "TEST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tiny = MaterialTheme.typography.labelSmall
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NEUTRAL", style = tiny)
                    Switch(checked = DashboardTestState.neutral, onCheckedChange = { DashboardTestState.neutral = it })
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
                Row(
                    Modifier.fillMaxWidth().height(32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OLEJ", style = tiny)
                    Switch(checked = DashboardTestState.oil, onCheckedChange = { DashboardTestState.oil = it })
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
                Row(
                    Modifier.fillMaxWidth().height(28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sym. prędkość  ${DashboardTestState.simSpeed.toInt()} km/h", style = tiny)
                    Switch(checked = DashboardTestState.useSimSpeed, onCheckedChange = { DashboardTestState.useSimSpeed = it })
                }
                Slider(
                    value = DashboardTestState.simSpeed,
                    onValueChange = {
                        DashboardTestState.simSpeed = it
                        if (DashboardTestState.useSimSpeed) {
                            speedKmh = it
                            if (isConnected) ble.sendSpeed(it)
                        }
                    },
                    valueRange = 0f..200f,
                    enabled = DashboardTestState.useSimSpeed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
                Row(
                    Modifier.fillMaxWidth().height(28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sym. obroty  ${DashboardTestState.simRpm.toInt()} rpm", style = tiny)
                    Switch(checked = DashboardTestState.useSimRpm, onCheckedChange = { DashboardTestState.useSimRpm = it })
                }
                Slider(
                    value = DashboardTestState.simRpm,
                    onValueChange = { DashboardTestState.simRpm = it },
                    valueRange = 0f..10000f,
                    enabled = DashboardTestState.useSimRpm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }
        }
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text("Wyłączyć HUB?") },
            text = { Text("Aplikacja zostanie zamknięta.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShutdownDialog = false
                        if (isConnected) ble.sendCommand("SHUTDOWN_NOW")
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
private fun StatusDot(active: Boolean, color: Color) {
    Box(
        Modifier
            .size(16.dp)
            .background(
                if (active) color else color.copy(alpha = 0.22f),
                CircleShape
            )
            .border(
                1.dp,
                if (active) color else color.copy(alpha = 0.35f),
                CircleShape
            )
    )
}

@Composable
private fun TurnArrow(left: Boolean, level: Float) {
    val active = level > 0.05f
    val fill = COL_TURN.copy(alpha = level.coerceIn(0f, 1f))
    Canvas(Modifier.size(28.dp, 22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (left) {
                moveTo(w * 0.85f, h * 0.15f)
                lineTo(w * 0.25f, h * 0.5f)
                lineTo(w * 0.85f, h * 0.85f)
                close()
            } else {
                moveTo(w * 0.15f, h * 0.15f)
                lineTo(w * 0.75f, h * 0.5f)
                lineTo(w * 0.15f, h * 0.85f)
                close()
            }
        }
        if (active) {
            drawPath(path, color = fill, style = Fill)
        }
        drawPath(
            path,
            color = if (active) COL_TURN else COL_TURN.copy(alpha = 0.4f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun Gauge(
    value: Float,
    max: Float,
    label: String,
    unit: String,
    color: Color
) {
    val sweep = (value / max).coerceIn(0f, 1f) * 270f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
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
                Text(label, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(unit, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
