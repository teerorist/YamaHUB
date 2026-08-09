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
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import com.yamahub.app.Prefs

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

    var leftDownWasActive by remember { mutableStateOf(false) }
    var rightDownWasActive by remember { mutableStateOf(false) }

    fun pressDown(row: ControlInRow) {
        if (!isConnected) return
        when (row.mode) {
            1 -> row.outNums.forEach { ble.setOutput(it, true) }
            2 -> {
                leftDownWasActive = leftActive
                if (!hazardOn && !leftActive) {
                    if (rightActive) ble.sendCommand("RIGHT:0")
                    ble.sendCommand("LEFT:1") // od razu N
                }
            }
            3 -> {
                rightDownWasActive = rightActive
                if (!hazardOn && !rightActive) {
                    if (leftActive) ble.sendCommand("LEFT:0")
                    ble.sendCommand("RIGHT:1")
                }
            }
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
                when {
                    hazardOn -> ble.setHazard(false)
                    leftDownWasActive -> ble.sendCommand("LEFT:0")
                    heldMs >= 400L -> ble.sendCommand("LEFT:2") // promocja N → NS
                    // short: LEFT:1 już na pressDown
                    else -> {}
                }
            }
            3 -> {
                when {
                    hazardOn -> ble.setHazard(false)
                    rightDownWasActive -> ble.sendCommand("RIGHT:0")
                    heldMs >= 400L -> ble.sendCommand("RIGHT:2")
                    else -> {}
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

