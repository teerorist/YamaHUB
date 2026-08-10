package com.yamahub.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * ControlScreen = tabela:
 *   [☰ DnD + etykieta] | [OUT xx]
 *
 * - DnD zmienia tylko przypisanie OUT (autozapis INCFG)
 * - Etykieta: short / long → ControlActions (osobne moduły funkcji)
 * Tu NIE ma logiki kierunków / świateł / startera.
 */
@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var states by remember { mutableStateOf(List(10) { false }) }
    var hazardOn by remember { mutableStateOf(false) }
    var cfg by remember { mutableStateOf<List<InputCfgItem>>(emptyList()) }
    var rows by remember {
        mutableStateOf((1..10).map { out ->
            ControlInRow(0, -1, "—", null, listOf(out), out)
        })
    }
    var leftOutNum by remember { mutableIntStateOf(1) }
    var rightOutNum by remember { mutableIntStateOf(5) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }

    val leftActive = states.getOrElse(leftOutNum - 1) { false } || hazardOn
    val rightActive = states.getOrElse(rightOutNum - 1) { false } || hazardOn
    val leftLevel = rememberBlinkLevel(leftActive, fadeSpeed, fadeCurve)
    val rightLevel = rememberBlinkLevel(rightActive, fadeSpeed, fadeCurve)

    fun outLevel(out: Int): Float =
        if (states.getOrElse(out - 1) { false }) 1f else 0f

    // DnD
    var dragFromOut by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { (56.dp + 6.dp).toPx() }

    fun applyCfg(list: List<InputCfgItem>) {
        cfg = list
        rows = buildRows(list)
        leftOutNum = list.firstOrNull { it.mode == 2 }?.outNum?.coerceIn(1, 10) ?: 1
        rightOutNum = list.firstOrNull { it.mode == 3 }?.outNum?.coerceIn(1, 10) ?: 5
    }

    fun autoSave(newCfg: List<InputCfgItem>) {
        if (!ble.isConnected || saving) return
        saving = true
        scope.launch {
            try {
                newCfg.forEach { item ->
                    ble.setInputCfg(item.inNum, item.mode, item.outNum, item.name)
                    delay(70)
                }
                delay(200)
                ble.requestInputCfg()
            } finally {
                delay(500)
                saving = false
            }
        }
    }

    fun endDrag() {
        if (dragFromOut in 1..10 && rowHeightPx > 0f) {
            val steps = round(dragOffsetY / rowHeightPx).toInt()
            val toOut = (dragFromOut + steps).coerceIn(1, 10)
            if (toOut != dragFromOut && cfg.isNotEmpty()) {
                val newCfg = swapOutAssignment(cfg, dragFromOut, toOut)
                if (newCfg != cfg) {
                    applyCfg(newCfg)
                    autoSave(newCfg)
                }
            }
        }
        dragFromOut = -1
        dragOffsetY = 0f
    }

    DisposableEffect(Unit) {
        ControlActions.reset()
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg
        val prevBlinkCfg = ble.onConfigReceived

        ble.onConnectionChanged = { c ->
            isConnected = c
            if (!c) ControlActions.reset()
            prevConn?.invoke(c)
        }
        ble.onStateReceived = { list ->
            if (list.size >= 10) {
                states = list
                hazardOn = list.getOrElse(leftOutNum - 1) { false } &&
                    list.getOrElse(rightOutNum - 1) { false } &&
                    leftOutNum != rightOutNum
            }
            prevState?.invoke(list)
        }
        ble.onInputCfg = { list ->
            Log.d("ControlScreen", "INCFG size=${list.size}")
            if (list.size in 9..10) {
                applyCfg(list)
                if (saving) saving = false
            }
            prevCfg?.invoke(list)
        }
        ble.onConfigReceived = { fade, blinks, curve, ac ->
            fadeSpeed = fade.coerceIn(4, 60)
            fadeCurve = curve.coerceIn(0, 2)
            prevBlinkCfg?.invoke(fade, blinks, curve, ac)
        }

        if (ble.isConnected) {
            // jeden start – bez dublowania z LaunchedEffect
            ble.requestInputCfg()
            ble.requestState()
            ble.sendCommand("GET_CFG")
        }
        onDispose {
            ControlActions.reset()
            ble.onConnectionChanged = prevConn
            ble.onStateReceived = prevState
            ble.onInputCfg = prevCfg
            ble.onConfigReceived = prevBlinkCfg
        }
    }

    // retry INCFG tylko gdy jeszcze puste (wolny start BLE)
    LaunchedEffect(isConnected) {
        if (!isConnected) return@LaunchedEffect
        delay(600)
        if (cfg.isEmpty()) {
            ble.requestInputCfg()
            ble.sendCommand("GET_CFG")
        }
    }

    // STATE – rzadziej, żeby nie dławić UI/BLE
    LaunchedEffect(isConnected) {
        while (isConnected) {
            ble.requestState()
            delay(500)
        }
    }


    val dragToOut = if (dragFromOut in 1..10 && rowHeightPx > 0f) {
        (dragFromOut + round(dragOffsetY / rowHeightPx).toInt()).coerceIn(1, 10)
    } else -1

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(rows, key = { _, r -> r.primaryOut }) { index, row ->
                val out = row.primaryOut
                val isDragging = dragFromOut == out
                val gapShiftY = when {
                    dragFromOut < 0 || isDragging -> 0f
                    dragFromOut < dragToOut &&
                        out in (dragFromOut + 1)..dragToOut -> -rowHeightPx
                    dragToOut < dragFromOut &&
                        out in dragToOut until dragFromOut -> rowHeightPx
                    else -> 0f
                }

                ControlInItem(
                    row = row,
                    levelForOut = { o ->
                        when {
                            row.mode == 2 -> leftLevel
                            row.mode == 3 -> rightLevel
                            o == leftOutNum && leftActive -> leftLevel
                            o == rightOutNum && rightActive -> rightLevel
                            else -> outLevel(o)
                        }
                    },
                    enabled = isConnected && row.mode >= 0 && !saving,
                    isDragging = isDragging,
                    gapShiftY = gapShiftY,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    onDown = {
                        ControlActions.onDown(
                            ble, row, leftActive, rightActive, hazardOn, scope
                        )
                    },
                    onUp = { held ->
                        ControlActions.onUp(
                            ble, row, held, leftActive, rightActive, hazardOn, ::outLevel
                        )
                    },
                    onDragStart = {
                        dragFromOut = out
                        dragOffsetY = 0f
                    },
                    onDrag = { dy -> dragOffsetY += dy },
                    onDragEnd = { endDrag() },
                    onDragCancel = {
                        dragFromOut = -1
                        dragOffsetY = 0f
                    }
                )
            }
        }
    }
}
