package com.yamahub.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import com.yamahub.app.Prefs
import com.yamahub.app.displayName
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier

/**
 * Wiersz sterowania.
 * outNums: 1 lub 2 numery OUT (np. LIGHTS single = LOW+HI).
 * subtitle: tylko jak w InputSettings (LOW BEAM / HI BEAM / front / rear).
 */
private data class ControlInRow(
    val inNum: Int,
    val mode: Int,
    val title: String,
    val subtitle: String?,
    val outNums: List<Int>
)

private fun isNeutral(item: InputCfgItem): Boolean {
    if (item.mode != 1) return false
    return item.name.lowercase().contains("neutral")
}

private fun isLightsName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("lights") || n.contains("light") ||
        n.contains("beam") || n.contains("hi_beam") || n.contains("low_beam")
}

private fun isBrakeName(name: String): Boolean =
    name.lowercase().contains("brake")

private fun titleFor(item: InputCfgItem): String {
    val n = displayName(item.name)
    return when (item.mode) {
        2 -> "KIERUNEK L"
        3 -> "KIERUNEK P"
        6 -> "STARTER"
        else -> when {
            isLightsName(item.name) -> "LIGHTS"
            isBrakeName(item.name) -> "BRAKE"
            n.isNotBlank() -> n
            else -> "IN_${item.inNum}"
        }
    }
}

private fun subtitleFor(
    item: InputCfgItem,
    lights: List<InputCfgItem>,
    brakes: List<InputCfgItem>
): String? {
    if (isLightsName(item.name) && lights.size >= 2) {
        val idx = lights.indexOfFirst { it.inNum == item.inNum }
        return if (idx <= 0) "LOW BEAM" else "HI BEAM"
    }
    if (isBrakeName(item.name) && brakes.size >= 2) {
        val idx = brakes.indexOfFirst { it.inNum == item.inNum }
        return if (idx <= 0) "front" else "rear"
    }
    return null
}

