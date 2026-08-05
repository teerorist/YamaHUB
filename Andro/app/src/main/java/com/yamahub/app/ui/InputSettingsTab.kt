package com.yamahub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.rememberUpdatedState
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

/**
 * Tryby ESP (inputs.h):
 * 0 TOGGLE/BUTTON/LIGHTS | 1 MOMENT (BRAKE/NEUTRAL) | 2 LEFT | 3 RIGHT
 * 4 SENSOR | 5 DISABLED | 6 STARTER
 *
 * IN_01…IN_10 – lewa kolumna stała (bez DnD).
 * Prawa karta = funkcja, DnD tylko po długim przytrzymaniu uchwytu.
 */
private object Mode {
    const val TOGGLE = 0
    const val MOMENT = 1
    const val LEFT = 2
    const val RIGHT = 3
    const val SENSOR = 4
    const val DISABLED = 5
    const val STARTER = 6
}

private enum class FnKind {
    LEFT, RIGHT, LIGHTS, BRAKE, NEUTRAL, STARTER, BUTTON, SENSOR, DISABLED
}

private var nextSlotId = 1L

private data class FnSlot(
    val kind: FnKind,
    val variant: Int = 1,
    val outNum: Int = 1,
    val customName: String = "",
    val id: Long = nextSlotId++
)

private fun FnSlot.title(): String = when (kind) {
    FnKind.LEFT -> "KIERUNEK L"
    FnKind.RIGHT -> "KIERUNEK P"
    FnKind.LIGHTS -> if (variant <= 1) "LIGHTS" else "LIGHTS $variant"
    FnKind.BRAKE -> if (variant == 2) "BRAKE rear" else "BRAKE"
    FnKind.NEUTRAL -> "NEUTRAL"
    FnKind.STARTER -> "STARTER"
    FnKind.BUTTON -> customName.ifBlank { "BUTTON" }
    FnKind.SENSOR -> "SENSOR"
    FnKind.DISABLED -> "DISABLED"
}

private fun FnSlot.isFixed(): Boolean = when (kind) {
    FnKind.LEFT, FnKind.RIGHT, FnKind.NEUTRAL, FnKind.STARTER -> true
    FnKind.LIGHTS -> variant <= 1
    FnKind.BRAKE -> variant <= 1
    else -> false
}

private fun FnSlot.hasOutPicker(): Boolean = kind != FnKind.DISABLED

private fun FnSlot.toMode(): Int = when (kind) {
    FnKind.LEFT -> Mode.LEFT
    FnKind.RIGHT -> Mode.RIGHT
    FnKind.LIGHTS, FnKind.BUTTON -> Mode.TOGGLE
    FnKind.BRAKE, FnKind.NEUTRAL -> Mode.MOMENT
    FnKind.SENSOR -> Mode.SENSOR
    FnKind.DISABLED -> Mode.DISABLED
    FnKind.STARTER -> Mode.STARTER
}

private fun FnSlot.toWireName(): String = when (kind) {
    FnKind.LEFT -> "Kierunek_L"
    FnKind.RIGHT -> "Kierunek_P"
    FnKind.LIGHTS -> if (variant <= 1) "LIGHTS" else "LIGHTS_$variant"
    FnKind.BRAKE -> if (variant <= 1) "BRAKE_front" else "BRAKE_rear"
    FnKind.NEUTRAL -> "NEUTRAL"
    FnKind.STARTER -> "STARTER"
    FnKind.BUTTON -> customName.ifBlank { "BUTTON" }.replace(" ", "_").take(15)
    FnKind.SENSOR -> "SENSOR"
    FnKind.DISABLED -> "DISABLED"
}

private fun InputCfgItem.toFnSlot(): FnSlot {
    val n = name.trim()
    val nd = displayName(n).lowercase()
    val o = outNum.coerceIn(1, 10)
    return when (mode) {
        Mode.LEFT -> FnSlot(FnKind.LEFT, outNum = o)
        Mode.RIGHT -> FnSlot(FnKind.RIGHT, outNum = o)
        Mode.SENSOR -> FnSlot(FnKind.SENSOR, outNum = o)
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
                nd.contains("hi beam") || nd.contains("lowbeam") -> {
                val v = if (nd.contains("2") || n.contains("_2")) 2 else 1
                FnSlot(FnKind.LIGHTS, variant = v, outNum = o)
            }
            else -> FnSlot(FnKind.BUTTON, outNum = o, customName = displayName(n))
        }
    }
}

