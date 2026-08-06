package com.yamahub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import com.yamahub.app.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

// ---------------------------------------------------------------------------
// ESP modes
// ---------------------------------------------------------------------------
private object Mode {
    const val TOGGLE = 0
    const val MOMENT = 1
    const val LEFT = 2
    const val RIGHT = 3
    const val SENSOR = 4
    const val DISABLED = 5
    const val STARTER = 6
}

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------
private enum class FnKind {
    LEFT, RIGHT, LIGHTS, BRAKE, NEUTRAL, STARTER, BUTTON, SENSOR, DISABLED
}

private var nextSlotId = 1L

private data class FnSlot(
    val kind: FnKind,
    val variant: Int = 1,
    val outNum: Int = 1,
    /** Drugi OUT tylko przy 1× LIGHTS (LOW = outNum, HI = outNum2). 0 = brak. */
    val outNum2: Int = 0,
    val customName: String = "",
    val id: Long = nextSlotId++
)

private fun FnSlot.title(): String = when (kind) {
    FnKind.LEFT -> "KIERUNEK L"
    FnKind.RIGHT -> "KIERUNEK P"
    FnKind.LIGHTS -> "LIGHTS"
    FnKind.BRAKE -> "BRAKE"
    FnKind.NEUTRAL -> "NEUTRAL"
    FnKind.STARTER -> "STARTER"
    FnKind.BUTTON -> customName.ifBlank { "BUTTON" }
    FnKind.SENSOR -> customName.ifBlank { "SENSOR" }
    FnKind.DISABLED -> "DISABLED"
}

private fun FnSlot.subtitle(lightsCount: Int, brakesCount: Int): String? = when (kind) {
    FnKind.LIGHTS -> when {
        lightsCount < 2 -> null
        variant <= 1 -> "LOW BEAM"
        else -> "HI BEAM"
    }
    FnKind.BRAKE -> when {
        brakesCount < 2 -> null
        variant <= 1 -> "front"
        else -> "rear"
    }
    else -> null
}

/** Stałe funkcje – bez zmiany rodzaju. LIGHTS 2 / BRAKE 2 są zdejmowalne. */
private fun FnSlot.isFixed(): Boolean = when (kind) {
    FnKind.LEFT, FnKind.RIGHT, FnKind.NEUTRAL, FnKind.STARTER -> true
    FnKind.LIGHTS, FnKind.BRAKE -> variant <= 1
    else -> false
}

private fun FnSlot.hasOutPicker(): Boolean = when (kind) {
    FnKind.DISABLED, FnKind.SENSOR, FnKind.NEUTRAL -> false
    else -> true
}

private fun FnSlot.toMode(): Int = when (kind) {
    FnKind.LEFT -> Mode.LEFT
    FnKind.RIGHT -> Mode.RIGHT
    FnKind.LIGHTS, FnKind.BUTTON -> Mode.TOGGLE
    FnKind.BRAKE, FnKind.NEUTRAL -> Mode.MOMENT
    FnKind.SENSOR -> Mode.SENSOR
    FnKind.DISABLED -> Mode.DISABLED
    FnKind.STARTER -> Mode.STARTER
}

private fun FnSlot.toWireName(lightsCount: Int): String = when (kind) {
    FnKind.LEFT -> "Kierunek_L"
    FnKind.RIGHT -> "Kierunek_P"
    FnKind.LIGHTS -> when {
        lightsCount < 2 && outNum2 in 1..10 -> "LIGHTS_H$outNum2"
        variant <= 1 -> "LIGHTS"
        else -> "LIGHTS_2"
    }
    FnKind.BRAKE -> if (variant <= 1) "BRAKE_front" else "BRAKE_rear"
    FnKind.NEUTRAL -> "NEUTRAL"
    FnKind.STARTER -> "STARTER"
    FnKind.BUTTON -> customName.ifBlank { "BUTTON" }.replace(" ", "_").take(15)
    FnKind.SENSOR -> customName.ifBlank { "SENSOR" }.replace(" ", "_").take(15)
    FnKind.DISABLED -> "DISABLED"
}

