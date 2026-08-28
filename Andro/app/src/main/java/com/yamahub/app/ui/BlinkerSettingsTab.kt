package com.yamahub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yamahub.app.BleHub

@Composable
fun BlinkerSettingsTab() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }

    var fadeSpeed by remember { mutableFloatStateOf(12f) }
    var blinkCount by remember { mutableFloatStateOf(3f) }
    var curve by remember { mutableIntStateOf(-1) }
    var acSpeed by remember { mutableFloatStateOf(20f) }
    var autoCancel by remember { mutableStateOf(false) }
    var autoLights by remember { mutableStateOf(false) }
    var cfgReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val prev = ble.onConfigReceived
        ble.onConfigReceived = { f, b, c, a, acOn, lightsOn ->
            fadeSpeed = f.toFloat()
            blinkCount = b.toFloat()
            curve = c.coerceIn(0, 2)
            acSpeed = a.coerceIn(5, 30).toFloat()
            if (acOn != null) autoCancel = acOn
            if (lightsOn != null) autoLights = lightsOn
            cfgReady = true
            prev?.invoke(f, b, c, a, acOn, lightsOn)
        }
        onDispose { ble.onConfigReceived = prev }
    }

    // Wejście w zakładkę → odczyt CFG, potem LIVE hazard
    LaunchedEffect(Unit) {
        if (!ble.isConnected) return@LaunchedEffect
        ble.requestConfig()
        ble.setHazard(true)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ble.isConnected) ble.setHazard(false)
        }
    }

    fun pushCfg(
        cancel: Boolean = autoCancel,
        lights: Boolean = autoLights,
        curveVal: Int = curve
    ) {
        if (!cfgReady || curveVal !in 0..2) return
        ble.setConfig(
            fadeSpeed.toInt(),
            blinkCount.toInt(),
            curveVal,
            acSpeed.toInt().coerceIn(5, 30),
            cancel,
            lights
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text("Szybkość fade: ${if (cfgReady) fadeSpeed.toInt().toString() else "—"}")
        Slider(
            value = fadeSpeed,
            onValueChange = { fadeSpeed = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 4f..40f,
            enabled = cfgReady
        )

        Text("Liczba mrugnięć (N): ${if (cfgReady) blinkCount.toInt().toString() else "—"}")
        Slider(
            value = blinkCount,
            onValueChange = { blinkCount = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 2f..6f,
            steps = 3,
            enabled = cfgReady
        )

        Text("Krzywa fade")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Liniowa", "Płynna", "Ostra").forEachIndexed { idx, label ->
                FilterChip(
                    selected = cfgReady && curve == idx,
                    enabled = cfgReady,
                    onClick = {
                        curve = idx
                        pushCfg(curveVal = idx)
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = cfgReady) {
                    val v = !autoCancel
                    autoCancel = v
                    pushCfg(cancel = v)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = cfgReady && autoCancel,
                onCheckedChange = null,
                enabled = cfgReady
            )
            Text("Autowyłączenie kierunkowskazu")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = cfgReady) {
                    val v = !autoLights
                    autoLights = v
                    pushCfg(lights = v)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = cfgReady && autoLights,
                onCheckedChange = null,
                enabled = cfgReady
            )
            Text("Autowłączenie świateł")
        }
        Text("Próg prędkości: ${if (cfgReady) "${acSpeed.toInt()} km/h" else "—"}")
        Slider(
            value = acSpeed,
            onValueChange = { acSpeed = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 5f..30f,
            steps = 24,
            enabled = cfgReady
        )

    }
}