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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import com.yamahub.app.Prefs
import com.yamahub.app.displayName
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState

/** id: "hazard" | "left" | "right" | "out_1".."out_9" */
private data class ControlRow(val id: String, val title: String)

private fun buildRowsFromCfg(cfg: List<InputCfgItem>): List<ControlRow> {
    var leftName: String? = null
    var rightName: String? = null
    val outNames = linkedMapOf<Int, String>()

    for (item in cfg) {
        when (item.mode) {
            2 -> if (leftName == null) {
                leftName = displayName(item.name).ifBlank { "Kierunek L" }
            }
            3 -> if (rightName == null) {
                rightName = displayName(item.name).ifBlank { "Kierunek P" }
            }
            0, 1 -> {
                val o = item.outNum
                if (o in 1..9 && o !in outNames) {
                    outNames[o] = displayName(item.name).ifBlank { "OUT_$o" }
                }
            }
        }
    }

    val rows = mutableListOf(ControlRow("hazard", "Awaryjne"))
    if (leftName != null) rows.add(ControlRow("left", leftName))
    if (rightName != null) rows.add(ControlRow("right", rightName))

    for ((out, name) in outNames) {
        if (leftName != null && out == 1) continue
        if (rightName != null && out == 5) continue
        rows.add(ControlRow("out_$out", name))
    }
    return rows
}

private fun applySavedOrder(rows: List<ControlRow>, orderIds: List<String>): List<ControlRow> {
    val byId = rows.associateBy { it.id }
    val ordered = orderIds.mapNotNull { byId[it] }
    val missing = rows.filter { r -> ordered.none { it.id == r.id } }
    return ordered + missing
}

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val prefs = remember { Prefs(context) }
    val listState = rememberLazyListState()

    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var states by remember { mutableStateOf(List(10) { false }) }
    var hazardOn by remember { mutableStateOf(false) }

    // Próg short/long z ustawień (domyślnie 400 ms)
    val shortThresholdMs = prefs.shortPressThresholdMs

    var baseRows by remember {
        mutableStateOf(
            listOf(
                ControlRow("hazard", "Awaryjne"),
                ControlRow("left", "Kierunek L"),
                ControlRow("right", "Kierunek P")
            )
        )
    }

    var orderIds by remember {
        val raw = prefs.controlOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val migrated = raw.filter {
            it == "hazard" || it == "left" || it == "right" || it.startsWith("out_")
        }
        mutableStateOf(migrated.ifEmpty { listOf("hazard", "left", "right") })
    }

    val rows = remember(baseRows, orderIds) {
        applySavedOrder(baseRows, orderIds)
    }

    fun persistOrder(newRows: List<ControlRow>) {
        orderIds = newRows.map { it.id }
        prefs.controlOrder = orderIds.joinToString(",")
    }

    // Wykonanie akcji (na razie short i long robią to samo – toggle.
    // Później w InputSettingsTab będzie można przypisać różne akcje)
    fun performAction(rowId: String, isLong: Boolean) {
        if (!isConnected) return

        when (rowId) {
            "hazard" -> {
                val newVal = !hazardOn
                hazardOn = newVal
                ble.setHazard(newVal)
            }
            "left" -> {
                val newVal = !states.getOrElse(0) { false }
                states = states.toMutableList().also { it[0] = newVal }
                ble.setOutput(1, newVal)
            }
            "right" -> {
                val newVal = !states.getOrElse(4) { false }
                states = states.toMutableList().also { it[4] = newVal }
                ble.setOutput(5, newVal)
            }
            else -> {
                val num = rowId.removePrefix("out_").toIntOrNull() ?: return
                if (num in 1..9) {
                    val newVal = !states.getOrElse(num - 1) { false }
                    states = states.toMutableList().also { it[num - 1] = newVal }
                    ble.setOutput(num, newVal)
                }
            }
        }
        // isLong na razie nieużywane – miejsce na przyszłe różne akcje
    }

    DisposableEffect(Unit) {
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg

        ble.onConnectionChanged = { c: Boolean ->
            isConnected = c
            prevConn?.invoke(c)
        }
        ble.onStateReceived = { list: List<Boolean> ->
            if (list.size >= 10) {
                states = list
                hazardOn = list[0] && list[4]
            }
            prevState?.invoke(list)
        }
        ble.onInputCfg = { list ->
            Log.d("ControlScreen", "INCFG size=${list.size}")
            if (list.size == 9) {
                baseRows = buildRowsFromCfg(list)
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
        if (ble.isConnected) ble.requestInputCfg()
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

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(-1) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(rows, key = { _, r -> r.id }) { index, row ->
                val isDragging = draggingId == row.id

                val checked = when (row.id) {
                    "hazard" -> hazardOn
                    "left" -> states.getOrElse(0) { false }
                    "right" -> states.getOrElse(4) { false }
                    else -> {
                        val num = row.id.removePrefix("out_").toIntOrNull() ?: 0
                        states.getOrElse(num - 1) { false }
                    }
                }

                val rowsLatest by rememberUpdatedState(rows)
                val indexLatest by rememberUpdatedState(index)

                // Stan wciśnięcia (do rozjaśnienia)
                var isPressed by remember { mutableStateOf(false) }
                var pressStartTime by remember { mutableStateOf(0L) }

                val backgroundColor by animateColorAsState(
                    targetValue = when {
                        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    label = "rowBg"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 12f else 0f
                        }
                        // Drag do zmiany kolejności – tylko po długim przytrzymaniu
                        .pointerInput(row.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = row.id
                                    dragIndex = indexLatest
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    dragIndex = -1
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    dragIndex = -1
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y

                                    val from = dragIndex
                                    if (from < 0) return@detectDragGesturesAfterLongPress

                                    val current = rowsLatest
                                    if (from !in current.indices) return@detectDragGesturesAfterLongPress

                                    val layoutInfo = listState.layoutInfo
                                    val draggedInfo = layoutInfo.visibleItemsInfo
                                        .find { it.index == from }
                                        ?: return@detectDragGesturesAfterLongPress

                                    val draggedCenter =
                                        draggedInfo.offset + draggedInfo.size / 2f + dragOffset

                                    val target = layoutInfo.visibleItemsInfo
                                        .minByOrNull { abs((it.offset + it.size / 2f) - draggedCenter) }
                                        ?: return@detectDragGesturesAfterLongPress

                                    val to = target.index
                                    if (to != from && to in current.indices) {
                                        val mutable = current.toMutableList()
                                        val item = mutable.removeAt(from)
                                        mutable.add(to, item)
                                        persistOrder(mutable)

                                        val newInfo = listState.layoutInfo.visibleItemsInfo
                                            .find { it.index == to }
                                        if (newInfo != null) {
                                            dragOffset =
                                                draggedCenter - (newInfo.offset + newInfo.size / 2f)
                                        }
                                        dragIndex = to
                                    }
                                }
                            )
                        }
                ) {
                    // Cały pasek jest przyciskiem
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                            .pointerInput(row.id, shortThresholdMs) {
                                detectTapGestures(
                                    onPress = {
                                        if (!isConnected) return@detectTapGestures
                                        isPressed = true
                                        pressStartTime = System.currentTimeMillis()
                                        tryAwaitRelease()
                                        isPressed = false
                                        val duration = System.currentTimeMillis() - pressStartTime
                                        val isLong = duration >= shortThresholdMs
                                        performAction(row.id, isLong)
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (checked)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )

                            // Wskaźnik stanu (mała kropka zamiast Switcha)
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (checked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}