private fun InputCfgItem.toFnSlot(): FnSlot {
    val n = name.trim()
    val nd = displayName(n).lowercase()
    val o = outNum.coerceIn(1, 10)
    return when (mode) {
        Mode.LEFT -> FnSlot(FnKind.LEFT, outNum = o)
        Mode.RIGHT -> FnSlot(FnKind.RIGHT, outNum = o)
        Mode.SENSOR -> FnSlot(
            FnKind.SENSOR, outNum = o,
            customName = displayName(n).ifBlank { "SENSOR" }
        )
        Mode.DISABLED -> FnSlot(FnKind.DISABLED, outNum = o)
        Mode.STARTER -> FnSlot(FnKind.STARTER, outNum = o)
        Mode.MOMENT -> when {
            nd.contains("neutral") -> FnSlot(FnKind.NEUTRAL, outNum = o)
            nd.contains("brake") && (nd.contains("rear") || nd.contains("2")) ->
                FnSlot(FnKind.BRAKE, variant = 2, outNum = o)
            nd.contains("starter") -> FnSlot(FnKind.STARTER, outNum = o)
            else -> FnSlot(FnKind.BRAKE, variant = 1, outNum = o)
        }
        else -> when {
            nd.contains("starter") -> FnSlot(FnKind.STARTER, outNum = o)
            nd.startsWith("lights") || nd.contains("light") ||
                nd.contains("hi_beam") || nd.contains("low_beam") ||
                nd.contains("hibeam") || nd.contains("lowbeam") -> {
                val hiMatch = Regex("""LIGHTS_H(\d+)""", RegexOption.IGNORE_CASE).find(n)
                val hi = hiMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 0
                val v = when {
                    hi > 0 -> 1
                    nd.contains("2") || n.contains("_2") -> 2
                    else -> 1
                }
                FnSlot(FnKind.LIGHTS, variant = v, outNum = o, outNum2 = hi)
            }
            else -> FnSlot(FnKind.BUTTON, outNum = o, customName = displayName(n))
        }
    }
}

private fun defaultSlots(): List<FnSlot> = listOf(
    FnSlot(FnKind.LEFT, outNum = 1),
    FnSlot(FnKind.RIGHT, outNum = 5),
    FnSlot(FnKind.LIGHTS, variant = 1, outNum = 3, outNum2 = 7),
    FnSlot(FnKind.BRAKE, variant = 1, outNum = 4),
    FnSlot(FnKind.NEUTRAL, outNum = 6),
    FnSlot(FnKind.STARTER, outNum = 10),
    FnSlot(FnKind.BUTTON, outNum = 2, customName = "BUTTON"),
    FnSlot(FnKind.BUTTON, outNum = 8, customName = "BUTTON"),
    FnSlot(FnKind.DISABLED, outNum = 9),
    FnSlot(FnKind.DISABLED, outNum = 1)
)