private fun outNumsFor(item: InputCfgItem, lightsCount: Int): List<Int> {
    val primary = item.outNum.coerceIn(1, 10)
    if (isLightsName(item.name) && lightsCount < 2) {
        val hi = Regex("""LIGHTS_H(\d+)""", RegexOption.IGNORE_CASE)
            .find(item.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.coerceIn(1, 10)
        if (hi != null && hi != primary) return listOf(primary, hi)
    }
    return listOf(primary)
}

private fun buildRows(cfg: List<InputCfgItem>): List<ControlInRow> {
    val usable = cfg.filter { item ->
        when {
            item.mode == 4 || item.mode == 5 -> false
            isNeutral(item) -> false
            else -> true
        }
    }
    val lights = usable.filter { isLightsName(it.name) && it.mode == 0 }
        .sortedBy { it.inNum }
    val brakes = usable.filter { isBrakeName(it.name) && it.mode == 1 }
        .sortedBy { it.inNum }
    val lc = lights.size

    return usable
        .sortedBy { it.outNum.coerceIn(1, 10) }
        .map { item ->
            ControlInRow(
                inNum = item.inNum,
                mode = item.mode,
                title = titleFor(item),
                subtitle = subtitleFor(item, lights, brakes),
                outNums = outNumsFor(item, lc)
            )
        }
}

/** Kolory OUT – te same co na ESP. */
private val COL_ORANGE = Color(0xFFFF9800)
private val COL_GREEN = Color(0xFF4CAF50)
private val COL_WHITE = Color(0xFFF5F5F5)
private val COL_BLUE = Color(0xFF2196F3)
private val COL_RED = Color(0xFFF44336)
private val COL_CYAN = Color(0xFF00BCD4)
private val COL_OFF = Color(0xFF2A2A2A)

private fun colorForRow(row: ControlInRow, outIndexInRow: Int): Color {
    return when (row.mode) {
        2, 3 -> COL_ORANGE
        6 -> COL_GREEN
        else -> {
            val sub = row.subtitle?.lowercase().orEmpty()
            val title = row.title.lowercase()
            when {
                sub.contains("hi") || title.contains("hi beam") -> COL_BLUE
                sub.contains("low") ||
                    (title == "lights" && row.outNums.size == 2 && outIndexInRow == 0) -> COL_WHITE
                title == "lights" && row.outNums.size == 2 && outIndexInRow == 1 -> COL_BLUE
                title == "lights" && row.outNums.size == 1 -> COL_WHITE
                title.contains("brake") || sub.contains("front") || sub.contains("rear") -> COL_RED
                else -> COL_CYAN
            }
        }
    }
}

/** Krzywa fade jak na ESP: 0 liniowa, 1 płynna, 2 ostra. */
private fun applyCurve(t: Float, curve: Int): Float {
    val x = t.coerceIn(0f, 1f)
    return when (curve) {
        2 -> if (x < 0.5f) 0f else 1f
        1 -> {
            // smoothstep
            x * x * (3f - 2f * x)
        }
        else -> x
    }
}

/**
 * Lokalna animacja trójkąta 0→1→0 jak mruganie kierunków.
 * stepMs ≈ cfg.fadeSpeed (ms między skokami jasności).
 */
@Composable
private fun rememberBlinkLevel(active: Boolean, fadeSpeed: Int, curve: Int): Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, fadeSpeed, curve) {
        if (!active) {
            // dokończ fade-out
            while (level > 0.01f) {
                level = (level - 0.04f).coerceAtLeast(0f)
                delay(16)
            }
            level = 0f
            return@LaunchedEffect
        }
        var phase = 0f // 0..2 (w górę + w dół)
        val stepMs = fadeSpeed.coerceIn(4, 60).toLong()
        // pełny cykl ~ (255/8)*fadeSpeed*2 ms na ESP → tu ciągły trójkąt
        while (true) {
            // phase 0..1 up, 1..2 down
            phase += 0.04f
            if (phase >= 2f) phase -= 2f
            val raw = if (phase <= 1f) phase else (2f - phase)
            level = applyCurve(raw, curve)
            delay(stepMs)
        }
    }
    return level
}

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val prefs = remember { Prefs(context) }

    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var states by remember { mutableStateOf(List(10) { false }) }
    var hazardOn by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<ControlInRow>>(emptyList()) }
    var leftOutNum by remember { mutableIntStateOf(1) }
    var rightOutNum by remember { mutableIntStateOf(5) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }

    val leftActive = states.getOrElse(leftOutNum - 1) { false } || hazardOn
    val rightActive = states.getOrElse(rightOutNum - 1) { false } || hazardOn
    val leftLevel = rememberBlinkLevel(leftActive, fadeSpeed, fadeCurve)
    val rightLevel = rememberBlinkLevel(rightActive, fadeSpeed, fadeCurve)

    fun outLevel(out: Int): Float {
        val on = states.getOrElse(out - 1) { false }
        return if (on) 1f else 0f
    }

    fun pressDown(row: ControlInRow) {
        if (!isConnected) return
        when (row.mode) {
            1 -> row.outNums.forEach { ble.setOutput(it, true) }
            6 -> ble.sendCommand("IN10:1")
            else -> {}
        }
    }

    fun pressUp(row: ControlInRow, heldMs: Long) {
        if (!isConnected) return
        when (row.mode) {
            1 -> row.outNums.forEach { ble.setOutput(it, false) }
            0 -> {
                val primary = row.outNums.first()
                val cur = outLevel(primary) > 0.5f
                row.outNums.forEach { ble.setOutput(it, !cur) }
            }
            2 -> {
                // lewy – toggle / przełączenie z prawego
                if (hazardOn) {
                    ble.setHazard(false)
                } else if (leftActive && !hazardOn) {
                    ble.setOutput(leftOutNum, false)
                    // też klasyczne OUT:1 na wypadek remap
                    if (leftOutNum != 1) ble.setOutput(1, false)
                } else {
                    if (rightActive) {
                        ble.setOutput(rightOutNum, false)
                        if (rightOutNum != 5) ble.setOutput(5, false)
                    }
                    ble.setOutput(leftOutNum, true)
                    if (leftOutNum != 1) ble.setOutput(1, true)
                }
            }
            3 -> {
                if (hazardOn) {
                    ble.setHazard(false)
                } else if (rightActive && !hazardOn) {
                    ble.setOutput(rightOutNum, false)
                    if (rightOutNum != 5) ble.setOutput(5, false)
                } else {
                    if (leftActive) {
                        ble.setOutput(leftOutNum, false)
                        if (leftOutNum != 1) ble.setOutput(1, false)
                    }
                    ble.setOutput(rightOutNum, true)
                    if (rightOutNum != 5) ble.setOutput(5, true)
                }
            }
            6 -> ble.sendCommand("IN10:0")
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg
        val prevBlinkCfg = ble.onConfigReceived

        ble.onConnectionChanged = { c ->
            isConnected = c
            prevConn?.invoke(c)
        }
        ble.onStateReceived = { list ->
            if (list.size >= 10) {
                states = list
                hazardOn = list.getOrElse(leftOutNum - 1) { false } && list.getOrElse(rightOutNum - 1) { false }
            }
            prevState?.invoke(list)
        }
        ble.onInputCfg = { list ->
            Log.d("ControlScreen", "INCFG size=${list.size}")
            if (list.size in 9..10) {
                rows = buildRows(list)
                leftOutNum = list.firstOrNull { it.mode == 2 }?.outNum?.coerceIn(1, 10) ?: 1
                rightOutNum = list.firstOrNull { it.mode == 3 }?.outNum?.coerceIn(1, 10) ?: 5
            }
            prevCfg?.invoke(list)
        }
        ble.onConfigReceived = { fade, blinks, curve, ac ->
            fadeSpeed = fade.coerceIn(4, 60)
            fadeCurve = curve.coerceIn(0, 2)
            prevBlinkCfg?.invoke(fade, blinks, curve, ac)
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
            ble.onConfigReceived = prevBlinkCfg
        }
    }

    LaunchedEffect(Unit) {
        delay(400)
        if (ble.isConnected) {
            ble.requestInputCfg()
            ble.requestState()
            ble.sendCommand("GET_CFG")
        }
    }

    LaunchedEffect(isConnected) {
        while (isConnected) {
            ble.requestState()
            delay(250)
        }
    }

    DisposableEffect(isConnected) {
        if (!isConnected) return@DisposableEffect onDispose {}
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return@DisposableEffect onDispose {}

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                ble.sendSpeed(location.speed * 3.6f)
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
            .padding(12.dp)
    ) {
        HazardRow(
            enabled = isConnected,
            leftLevel = leftLevel,
            rightLevel = rightLevel,
            leftOut = leftOutNum,
            rightOut = rightOutNum,
            onToggle = {
                if (!isConnected) return@HazardRow
                ble.setHazard(!hazardOn)
            }
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rows, key = { it.inNum }) { row ->
                ControlInItem(
                    row = row,
                    levelForOut = { out ->
                        when {
                            row.mode == 2 -> leftLevel
                            row.mode == 3 -> rightLevel
                            out == leftOutNum && leftActive -> leftLevel
                            out == rightOutNum && rightActive -> rightLevel
                            else -> outLevel(out)
                        }
                    },
                    enabled = isConnected,
                    onDown = { pressDown(row) },
                    onUp = { held -> pressUp(row, held) }
                )
            }
        }
    }
}