private fun defaultSlots(): List<FnSlot> = listOf(
    FnSlot(FnKind.LEFT, outNum = 1),
    FnSlot(FnKind.RIGHT, outNum = 5),
    FnSlot(FnKind.LIGHTS, variant = 1, outNum = 3),
    FnSlot(FnKind.BRAKE, variant = 1, outNum = 4),
    FnSlot(FnKind.NEUTRAL, outNum = 6),
    FnSlot(FnKind.BUTTON, outNum = 7, customName = "BUTTON"),
    FnSlot(FnKind.BUTTON, outNum = 8, customName = "BUTTON"),
    FnSlot(FnKind.DISABLED, outNum = 9),
    FnSlot(FnKind.DISABLED, outNum = 2),
    FnSlot(FnKind.STARTER, outNum = 10)
)

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
            if (free >= 0) list[free] = FnSlot(kind, variant, outFallback)
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
    return list
}

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

    // DnD state – indeks w draft, nie w LazyColumn key
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val dirty = draft != saved

    // przybliżona wysokość rzędu (zwinięty) w px – do swapów w trakcie drag
    val rowHeightPx = with(density) { (56.dp + 6.dp).toPx() } // karta + spacedBy

    fun applyFromEsp(list: List<InputCfgItem>) {
        if (list.size !in 9..10) return
        val padded = list.toMutableList()
        while (padded.size < 10) {
            val n = padded.size + 1
            padded.add(InputCfgItem(n, Mode.DISABLED, n.coerceAtMost(10), "DISABLED"))
        }
        if (list.size == 9 && padded.none {
                it.mode == Mode.STARTER ||
                    displayName(it.name).contains("starter", ignoreCase = true)
            }
        ) {
            padded[9] = InputCfgItem(10, Mode.STARTER, 10, "STARTER")
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

    fun countKind(kind: FnKind) = draft.count { it.kind == kind }
    fun canAddLights2() = countKind(FnKind.LIGHTS) == 1
    fun canAddBrake2() = countKind(FnKind.BRAKE) == 1

    fun usedOutNumbers(exceptIndex: Int? = null): Set<Int> =
        draft.mapIndexedNotNull { i, s ->
            if (exceptIndex != null && i == exceptIndex) null
            else if (s.hasOutPicker()) s.outNum
            else null
        }.toSet()

    fun validate(): String? {
        if (countKind(FnKind.LEFT) != 1) return "Wymagane dokładnie 1× KIERUNEK L"
        if (countKind(FnKind.RIGHT) != 1) return "Wymagane dokładnie 1× KIERUNEK P"
        if (countKind(FnKind.LIGHTS) < 1) return "Wymagane co najmniej 1× LIGHTS"
        if (countKind(FnKind.BRAKE) < 1) return "Wymagane co najmniej 1× BRAKE"
        if (countKind(FnKind.NEUTRAL) != 1) return "Wymagane dokładnie 1× NEUTRAL"
        if (countKind(FnKind.STARTER) != 1) return "Wymagane dokładnie 1× STARTER"
        val outs = draft.filter { it.hasOutPicker() }.map { it.outNum }
        if (outs.size != outs.toSet().size) {
            val dup = outs.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            return "OUT zajęte wielokrotnie: " + dup.joinToString { "OUT_$it" }
        }
        return null
    }

    fun saveAll() {
        val err = validate()
        if (err != null) {
            error = err
            return
        }
        error = null
        saving = true
        val toSave = draft.toList()
        scope.launch {
            try {
                toSave.forEachIndexed { index, slot ->
                    ble.setInputCfg(
                        inNum = index + 1,
                        mode = slot.toMode(),
                        outNum = slot.outNum.coerceIn(1, 10),
                        name = slot.toWireName()
                    )
                    delay(60)
                }
                delay(200)
                ble.requestInputCfg()
            } catch (e: Exception) {
                saving = false
                error = "Błąd zapisu: ${e.message}"
            }
        }
    }

    fun setSlot(index: Int, slot: FnSlot) {
        draft = draft.toMutableList().also { it[index] = slot }
        error = null
    }

    fun changeKind(index: Int, newKind: FnKind) {
        val cur = draft[index]
        if (cur.isFixed() && newKind != cur.kind) return
        val slot = when (newKind) {
            FnKind.LIGHTS -> FnSlot(FnKind.LIGHTS, variant = 2, outNum = cur.outNum)
            FnKind.BRAKE -> FnSlot(FnKind.BRAKE, variant = 2, outNum = cur.outNum)
            FnKind.BUTTON -> FnSlot(FnKind.BUTTON, outNum = cur.outNum, customName = "BUTTON")
            FnKind.SENSOR -> FnSlot(FnKind.SENSOR, outNum = cur.outNum)
            FnKind.DISABLED -> FnSlot(FnKind.DISABLED, outNum = cur.outNum)
            else -> cur
        }
        setSlot(index, slot)
    }

    /** Zamiana miejsc – funkcja wędruje między IN_XX. */
    fun moveSlot(from: Int, to: Int) {
        if (from == to || from !in draft.indices || to !in draft.indices) return
        draft = draft.toMutableList().also { list ->
            val item = list.removeAt(from)
            list.add(to, item)
        }
        // przenieś expanded razem z elementem
        when {
            expandedIdx == from -> expandedIdx = to
            from < expandedIdx && to >= expandedIdx -> expandedIdx--
            from > expandedIdx && to <= expandedIdx -> expandedIdx++
        }
        error = null
    }

    /**
     * DnD z realną zamianą w liście (nie graphicsLayer na sąsiadach).
     * Dzięki temu po puszczeniu strefy dotyku = to, co widać.
     */
    fun onHandleDrag(deltaY: Float) {
        if (dragFrom < 0) return
        dragOffsetY += deltaY
        var from = dragFrom
        // wielokrotne przeskoki w jednym geście = dowolne miejsce na liście
        while (dragOffsetY > rowHeightPx * 0.5f && from < draft.lastIndex) {
            moveSlot(from, from + 1)
            from += 1
            dragFrom = from
            dragOffsetY -= rowHeightPx
        }
        while (dragOffsetY < -rowHeightPx * 0.5f && from > 0) {
            moveSlot(from, from - 1)
            from -= 1
            dragFrom = from
            dragOffsetY += rowHeightPx
        }
    }

    fun endDrag() {
        dragFrom = -1
        dragOffsetY = 0f
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            "Złap uchwyt ⋮ – lista przestawia się pod palcem (w dowolne miejsce).\n" +
                "IN_xx zostaje na miejscu. Każde OUT_XX tylko raz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(
                items = draft,
                key = { _, s -> s.id }
            ) { index, slot ->
                // stabilniejszy klucz bez index – ale po reorder Compose i tak odświeży
                val inLabel = "IN_%02d".format(index + 1)
                val isDragging = dragFrom == index
                val isExpanded = expandedIdx == index

                val currentIndex by rememberUpdatedState(index)

                val cardElevation by animateDpAsState(
                    if (isDragging) 8.dp else 0.dp,
                    label = "elev"
                )

                val cardBg by animateColorAsState(
                    if (isDragging) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    label = "bg"
                )

                // === RZĄD: [IN stałe] + [karta funkcji – DnD] ===
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // ----- LEWA: stała etykieta IN_XX (NIE w DnD) -----
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

                    // ----- PRAWA: karta funkcji (tylko ona się rusza) -----
                    Column(
                        Modifier
                            .weight(1f)
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                // tylko lekki offset między przeskokami; lista i tak się przestawia
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    shadowElevation = 12f
                                    alpha = 0.95f
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
                            // Uchwyt DnD – duży hit-target, long-press + drag
                            // DnD tylko na uchwycie – od razu po złapaniu
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .pointerInput(slot.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                // aktualny index (nie z cache'u gestu)
                                                dragFrom = currentIndex
                                                dragOffsetY = 0f
                                                expandedIdx = -1
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                onHandleDrag(amount.y)
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { endDrag() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Przeciągnij",
                                    tint = if (isDragging)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = slot.title(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedIdx = if (isExpanded) -1 else index
                                    }
                            )

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
                                canAddLights2 = canAddLights2(),
                                canAddBrake2 = canAddBrake2(),
                                occupiedOuts = usedOutNumbers(exceptIndex = index),
                                onChange = { setSlot(index, it) },
                                onChangeKind = { changeKind(index, it) }
                            )
                        }
                    }
                }
            }
        }

        val liveError = remember(draft) { validate() }
        val showError = error ?: liveError

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
    occupiedOuts: Set<Int>,
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
                        FnKind.LIGHTS -> "LIGHTS ${slot.variant}"
                        FnKind.BRAKE -> slot.title()
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
        if (slot.kind == FnKind.BUTTON) {
            OutlinedTextField(
                value = slot.customName,
                onValueChange = { onChange(slot.copy(customName = it.take(15))) },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (slot.hasOutPicker()) {
            var outExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = outExpanded,
                onExpandedChange = { outExpanded = it }
            ) {
                OutlinedTextField(
                    value = "OUT_${slot.outNum}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Wyjście") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(outExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = outExpanded,
                    onDismissRequest = { outExpanded = false }
                ) {
                    (1..10).forEach { o ->
                        val taken = o in occupiedOuts
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (taken) "OUT_$o  · zajęte"
                                    else "OUT_$o"
                                )
                            },
                            enabled = !taken,
                            onClick = {
                                outExpanded = false
                                onChange(slot.copy(outNum = o))
                            }
                        )
                    }
                }
            }
        }

        when (slot.kind) {
            FnKind.LIGHTS -> Text(
                "Short / long – konfiguracja gestów wkrótce.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FnKind.BRAKE, FnKind.NEUTRAL -> Text(
                "Typ: moment (wciśnięty = ON, puszczony = OFF).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FnKind.STARTER -> Text(
                "Short = killswitch, long = starter. OUT = przekaźnik startera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {}
        }
    }
}
