package com.yamahub.app.ui

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay
import kotlin.math.round

@Composable
fun InputSettingsTab() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var saved by remember { mutableStateOf<List<FnSlot>?>(null) }
    var draft by remember { mutableStateOf<List<FnSlot>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var starterEnabled by remember { mutableStateOf(ble.starterEnabled) }
    var inputStates by remember { mutableStateOf(List(10) { false }) }
    var modeDefinitions by remember { mutableStateOf(emptyList<com.yamahub.app.ModeDefinition>()) }
    var expandedIdx by remember { mutableIntStateOf(-1) }

    var dragId by remember { mutableStateOf(-1L) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val rowHeightPx = with(density) { (46.dp + 6.dp).toPx() }
    val currentDraft = draft ?: emptyList()
    val lightsCount = currentDraft.count { it.kind == FnKind.LIGHTS_1 || it.kind == FnKind.LIGHTS_2 }
    val brakesCount = currentDraft.count { it.kind == FnKind.BRAKE_1 || it.kind == FnKind.BRAKE_2 }
    val hasKillSwitch = currentDraft.any { it.kind == FnKind.KILL_SWITCH }

    fun collectOutClaims(slots: List<FnSlot> = draft ?: emptyList()): List<Pair<Int, String>> {
        val claims = mutableListOf<Pair<Int, String>>()
        val lc = slots.count { it.kind == FnKind.LIGHTS_1 || it.kind == FnKind.LIGHTS_2 }
        var brakeDone = false
        slots.forEach { s ->
            if (s.kind == FnKind.DISABLED) return@forEach
            if (s.kind == FnKind.BRAKE_2) return@forEach
            if (s.category == FnCategory.SENSOR &&
                s.kind != FnKind.BRAKE_1 && s.kind != FnKind.BRAKE_2 &&
                !s.outputEnabled
            ) return@forEach
            
            when (s.kind) {
                FnKind.BRAKE_1 -> {
                    if (!brakeDone && s.outPrimary in 1..10) {
                        claims += s.outPrimary to "BRAKE"
                        brakeDone = true
                    }
                }
                FnKind.LIGHTS_1 -> {
                    if (s.outPrimary in 1..10) claims += s.outPrimary to "LIGHTS HI"
                    if (lc < 2 && s.outSecondary in 1..10) claims += s.outSecondary to "LIGHTS LOW"
                }
                FnKind.STARTER -> {
                    if (s.outPrimary in 1..10) claims += s.outPrimary to "STARTER"
                    if (!slots.any { it.kind == FnKind.KILL_SWITCH } && s.outSecondary in 1..10)
                        claims += s.outSecondary to "KILL"
                }
                FnKind.KILL_SWITCH -> {
                    if (s.outPrimary in 1..10) claims += s.outPrimary to "KILL"
                }
                FnKind.LIGHTS_2 -> {
                    if (s.outPrimary in 1..10) claims += s.outPrimary to "LIGHTS LOW"
                }
                else -> {
                    val output = if (s.kind == FnKind.USER) s.outputIndex else s.outPrimary
                    if (output in 1..10) claims += output to s.title(modeDefinitions, lc,
                        slots.count { it.kind == FnKind.BRAKE_1 || it.kind == FnKind.BRAKE_2 })
                }
            }
        }
        return claims
    }

    fun outOccupants(): Map<Int, List<String>> =
        collectOutClaims().groupBy({ it.first }, { it.second })

    fun isOutConflictAt(index: Int): Boolean {
        val s = currentDraft.getOrNull(index) ?: return false
        if (s.kind == FnKind.DISABLED) return false
        val occupants = outOccupants()
        
        // Sprawdzamy czy którykolwiek z portów tego slotu jest zajęty przez kogoś innego
        val myPorts = mutableSetOf<Int>()
        if (s.kind == FnKind.USER) {
            val on = s.category == FnCategory.BUTTON || s.outputEnabled
            if (on && s.outputIndex > 0) myPorts.add(s.outputIndex)
        } else if (s.category == FnCategory.SENSOR &&
            s.kind != FnKind.BRAKE_1 && s.kind != FnKind.BRAKE_2
        ) {
            if (s.outputEnabled && s.outPrimary > 0) myPorts.add(s.outPrimary)
        } else {
            if (s.outPrimary > 0) myPorts.add(s.outPrimary)
            if (s.kind == FnKind.LIGHTS_1 && lightsCount < 2 && s.outSecondary > 0) myPorts.add(s.outSecondary)
            if (s.kind == FnKind.STARTER && !hasKillSwitch && s.outSecondary > 0)
                myPorts.add(s.outSecondary)
        }
        
        return myPorts.any { (occupants[it]?.size ?: 0) > 1 }
    }

    fun validate(d: List<FnSlot>? = draft): String? {
        if (d == null) return null
        val required = listOf(
            FnKind.LEFT, FnKind.RIGHT, FnKind.LIGHTS_1,
            FnKind.BRAKE_1, FnKind.NEUTRAL, FnKind.STARTER
        )
        required.forEach { k ->
            if (d.count { it.kind == k } != 1) return "Brak funkcji ${k.name}"
        }
        FnKind.entries.filter { it != FnKind.USER && it != FnKind.DISABLED }.forEach { k ->
            if (d.count { it.kind == k } > 1) return "Tylko jedna funkcja ${kindPickerLabel(k)}"
        }
        d.forEach { s ->
            if (s.kind in required && s.kind != FnKind.NEUTRAL && s.outPrimary !in 1..10)
                return "${s.kind.name}: wybierz OUT"
            if (s.kind == FnKind.KILL_SWITCH && s.outPrimary !in 1..10)
                return "KILL SWITCH: wybierz OUT"
            if (s.kind == FnKind.USER && s.category == FnCategory.BUTTON && s.outputIndex !in 1..10)
                return "USER: wybierz OUT"
            if (s.kind == FnKind.USER && s.category == FnCategory.SENSOR &&
                s.outputEnabled && s.outputIndex !in 1..10
            ) return "USER: wybierz OUT"
            if (s.category == FnCategory.SENSOR && s.kind != FnKind.USER &&
                s.kind != FnKind.BRAKE_1 && s.kind != FnKind.BRAKE_2 &&
                s.outputEnabled && s.outPrimary !in 1..10
            ) return "${s.kind.name}: wybierz OUT"
        }

        val lc = d.count { it.kind == FnKind.LIGHTS_1 || it.kind == FnKind.LIGHTS_2 }

        if (lc < 2) {
            val lights = d.firstOrNull { it.kind == FnKind.LIGHTS_1 }
            if (lights != null && lights.outSecondary !in 1..10) {
                return "LIGHTS: wybierz OUT dla LOW BEAM"
            }
        }

        val dup = collectOutClaims(d).groupBy { it.first }.filter { it.value.size > 1 }
        if (dup.isNotEmpty()) {
            return dup.entries.joinToString("; ") { (out, who) ->
                "OUT %02d: ".format(out) + who.joinToString(", ") { it.second }
            }
        }
        return null
    }

    fun applyFromEsp(list: List<InputCfgItem>) {
        Log.d("InputSettingsTab", "applyFromEsp called with ${list.size} items")
        if (list.size !in 9..10) return
        val slots = list.map { it.toFnSlot() }
        saved = slots
        draft = slots
        error = null
    }

    DisposableEffect(Unit) {
        val prevCfg = ble.onInputCfg
        val prevConn = ble.onConnectionChanged
        val prevInputStates = ble.onInputStates
        ble.onInputCfg = { list ->
            Log.d("InputSettingsTab", "onInputCfg callback triggered")
            applyFromEsp(list)
            prevCfg?.invoke(list)
        }
        ble.onConnectionChanged = { c ->
            isConnected = c
            if (!c) starterEnabled = false
            prevConn?.invoke(c)
        }
        ble.onInputStates = { states -> inputStates = states; prevInputStates?.invoke(states) }
        ble.onModeDefinitions = { defs -> modeDefinitions = defs }
        val prevStarter = ble.onStarterEnabled
        ble.onStarterEnabled = { enabled -> starterEnabled = enabled; prevStarter?.invoke(enabled) }
        onDispose {
            ble.onInputCfg = prevCfg
            ble.onConnectionChanged = prevConn
            ble.onInputStates = prevInputStates
            ble.onStarterEnabled = prevStarter
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        Log.d("InputSettingsTab", "LaunchedEffect: checking cache and requesting data")
        
        // Use cached config if available to avoid hanging on loader
        ble.lastReceivedInputCfg?.let {
            Log.d("InputSettingsTab", "Using cached config")
            applyFromEsp(it)
        }

        if (ble.isConnected) ble.requestInputCfg()
        if (ble.isConnected) ble.requestModeDefinitions()
    }

    fun sendSlot(index: Int, slot: FnSlot, lightsCount: Int, hasKillSwitch: Boolean) {
        val primary = when {
            slot.kind == FnKind.DISABLED -> 0
            slot.kind == FnKind.USER &&
                (slot.category == FnCategory.BUTTON || slot.outputEnabled) -> slot.outputIndex
            slot.kind == FnKind.USER -> 0
            slot.category == FnCategory.SENSOR &&
                slot.kind != FnKind.BRAKE_1 && slot.kind != FnKind.BRAKE_2 &&
                !slot.outputEnabled -> 0
            else -> slot.outPrimary
        }
        val outEn = when {
            slot.kind == FnKind.USER && slot.category == FnCategory.BUTTON -> true
            slot.category == FnCategory.SENSOR &&
                slot.kind != FnKind.BRAKE_1 && slot.kind != FnKind.BRAKE_2 -> slot.outputEnabled
            else -> slot.outputEnabled || primary in 1..10
        }
        val secondary = when {
            slot.kind == FnKind.STARTER && !hasKillSwitch -> slot.outSecondary
            slot.kind == FnKind.LIGHTS_1 && lightsCount < 2 -> slot.outSecondary
            else -> 0
        }
        ble.setInputCfgV4(
            inNum = index + 1,
            category = when (slot.category) {
                FnCategory.BUTTON -> 0
                FnCategory.SENSOR -> 1
                else -> 2
            },
            functionId = slot.kind.id,
            outPrimary = primary,
            outSecondary = secondary,
            outputEnabled = outEn,
            outputNum = if (slot.kind == FnKind.USER && outEn) slot.outputIndex else primary,
            name = slot.customName.ifBlank { slot.category.name }
                .replace(" ", "_").replace(";", "_").replace(",", "_").take(15)
        )
    }

    fun persistDiff(newList: List<FnSlot>) {
        if (!isConnected) return
        val old = saved ?: return
        val lc = newList.count { it.kind == FnKind.LIGHTS_1 || it.kind == FnKind.LIGHTS_2 }
        val hasKillSwitch = newList.any { it.kind == FnKind.KILL_SWITCH }
        val changed = newList.indices.filter { i -> !newList[i].samePersist(old.getOrNull(i)) }
        if (changed.isEmpty()) return
        changed.forEach { i -> sendSlot(i, newList[i], lc, hasKillSwitch) }
        ble.commitInputCfg()
        saved = newList
    }

    fun persistIfValid(newList: List<FnSlot>) {
        draft = newList
        val err = validate(newList)
        error = err
        if (err == null) persistDiff(newList)
    }

    fun toggleExpand(index: Int) {
        if (expandedIdx >= 0) persistIfValid(currentDraft)
        expandedIdx = if (expandedIdx == index) -1 else index
    }

    fun moveSlot(from: Int, to: Int) {
        val d = draft ?: return
        if (from == to) return
        val list = d.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        persistIfValid(list)
    }

    fun endDrag() {
        val d = draft ?: return
        if (dragId >= 0L && rowHeightPx > 0f) {
            val from = d.indexOfFirst { it.id == dragId }
            if (from >= 0) {
                val steps = round(dragOffsetY / rowHeightPx).toInt()
                val to = (from + steps).coerceIn(0, d.lastIndex)
                if (to != from) moveSlot(from, to)
            }
        }
        dragId = -1L
        dragOffsetY = 0f
    }

    if (!isConnected) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Brak połączenia z HUBem", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    if (draft == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Pobieranie ustawień z HUBa...")
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(currentDraft, key = { _, s -> s.id }) { index, slot ->
                val isDragging = dragId >= 0L && slot.id == dragId
                val isExpanded = expandedIdx == index
                val conflict = isOutConflictAt(index)
                val isDisabled = slot.kind == FnKind.DISABLED
                
                val dragFromIndex = if (dragId >= 0L) currentDraft.indexOfFirst { it.id == dragId } else -1
                val dragToIndex = if (dragFromIndex >= 0 && rowHeightPx > 0f) {
                    (dragFromIndex + round(dragOffsetY / rowHeightPx).toInt()).coerceIn(0, currentDraft.lastIndex)
                } else -1

                val gapShiftY = when {
                    dragFromIndex < 0 || isDragging -> 0f
                    dragFromIndex < dragToIndex && index in (dragFromIndex + 1)..dragToIndex -> -rowHeightPx
                    dragToIndex < dragFromIndex && index in dragToIndex until dragFromIndex -> rowHeightPx
                    else -> 0f
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // IN Badge (Tappable)
                    Box(
                        Modifier.size(46.dp).background(
                            when {
                                isDisabled -> Color(0xFF333333)
                                inputStates.getOrElse(index) { false } -> Color.DarkGray
                                else -> Color.Black
                            },
                            RoundedCornerShape(8.dp)
                        ).border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = if (isDisabled) 0.45f else 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .pointerInput(index) {
                            detectTapGestures(onPress = {
                                ble.sendCommand("IN:${index + 1}:1")
                                try { awaitRelease() } finally { ble.sendCommand("IN:${index + 1}:0") }
                            })
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "IN %02d".format(index + 1),
                            style = if (isDisabled) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                            fontWeight = if (isDisabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDisabled) Color.White.copy(alpha = 0.45f) else Color.LightGray
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Slot Card
                    Column(
                        Modifier.weight(1f).zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else gapShiftY
                                shadowElevation = if (isDragging) 12f else 0f
                            }
                            .background(
                                if (isDragging) MaterialTheme.colorScheme.secondaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                                RoundedCornerShape(10.dp)
                            )
                            .border(1.dp, if (conflict) Color.Red else Color.Transparent, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Menu, null, Modifier.size(44.dp).pointerInput(slot.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        if (expandedIdx >= 0) persistIfValid(currentDraft)
                                        dragId = slot.id; dragOffsetY = 0f; expandedIdx = -1
                                    },
                                    onDrag = { change, amount -> change.consume(); dragOffsetY += amount.y },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { dragId = -1L }
                                )
                            }.padding(10.dp))
                            
                            Column(Modifier.weight(1f).clickable { toggleExpand(index) }) {
                                Text(slot.title(modeDefinitions, lightsCount, brakesCount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                slot.subtitle(lightsCount, hasKillSwitch)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1) }
                            }
                            IconButton(onClick = { toggleExpand(index) }) {
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        }

                        if (isExpanded) {
                            Box(Modifier.padding(bottom = 8.dp)) {
                                SlotEditor(
                                    slot = slot,
                                    modeDefinitions = modeDefinitions,
                                    outOccupants = outOccupants(),
                                    lightsCount = lightsCount,
                                    hasKillSwitch = hasKillSwitch,
                                    hideKinds = currentDraft
                                        .filter { it.id != slot.id && it.kind != FnKind.USER && it.kind != FnKind.DISABLED }
                                        .map { it.kind }
                                        .toSet(),
                                    onChange = { s ->
                                        val newList = currentDraft.toMutableList()
                                        newList[index] = s
                                        draft = newList
                                    },
                                    onChangeCategory = { c ->
                                        draft = if (c == FnCategory.DISABLED) {
                                            applyKindChange(currentDraft, index, FnKind.DISABLED)
                                        } else {
                                            currentDraft.toMutableList().also {
                                                it[index] = slot.copy(
                                                    category = c,
                                                    kind = FnKind.USER,
                                                    customName = "",
                                                    outputEnabled = c == FnCategory.BUTTON || slot.outputEnabled
                                                )
                                            }
                                        }
                                    },
                                    onChangeKind = { k ->
                                        draft = applyKindChange(currentDraft, index, k)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        }
    }
}
