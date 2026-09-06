package com.yamahub.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var starterEnabled by remember { mutableStateOf(ble.starterEnabled) }
    var states by remember { mutableStateOf(List(10) { false }) }
    var cfg by remember { mutableStateOf<List<InputCfgItem>>(emptyList()) }
    var rows by remember { mutableStateOf(emptyList<ControlInRow>()) }
    
    var leftOutNum by remember { mutableIntStateOf(0) }
    var rightOutNum by remember { mutableIntStateOf(0) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }
    var isCfgReady by remember { mutableStateOf(false) }

    // Blinkers level calculation
    val leftOn = if (leftOutNum in 1..10) states.getOrElse(leftOutNum - 1) { false } else false
    val rightOn = if (rightOutNum in 1..10) states.getOrElse(rightOutNum - 1) { false } else false
    val hazardDisplay = leftOn && rightOn && leftOutNum != rightOutNum && leftOutNum > 0
    val blink = rememberBlinkPair(leftOn, rightOn, hazardDisplay, fadeSpeed, fadeCurve)
    val leftLevel = if (isCfgReady) blink.left else 0f
    val rightLevel = if (isCfgReady) blink.right else 0f

    fun outLevel(out: Int): Float = if (states.getOrElse(out - 1) { false }) 1f else 0f

    // DnD State
    var dragFromOut by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { (46.dp + 6.dp).toPx() }

    fun applyCfg(list: List<InputCfgItem>) {
        cfg = list
        rows = buildRows(list)
        leftOutNum = list.find { it.functionId == 1 }?.outNum ?: 0
        rightOutNum = list.find { it.functionId == 2 }?.outNum ?: 0
        isCfgReady = true
    }

    fun endDrag() {
        if (dragFromOut in 1..10 && rowHeightPx > 0f) {
            val steps = round(dragOffsetY / rowHeightPx).toInt()
            val toOut = (dragFromOut + steps).coerceIn(1, 10)
            if (toOut != dragFromOut && cfg.isNotEmpty()) {
                val newCfg = swapOutAssignment(cfg, dragFromOut, toOut)
                if (newCfg != cfg) {
                    applyCfg(newCfg)
                    // Auto-zapis do ESP
                    scope.launch {
                        newCfg.forEach { item ->
                            ble.setInputCfgV4(
                                item.inNum, item.mode, item.functionId, item.outNum,
                                item.outSecondary, item.outputEnabled, item.outputNum, item.name
                            )
                            delay(100)
                        }
                        ble.commitInputCfg()
                        ble.requestInputCfg()
                    }
                }
            }
        }
        dragFromOut = -1
        dragOffsetY = 0f
    }

    DisposableEffect(Unit) {
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg
        val prevBlinkCfg = ble.onConfigReceived
        val prevStarter = ble.onStarterEnabled

        ble.onConnectionChanged = { c -> isConnected = c; prevConn?.invoke(c) }
        ble.onStateReceived = { list -> if (list.size >= 10) states = list.take(10); prevState?.invoke(list) }
        ble.onInputCfg = { list -> if (list.size in 9..10 && !saving) applyCfg(list); prevCfg?.invoke(list) }
        ble.onConfigReceived = { fade, _, curve, _, _, _ ->
            fadeSpeed = fade.coerceIn(4, 60); fadeCurve = curve.coerceIn(0, 2)
            prevBlinkCfg?.invoke(fade, 3, curve, 20, true, true)
        }
        ble.onStarterEnabled = { enabled -> starterEnabled = enabled; prevStarter?.invoke(enabled) }

        if (ble.isConnected) {
            ble.requestInputCfg()
            ble.requestState()
            ble.sendCommand("GET_CFG")
        }
        onDispose {
            ble.onConnectionChanged = prevConn; ble.onStateReceived = prevState
            ble.onInputCfg = prevCfg; ble.onConfigReceived = prevBlinkCfg; ble.onStarterEnabled = prevStarter
        }
    }

    val dragToOut = if (dragFromOut in 1..10 && rowHeightPx > 0f) {
        (dragFromOut + round(dragOffsetY / rowHeightPx).toInt()).coerceIn(1, 10)
    } else -1

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(rows, key = { _, r -> r.primaryOut }) { _, row ->
                val out = row.primaryOut
                val isDragging = dragFromOut == out
                val gapShiftY = when {
                    dragFromOut < 0 || isDragging -> 0f
                    dragFromOut < dragToOut && out in (dragFromOut + 1)..dragToOut -> -rowHeightPx
                    dragToOut < dragFromOut && out in dragToOut until dragFromOut -> rowHeightPx
                    else -> 0f
                }

                ControlInItem(
                    row = row,
                    levelForOut = { o ->
                        when (row.functionId) {
                            1 -> leftLevel
                            2 -> rightLevel
                            else -> outLevel(o)
                        }
                    },
                    enabled = isConnected && isCfgReady && !saving && dragFromOut == -1 && row.functionId != 9,
                    isDragging = isDragging,
                    gapShiftY = gapShiftY,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    onOutTap = {
                        when (row.functionId) {
                            1 -> ble.sendCommand("LEFT:TOGGLE")
                            2 -> ble.sendCommand("RIGHT:TOGGLE")
                            else -> ble.setOutput(out, !states.getOrElse(out - 1) { false })
                        }
                    },
                    onDragStart = { dragFromOut = out; dragOffsetY = 0f },
                    onDrag = { dy -> dragOffsetY += dy },
                    onDragEnd = { endDrag() },
                    onDragCancel = { dragFromOut = -1; dragOffsetY = 0f }
                )
            }
        }
    }
}