@Composable
private fun HazardRow(
    enabled: Boolean,
    leftLevel: Float,
    rightLevel: Float,
    leftOut: Int,
    rightOut: Int,
    onToggle: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val cardBg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "hazardCard"
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(56.dp)
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .pointerInput(enabled) {
                    detectTapGestures(
                        onPress = {
                            if (!enabled) return@detectTapGestures
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                            onToggle()
                        }
                    )
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "AWARYJNE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
        OutSquare(
            label = "OUT_%02d".format(leftOut),
            level = leftLevel,
            onColor = COL_ORANGE
        )
        Spacer(Modifier.width(6.dp))
        OutSquare(
            label = "OUT_%02d".format(rightOut),
            level = rightLevel,
            onColor = COL_ORANGE
        )
    }
}

@Composable
private fun ControlInItem(
    row: ControlInRow,
    levelForOut: (Int) -> Float,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: (heldMs: Long) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }

    val cardBg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "card${row.inNum}"
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .height(56.dp)
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .pointerInput(row.inNum, enabled) {
                    detectTapGestures(
                        onPress = {
                            if (!enabled) return@detectTapGestures
                            pressed = true
                            downAt = System.currentTimeMillis()
                            onDown()
                            tryAwaitRelease()
                            pressed = false
                            onUp(System.currentTimeMillis() - downAt)
                        }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (row.subtitle != null) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        row.outNums.forEachIndexed { i, out ->
            if (i > 0) Spacer(Modifier.width(6.dp))
            OutSquare(
                label = "OUT_%02d".format(out),
                level = levelForOut(out),
                onColor = colorForRow(row, i)
            )
        }
    }
}

@Composable
private fun OutSquare(label: String, level: Float, onColor: Color) {
    val t = level.coerceIn(0f, 1f)
    val bg = if (t <= 0.01f) {
        COL_OFF
    } else {
        // mieszanie OFF → kolor funkcji proporcjonalnie do level (fade)
        Color(
            red = COL_OFF.red + (onColor.red - COL_OFF.red) * t,
            green = COL_OFF.green + (onColor.green - COL_OFF.green) * t,
            blue = COL_OFF.blue + (onColor.blue - COL_OFF.blue) * t,
            alpha = 1f
        )
    }
    val fg = if (t > 0.35f) {
        if (onColor == COL_WHITE || onColor == COL_CYAN) Color(0xFF111111)
        else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    Box(
        Modifier
            .size(width = 56.dp, height = 56.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center
        )
    }
}
