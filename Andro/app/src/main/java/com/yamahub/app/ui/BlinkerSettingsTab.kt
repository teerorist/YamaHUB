package com.yamahub.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yamahub.app.BleHub
import kotlinx.coroutines.delay

@Composable
fun BlinkerSettingsTab() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }

    var fadeSpeed by remember { mutableFloatStateOf(12f) }
    var blinkCount by remember { mutableFloatStateOf(3f) }
    var curve by remember { mutableIntStateOf(0) }
    var acSpeed by remember { mutableFloatStateOf(20f) }
    var beamFade by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val prev = ble.onConfigReceived
        ble.onConfigReceived = { f, b, c, a ->
            fadeSpeed = f.toFloat()
            blinkCount = b.toFloat()
            curve = c
            acSpeed = a.toFloat()
            prev?.invoke(f, b, c, a)
        }
        onDispose { ble.onConfigReceived = prev }
    }

    // Wejście w zakładkę → LIVE hazard + odczyt CFG
    LaunchedEffect(Unit) {
        if (!ble.isConnected) return@LaunchedEffect
        ble.setHazard(true)
        delay(300)
        ble.requestConfig()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ble.isConnected) ble.setHazard(false)
        }
    }

    fun pushCfg() {
        ble.setConfig(fadeSpeed.toInt(), blinkCount.toInt(), curve, acSpeed.toInt())
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text("Szybkość fade: ${fadeSpeed.toInt()}")
        Slider(
            value = fadeSpeed,
            onValueChange = { fadeSpeed = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 4f..40f
        )

        Text("Liczba mrugnięć (N): ${blinkCount.toInt()}")
        Slider(
            value = blinkCount,
            onValueChange = { blinkCount = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 1f..10f,
            steps = 8
        )

        Text("Krzywa fade")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Liniowa", "Płynna", "Ostra").forEachIndexed { idx, label ->
                FilterChip(
                    selected = curve == idx,
                    onClick = {
                        curve = idx
                        pushCfg()
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Wyłącz kierunkowskaz po przekroczeniu: ${acSpeed.toInt()} km/h")
        Slider(
            value = acSpeed,
            onValueChange = { acSpeed = it },
            onValueChangeFinished = { pushCfg() },
            valueRange = 0f..120f
        )

    }
}