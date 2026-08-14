package com.yamahub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import kotlin.math.round

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
    val rowHeightPx = with(density) { (46.dp + 6.dp).toPx() }
    val lightsCount = draft.count { it.kind == FnKind.LIGHTS }
    val brakesCount = draft.count { it.kind == FnKind.BRAKE }

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
                "OUT %02d: ".format(out) + who.joinToString(", ") { it.second }
            }
        }
        return null
    }

    fun applyFromEsp(list: List<InputCfgItem>) {
        if (list.size !in 9..10) return
        val padded = list.toMutableList()
        while (padded.size < 10) {
            val n = padded.size + 1
            padded.add(InputCfgItem(n, Mode.DISABLED, n.coerceAtMost(10), "DISABLED"))
        }
        val slots = normalizeSlots(padded.map { it.toFnSlot() })
        saved = slots

        if (saving) {
            // Sprawdzamy czy to co przyszło zgadza się z naszym szkicem (draft).
            // Ignorujemy ID (lokalne), patrzymy na funkcje i piny.
            val match = slots.size == draft.size && slots.indices.all { i ->
                val s1 = slots[i]
                val s2 = draft[i]
                s1.kind == s2.kind && s1.variant == s2.variant &&
                    s1.outNum == s2.outNum && s1.outNum2 == s2.outNum2 &&
                    s1.customName == s2.customName
            }
            if (match) {
                saving = false
            }
        } else {
            draft = slots
        }
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
                "OUT %02d: ".format(out) + who.joinToString(", ") { it.second }
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

    // Przy otwarciu edycji przewiń wiersz nad klawiaturę
    LaunchedEffect(expandedIdx) {
        if (expandedIdx in draft.indices) {
            delay(80)
            listState.animateScrollToItem(expandedIdx)
            delay(280) // czas na IME
            listState.animateScrollToItem(expandedIdx)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(12.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 6.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(
                items = draft,
                key = { _, s -> s.id }
            ) { index, slot ->
                val inLabel = "IN %02d".format(index + 1)
                val isDragging = dragId >= 0L && slot.id == dragId
                val isExpanded = expandedIdx == index
                val conflict = isOutConflictAt(index)
                val sub = slot.subtitle(lightsCount, brakesCount)

                val dragFromIndex = if (dragId >= 0L) draft.indexOfFirst { it.id == dragId } else -1
                val dragToIndex = if (dragFromIndex >= 0 && rowHeightPx > 0f) {
                    (dragFromIndex + kotlin.math.round(dragOffsetY / rowHeightPx).toInt())
                        .coerceIn(0, draft.lastIndex)
                } else -1

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
                            .size(width = 46.dp, height = 46.dp)
                            .background(
                                if (slot.kind != FnKind.DISABLED) Color(0xFF000000) else Color(0xFF333333),
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
                            color = Color.White,
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
                            Modifier
                                .height(38.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
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
                                        text = "OUT %02d  · konflikt".format(slot.outNum),
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