/** Uzupełnia brakujące wymagane funkcje; nie rusza istniejącego STARTER / LIGHTS OUT. */
private fun normalizeSlots(raw: List<FnSlot>): List<FnSlot> {
    val list = raw.take(10).toMutableList()
    while (list.size < 10) {
        list.add(FnSlot(FnKind.DISABLED, outNum = (list.size + 1).coerceAtMost(10)))
    }

    fun ensureOne(kind: FnKind, variant: Int = 1, outFallback: Int) {
        if (list.none { it.kind == kind && it.variant == variant }) {
            val free = list.indexOfFirst {
                it.kind == FnKind.DISABLED || it.kind == FnKind.BUTTON
            }
            if (free >= 0) {
                list[free] = FnSlot(kind, variant, outFallback)
            }
        }
    }
    ensureOne(FnKind.LEFT, 1, 1)
    ensureOne(FnKind.RIGHT, 1, 5)
    ensureOne(FnKind.LIGHTS, 1, 3)
    ensureOne(FnKind.BRAKE, 1, 4)
    ensureOne(FnKind.NEUTRAL, 1, 6)
    ensureOne(FnKind.STARTER, 1, 10)

    var leftSeen = false
    var rightSeen = false
    var lights = 0
    var brakes = 0
    var neutralSeen = false
    var starterSeen = false
    for (i in list.indices) {
        val s = list[i]
        when (s.kind) {
            FnKind.LEFT -> {
                if (leftSeen) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else leftSeen = true
            }
            FnKind.RIGHT -> {
                if (rightSeen) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else rightSeen = true
            }
            FnKind.LIGHTS -> {
                lights++
                if (lights > 2) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else list[i] = s.copy(variant = lights)
            }
            FnKind.BRAKE -> {
                brakes++
                if (brakes > 2) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else list[i] = s.copy(variant = brakes)
            }
            FnKind.NEUTRAL -> {
                if (neutralSeen) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else neutralSeen = true
            }
            FnKind.STARTER -> {
                if (starterSeen) list[i] = FnSlot(FnKind.DISABLED, outNum = s.outNum)
                else starterSeen = true
            }
            else -> {}
        }
    }

    // Oba BRAKE – ten sam OUT
    val brakeOut = list.firstOrNull { it.kind == FnKind.BRAKE }?.outNum
    if (brakeOut != null) {
        for (i in list.indices) {
            if (list[i].kind == FnKind.BRAKE) {
                list[i] = list[i].copy(outNum = brakeOut)
            }
        }
    }
    return list
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSettingsTab() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var saved by remember { mutableStateOf(defaultSlots()) }
    var draft by remember { mutableStateOf(defaultSlots()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var expandedIdx by remember { mutableIntStateOf(-1) }

    var dragId by remember { mutableStateOf(-1L) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val dirty = draft != saved
    val rowHeightPx = with(density) { (56.dp + 6.dp).toPx() }
    val lightsCount = draft.count { it.kind == FnKind.LIGHTS }
    val brakesCount = draft.count { it.kind == FnKind.BRAKE }

    // ----- claims / conflict -----
    fun collectOutClaims(slots: List<FnSlot> = draft): List<Pair<Int, String>> {
        val claims = mutableListOf<Pair<Int, String>>()
        val lc = slots.count { it.kind == FnKind.LIGHTS }
        var brakeDone = false
        slots.forEach { s ->
            if (!s.hasOutPicker()) return@forEach
            when (s.kind) {
                FnKind.BRAKE -> {
                    if (!brakeDone) {
                        claims += s.outNum.coerceIn(1, 10) to "BRAKE"
                        brakeDone = true
                    }
                }
                FnKind.LIGHTS -> {
                    if (lc < 2) {
                        claims += s.outNum.coerceIn(1, 10) to "LIGHTS LOW"
                        if (s.outNum2 in 1..10) claims += s.outNum2 to "LIGHTS HI"
                    } else {
                        val tag = if (s.variant <= 1) "LIGHTS LOW" else "LIGHTS HI"
                        claims += s.outNum.coerceIn(1, 10) to tag
                    }
                }
                else -> claims += s.outNum.coerceIn(1, 10) to s.title()
            }
        }
        return claims
    }

    fun outOccupants(): Map<Int, List<String>> =
        collectOutClaims().groupBy({ it.first }, { it.second })

    fun outsOf(slot: FnSlot, lc: Int): Set<Int> {
        if (!slot.hasOutPicker()) return emptySet()
        return if (slot.kind == FnKind.LIGHTS && lc < 2) {
            buildSet {
                add(slot.outNum.coerceIn(1, 10))
                if (slot.outNum2 in 1..10) add(slot.outNum2)
            }
        } else {
            setOf(slot.outNum.coerceIn(1, 10))
        }
    }

    fun isOutConflictAt(index: Int): Boolean {
        val s = draft.getOrNull(index) ?: return false
        if (!s.hasOutPicker()) return false
        val lc = lightsCount
        val mine = outsOf(s, lc)
        if (mine.isEmpty()) return false
        if (s.kind == FnKind.LIGHTS && lc < 2 &&
            s.outNum2 in 1..10 && s.outNum == s.outNum2
        ) return true
        for (i in draft.indices) {
            if (i == index) continue
            val o = draft[i]
            if (!o.hasOutPicker()) continue
            if (s.kind == FnKind.BRAKE && o.kind == FnKind.BRAKE) continue
            if (mine.any { it in outsOf(o, lc) }) return true
        }
        return false
    }

    fun freeOut(excluding: Set<Int>, preferred: Int = 0, slots: List<FnSlot> = draft): Int {
        val used = collectOutClaims(slots).map { it.first }.toSet() + excluding
        if (preferred in 1..10 && preferred !in used) return preferred
        for (o in 1..10) if (o !in used) return o
        return preferred.takeIf { it in 1..10 } ?: 1
    }

    fun countKind(kind: FnKind) = draft.count { it.kind == kind }

    fun validate(): String? {
        if (countKind(FnKind.LEFT) != 1) return "Wymagane dokładnie 1× KIERUNEK L"
        if (countKind(FnKind.RIGHT) != 1) return "Wymagane dokładnie 1× KIERUNEK P"
        if (countKind(FnKind.LIGHTS) < 1) return "Wymagane co najmniej 1× LIGHTS"
        if (countKind(FnKind.BRAKE) < 1) return "Wymagane co najmniej 1× BRAKE"
        if (countKind(FnKind.NEUTRAL) != 1) return "Wymagane dokładnie 1× NEUTRAL"
        if (countKind(FnKind.STARTER) != 1) return "Wymagane dokładnie 1× STARTER"

        if (lightsCount < 2) {
            val lights = draft.firstOrNull { it.kind == FnKind.LIGHTS }
            if (lights != null && lights.outNum2 !in 1..10) {
                return "LIGHTS: wybierz OUT dla HI BEAM"
            }
            if (lights != null && lights.outNum == lights.outNum2) {
                return "LIGHTS: LOW i HI muszą mieć różne OUT"
            }
        }

        val dup = collectOutClaims().groupBy { it.first }.filter { it.value.size > 1 }
        if (dup.isNotEmpty()) {
            return dup.entries.joinToString("; ") { (out, who) ->
                "OUT_" + out + ": " + who.joinToString(", ") { it.second }
            }
        }
        return null
    }

    // ----- ESP -----
    fun applyFromEsp(list: List<InputCfgItem>) {
        if (list.size !in 9..10) return
        val padded = list.toMutableList()
        while (padded.size < 10) {
            val n = padded.size + 1
            padded.add(InputCfgItem(n, Mode.DISABLED, n.coerceAtMost(10), "DISABLED"))
        }
        val slots = normalizeSlots(padded.map { it.toFnSlot() })
        saved = slots
        draft = slots
        if (saving) saving = false
        error = null
    }

    DisposableEffect(Unit) {
        val prev = ble.onInputCfg
        ble.onInputCfg = { list ->
            applyFromEsp(list)
            prev?.invoke(list)
        }
        onDispose { ble.onInputCfg = prev }
    }

    LaunchedEffect(Unit) {
        delay(300)
        if (ble.isConnected) ble.requestInputCfg()
    }

    fun saveAll() {
        val err = validate()
        if (err != null) {
            error = err
            return
        }
        val toSave = draft.toList()
        val dup = collectOutClaims(toSave).groupBy { it.first }.filter { it.value.size > 1 }
        if (dup.isNotEmpty()) {
            error = dup.entries.joinToString("; ") { (out, who) ->
                "OUT_" + out + ": " + who.joinToString(", ") { it.second }
            }
            return
        }
        error = null
        saving = true
        scope.launch {
            try {
                val lc = toSave.count { it.kind == FnKind.LIGHTS }
                toSave.forEachIndexed { index, slot ->
                    ble.setInputCfg(
                        inNum = index + 1,
                        mode = slot.toMode(),
                        outNum = slot.outNum.coerceIn(1, 10),
                        name = slot.toWireName(lc)
                    )
                    delay(80)
                }
                delay(250)
                ble.requestInputCfg()
                // fallback: nie wisieć wiecznie, jeśli notify nie wróci
                delay(2000)
                if (saving) {
                    saved = toSave
                    saving = false
                }
            } catch (e: Exception) {
                saving = false
                error = "Błąd zapisu: ${e.message}"
            }
        }
    }

    // ----- mutations -----
    fun setSlot(index: Int, slot: FnSlot) {
        if (index !in draft.indices) return
        val list = draft.toMutableList()
        list[index] = slot
        if (slot.kind == FnKind.BRAKE) {
            for (i in list.indices) {
                if (list[i].kind == FnKind.BRAKE) {
                    list[i] = list[i].copy(outNum = slot.outNum)
                }
            }
        }
        draft = list
        error = null
    }

    fun changeKind(index: Int, newKind: FnKind) {
        val cur = draft.getOrNull(index) ?: return
        if (cur.isFixed()) return

        val wasLights2 = cur.kind == FnKind.LIGHTS && cur.variant > 1
        val hiFromRemoved = if (wasLights2) cur.outNum else 0

        val slot = when (newKind) {
            FnKind.LIGHTS -> {
                val primary = draft.firstOrNull { it.kind == FnKind.LIGHTS && it.variant <= 1 }
                val low = primary?.outNum?.coerceIn(1, 10) ?: 3
                val hi = when {
                    primary != null && primary.outNum2 in 1..10 && primary.outNum2 != low ->
                        primary.outNum2
                    else -> freeOut(excluding = setOf(low), preferred = cur.outNum)
                }
                FnSlot(FnKind.LIGHTS, variant = 2, outNum = hi)
            }
            FnKind.BRAKE -> {
                val shared = draft.firstOrNull { it.kind == FnKind.BRAKE }?.outNum ?: cur.outNum
                FnSlot(FnKind.BRAKE, variant = 2, outNum = shared)
            }
            FnKind.BUTTON -> FnSlot(FnKind.BUTTON, outNum = cur.outNum, customName = "BUTTON")
            FnKind.SENSOR -> FnSlot(FnKind.SENSOR, outNum = cur.outNum, customName = "SENSOR")
            FnKind.DISABLED -> FnSlot(FnKind.DISABLED, outNum = cur.outNum)
            else -> cur
        }

        val list = draft.toMutableList()
        list[index] = slot

        if (newKind == FnKind.LIGHTS) {
            for (i in list.indices) {
                if (list[i].kind == FnKind.LIGHTS && list[i].variant <= 1) {
                    list[i] = list[i].copy(outNum2 = 0)
                }
            }
        }
        if (wasLights2 && newKind != FnKind.LIGHTS) {
            for (i in list.indices) {
                if (list[i].kind == FnKind.LIGHTS && list[i].variant <= 1) {
                    val hi = hiFromRemoved.takeIf { it in 1..10 } ?: list[i].outNum2
                    list[i] = list[i].copy(outNum2 = hi)
                }
            }
        }
        if (newKind == FnKind.BRAKE) {
            val shared = slot.outNum
            for (i in list.indices) {
                if (list[i].kind == FnKind.BRAKE) {
                    list[i] = list[i].copy(outNum = shared)
                }
            }
        }
        draft = list
        error = null
    }

    fun moveSlot(from: Int, to: Int) {
        if (from == to) return
        if (from !in draft.indices || to !in draft.indices) return
        if (draft.size != 10) return
        val list = draft.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        if (list.size != 10) return
        if (list.map { it.id }.toSet().size != 10) return
        draft = list
        when {
            expandedIdx == from -> expandedIdx = to
            from < expandedIdx && to >= expandedIdx -> expandedIdx--
            from > expandedIdx && to <= expandedIdx -> expandedIdx++
        }
        error = null
    }

    fun endDrag() {
        if (dragId >= 0L && rowHeightPx > 0f) {
            val from = draft.indexOfFirst { it.id == dragId }
            if (from >= 0) {
                val steps = round(dragOffsetY / rowHeightPx).toInt()
                val to = (from + steps).coerceIn(0, draft.lastIndex)
                if (to != from) moveSlot(from, to)
            }
        }
        dragId = -1L
        dragOffsetY = 0f
    }

    fun cancelDrag() {
        dragId = -1L
        dragOffsetY = 0f
    }

    val liveError = remember(draft) { validate() }
    val showError = error ?: liveError

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
            itemsIndexed(
                items = draft,
                key = { _, s -> s.id }
            ) { index, slot ->
                val inLabel = "IN_%02d".format(index + 1)
                val isDragging = dragId >= 0L && slot.id == dragId
                val isExpanded = expandedIdx == index
                val conflict = isOutConflictAt(index)
                val sub = slot.subtitle(lightsCount, brakesCount)

                // gdzie wyląduje przeciągany element (wizualna luka)
                val dragFromIndex = if (dragId >= 0L) draft.indexOfFirst { it.id == dragId } else -1
                val dragToIndex = if (dragFromIndex >= 0 && rowHeightPx > 0f) {
                    (dragFromIndex + kotlin.math.round(dragOffsetY / rowHeightPx).toInt())
                        .coerceIn(0, draft.lastIndex)
                } else -1

                // rozsuwanie: sąsiedzi ustępują miejsca w kierunku upuszczenia
                val gapShiftY = when {
                    dragFromIndex < 0 || isDragging -> 0f
                    dragFromIndex < dragToIndex &&
                        index in (dragFromIndex + 1)..dragToIndex -> -rowHeightPx
                    dragToIndex < dragFromIndex &&
                        index in dragToIndex until dragFromIndex -> rowHeightPx
                    else -> 0f
                }

                val cardElevation by animateDpAsState(
                    if (isDragging) 8.dp else 0.dp, label = "elev"
                )
                val cardBg by animateColorAsState(
                    if (isDragging) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    label = "bg"
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            inLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(
                        Modifier
                            .weight(1f)
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    shadowElevation = 12f
                                    alpha = 0.95f
                                } else if (gapShiftY != 0f) {
                                    translationY = gapShiftY
                                }
                            }
                            .shadow(cardElevation, RoundedCornerShape(10.dp))
                            .background(cardBg, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .pointerInput(slot.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragId = slot.id
                                                dragOffsetY = 0f
                                                expandedIdx = -1
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffsetY += amount.y
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { cancelDrag() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                    tint = if (isDragging)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedIdx = if (isExpanded) -1 else index
                                    }
                            ) {
                                Text(
                                    text = slot.title(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (conflict)
                                        MaterialTheme.colorScheme.error
                                    else
                                        LocalContentColor.current
                                )
                                when {
                                    conflict && slot.hasOutPicker() -> Text(
                                        text = "OUT_${slot.outNum}  · konflikt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    sub != null -> Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = {
                                expandedIdx = if (isExpanded) -1 else index
                            }) {
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }

                        if (isExpanded) {
                            Spacer(Modifier.height(6.dp))
                            SlotEditor(
                                slot = slot,
                                canAddLights2 = countKind(FnKind.LIGHTS) < 2,
                                canAddBrake2 = countKind(FnKind.BRAKE) < 2,
                                lightsCount = lightsCount,
                                outOccupants = outOccupants(),
                                onChange = { setSlot(index, it) },
                                onChangeKind = { changeKind(index, it) }
                            )
                        }
                    }
                }
            }
        }

        if (showError != null) {
            Text(
                showError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(
            onClick = { saveAll() },
            enabled = dirty && !saving && ble.isConnected && liveError == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Zapisywanie…" else "Zapisz ustawienia")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditor(
    slot: FnSlot,
    canAddLights2: Boolean,
    canAddBrake2: Boolean,
    lightsCount: Int,
    outOccupants: Map<Int, List<String>>,
    onChange: (FnSlot) -> Unit,
    onChangeKind: (FnKind) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!slot.isFixed()) {
            var kindExpanded by remember { mutableStateOf(false) }
            val options = buildList {
                add(FnKind.BUTTON to "BUTTON")
                add(FnKind.SENSOR to "SENSOR")
                add(FnKind.DISABLED to "DISABLED")
                if (canAddLights2) add(FnKind.LIGHTS to "LIGHTS 2")
                if (canAddBrake2) add(FnKind.BRAKE to "BRAKE 2")
            }
            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { kindExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (slot.kind) {
                        FnKind.BUTTON -> "BUTTON"
                        FnKind.SENSOR -> "SENSOR"
                        FnKind.DISABLED -> "DISABLED"
                        FnKind.LIGHTS -> "LIGHTS 2"
                        FnKind.BRAKE -> "BRAKE 2"
                        else -> slot.title()
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Funkcja") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = kindExpanded,
                    onDismissRequest = { kindExpanded = false }
                ) {
                    options.forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                kindExpanded = false
                                onChangeKind(k)
                            }
                        )
                    }
                }
            }
        }

        if (slot.kind == FnKind.BUTTON || slot.kind == FnKind.SENSOR) {
            OutlinedTextField(
                value = slot.customName,
                onValueChange = { onChange(slot.copy(customName = it.take(15))) },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (slot.hasOutPicker()) {
            val dualLights = slot.kind == FnKind.LIGHTS && lightsCount < 2
            if (dualLights) {
                OutPicker(
                    label = "OUT · LOW BEAM",
                    selected = slot.outNum,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum),
                    onSelect = { onChange(slot.copy(outNum = it)) }
                )
                OutPicker(
                    label = "OUT · HI BEAM",
                    selected = slot.outNum2.takeIf { it in 1..10 } ?: 0,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum2),
                    onSelect = { onChange(slot.copy(outNum2 = it)) }
                )
            } else {
                val label = when {
                    slot.kind == FnKind.LIGHTS && slot.variant <= 1 -> "OUT · LOW BEAM"
                    slot.kind == FnKind.LIGHTS -> "OUT · HI BEAM"
                    slot.kind == FnKind.BRAKE -> "OUT · BRAKE"
                    else -> "Wyjście"
                }
                OutPicker(
                    label = label,
                    selected = slot.outNum,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum),
                    onSelect = { onChange(slot.copy(outNum = it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutPicker(
    label: String,
    selected: Int,
    outOccupants: Map<Int, List<String>>,
    excludeSelf: Set<Int>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = if (selected in 1..10) "OUT_$selected" else "—",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (1..10).forEach { o ->
                val holders = outOccupants[o].orEmpty()
                val takenByOther = holders.isNotEmpty() && o !in excludeSelf
                val text = if (holders.isNotEmpty()) {
                    "OUT_$o  · " + holders.joinToString(", ")
                } else {
                    "OUT_$o"
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            color = if (takenByOther)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            else
                                LocalContentColor.current
                        )
                    },
                    enabled = true,
                    onClick = {
                        expanded = false
                        onSelect(o)
                    }
                )
            }
        }
    }
}
