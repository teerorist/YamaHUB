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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Tytuł jak w InputSettings (displayName / stałe nazwy). */
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

/**
 * Subtitle tylko gdy InputSettings też pokazuje:
 * 2× LIGHTS → LOW BEAM / HI BEAM
 * 2× BRAKE → front / rear
 * 1× LIGHTS z LIGHTS_H{n} → bez dopisku na karcie (dwa OUT na kwadratach)
 */
private fun subtitleFor(item: InputCfgItem, lights: List<InputCfgItem>, brakes: List<InputCfgItem>): String? {
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
    // 1× LIGHTS + zakodowane HI w nazwie LIGHTS_H{n}
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

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val prefs = remember { Prefs(context) }

    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var states by remember { mutableStateOf(List(10) { false }) }
    var hazardOn by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<ControlInRow>>(emptyList()) }
    var leftOutNum by remember { mutableStateOf(1) }
    var rightOutNum by remember { mutableStateOf(5) }

    val shortThresholdMs = prefs.shortPressThresholdMs

    fun outLit(out: Int): Boolean =
        states.getOrElse(out - 1) { false }

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
                // toggle – dla LIGHTS z 2 OUT: przełącz oba (albo tylko primary)
                val primary = row.outNums.first()
                val cur = outLit(primary)
                row.outNums.forEach { ble.setOutput(it, !cur) }
            }
            2 -> {
                // lewy kierunek
                if (hazardOn) {
                    ble.setHazard(false)
                } else if (outLit(leftOutNum)) {
                    ble.setOutput(leftOutNum, false)
                } else {
                    if (outLit(rightOutNum)) ble.setOutput(rightOutNum, false)
                    ble.setOutput(leftOutNum, true)
                }
            }
            3 -> {
                if (hazardOn) {
                    ble.setHazard(false)
                } else if (outLit(rightOutNum)) {
                    ble.setOutput(rightOutNum, false)
                } else {
                    if (outLit(leftOutNum)) ble.setOutput(leftOutNum, false)
                    ble.setOutput(rightOutNum, true)
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

        ble.onConnectionChanged = { c ->
            isConnected = c
            prevConn?.invoke(c)
        }
        ble.onStateReceived = { list ->
            if (list.size >= 10) {
                states = list
                hazardOn = list.getOrElse(0) { false } && list.getOrElse(4) { false }
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

        if (ble.isConnected) {
            ble.requestState()
            ble.requestInputCfg()
        }
        onDispose {
            ble.onConnectionChanged = prevConn
            ble.onStateReceived = prevState
            ble.onInputCfg = prevCfg
        }
    }

    LaunchedEffect(Unit) {
        delay(400)
        if (ble.isConnected) {
            ble.requestInputCfg()
            ble.requestState()
        }
    }

    // Okresowe odświeżanie STATE – żeby kwadraty łapały mruganie
    LaunchedEffect(isConnected) {
        while (isConnected) {
            ble.requestState()
            delay(200)
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
            active = hazardOn,
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
                    outLit = { out ->
                        when (row.mode) {
                            2 -> states.getOrElse(0) { false }  // STATE: left
                            3 -> states.getOrElse(4) { false }  // STATE: right
                            else -> outLit(out)
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
    active: Boolean,
    leftOut: Int,
    rightOut: Int,
    onToggle: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    // karta: tylko onPress (nie stan wyjścia)
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
        OutSquare(label = "OUT_%02d".format(leftOut), lit = active)
        Spacer(Modifier.width(6.dp))
        OutSquare(label = "OUT_%02d".format(rightOut), lit = active)
    }
}

@Composable
private fun ControlInItem(
    row: ControlInRow,
    outLit: (Int) -> Boolean,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: (heldMs: Long) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }

    // karta: tylko podświetlenie wciśnięcia
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
                lit = outLit(out)
            )
        }
    }
}

@Composable
private fun OutSquare(label: String, lit: Boolean) {
    val bg by animateColorAsState(
        if (lit) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        label = "sq$label"
    )
    val fg = if (lit)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface

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
