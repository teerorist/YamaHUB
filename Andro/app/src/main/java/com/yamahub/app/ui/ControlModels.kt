package com.yamahub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.yamahub.app.InputCfgItem
import com.yamahub.app.displayName
import kotlinx.coroutines.delay

data class ControlInRow(
    val inNum: Int,              // 0 = pusty slot
    val mode: Int,               // -1 = unused
    val title: String,
    val subtitle: String?,
    val outNums: List<Int>,      // zawsze 1 element = primaryOut
    val primaryOut: Int,         // OUT 1..10 tej pozycji
    val wireName: String = "",   // nazwa do SET_INCFG
    val isLightsHi: Boolean = false // ten slot to HI z LIGHTS_H{n}
)

fun isNeutral(item: InputCfgItem): Boolean {
    if (item.mode != 1) return false
    return item.name.lowercase().contains("neutral")
}

fun isLightsName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("lights") || n.contains("light") ||
        n.contains("beam") || n.contains("hi_beam") || n.contains("low_beam")
}

fun isBrakeName(name: String): Boolean =
    name.lowercase().contains("brake")

fun parseLightsHi(name: String): Int? =
    Regex("""LIGHTS_H(\d+)""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10)

fun titleFor(item: InputCfgItem): String {
    val n = item.displayName()
    return when (item.mode) {
        2 -> "KIERUNEK L"
        3 -> "KIERUNEK P"
        6 -> "STARTER"
        else -> when {
            isNeutral(item) -> "NEUTRAL"
            isLightsName(item.name) -> "LIGHTS"
            isBrakeName(item.name) -> "BRAKE"
            n.isNotBlank() -> n
            else -> "IN %02d".format(item.inNum)
        }
    }
}

/**
 * Zawsze 10 pozycji OUT_01…OUT_10.
 * LIGHTS z LIGHTS_H{n} zajmuje dwa sloty (LOW + HI).
 */
fun buildRows(cfg: List<InputCfgItem>): List<ControlInRow> {
    // SENSOR (4) i DISABLED (5) poza listą sterowania; NEUTRAL zostaje (indicator OUT)
    val usable = cfg.filter { item ->
        when {
            item.mode == 4 || item.mode == 5 -> false
            else -> true
        }
    }
    val lights = usable.filter { isLightsName(it.name) && it.mode == 0 }
        .sortedBy { it.inNum }
    val brakes = usable.filter { isBrakeName(it.name) && it.mode == 1 }
        .sortedBy { it.inNum }
    val lc = lights.size

    data class Hit(
        val item: InputCfgItem,
        val isHi: Boolean
    )
    val hits = mutableMapOf<Int, Hit>()

    usable.forEach { item ->
        val primary = item.outNum.coerceIn(1, 10)
        if (!hits.containsKey(primary)) {
            hits[primary] = Hit(item, isHi = false)
        }
        if (isLightsName(item.name) && lc < 2) {
            val hi = parseLightsHi(item.name)
            if (hi != null && hi != primary && !hits.containsKey(hi)) {
                hits[hi] = Hit(item, isHi = true)
            }
        }
    }

    return (1..10).map { out ->
        val hit = hits[out]
        if (hit == null) {
            ControlInRow(
                inNum = 0,
                mode = -1,
                title = "—",
                subtitle = null,
                outNums = listOf(out),
                primaryOut = out
            )
        } else {
            val item = hit.item
            val sub = when {
                isLightsName(item.name) && lc >= 2 -> {
                    val idx = lights.indexOfFirst { it.inNum == item.inNum }
                    if (idx <= 0) "LOW BEAM" else "HI BEAM"
                }
                isLightsName(item.name) && hit.isHi -> "HI BEAM"
                isLightsName(item.name) && parseLightsHi(item.name) != null -> "LOW BEAM"
                isBrakeName(item.name) && brakes.size >= 2 -> {
                    val idx = brakes.indexOfFirst { it.inNum == item.inNum }
                    if (idx <= 0) "front" else "rear"
                }
                else -> null
            }
            ControlInRow(
                inNum = item.inNum,
                mode = item.mode,
                title = titleFor(item),
                subtitle = sub,
                outNums = listOf(out),
                primaryOut = out,
                wireName = item.name,
                isLightsHi = hit.isHi
            )
        }
    }
}

/**
 * Po DnD: zamiana funkcji między outFrom i outTo (1..10).
 * Zwraca nową listę InputCfgItem do autozapisu.
 */
fun swapOutAssignment(
    cfg: List<InputCfgItem>,
    outFrom: Int,
    outTo: Int
): List<InputCfgItem> {
    if (outFrom !in 1..10 || outTo !in 1..10 || outFrom == outTo) return cfg

    val rows = buildRows(cfg)
    val a = rows[outFrom - 1]
    val b = rows[outTo - 1]
    if (a.mode < 0 && b.mode < 0) return cfg

    // Pracujemy na kopii
    val list = cfg.map { it.copy() }.toMutableList()

    fun findIdx(inNum: Int) = list.indexOfFirst { it.inNum == inNum }

    fun setPrimaryOut(inNum: Int, newOut: Int) {
        val i = findIdx(inNum)
        if (i < 0) return
        list[i] = list[i].copy(outNum = newOut)
    }

    fun setLightsHi(inNum: Int, hiOut: Int) {
        val i = findIdx(inNum)
        if (i < 0) return
        val cur = list[i]
        val base = cur.name.substringBefore("_H").ifBlank { "LIGHTS" }
        val newName = if (hiOut in 1..10) "${base}_H$hiOut" else base
        list[i] = cur.copy(name = newName.take(15))
    }

    fun clearLightsHi(inNum: Int) {
        val i = findIdx(inNum)
        if (i < 0) return
        val cur = list[i]
        val base = cur.name.substringBefore("_H").ifBlank { "LIGHTS" }
        list[i] = cur.copy(name = base.take(15))
    }

    // Zbierz role na outFrom / outTo
    data class Role(val inNum: Int, val isHi: Boolean)
    fun roleOf(row: ControlInRow): Role? =
        if (row.mode < 0 || row.inNum <= 0) null else Role(row.inNum, row.isLightsHi)

    val ra = roleOf(a)
    val rb = roleOf(b)

    // Pomocniczo: aktualny HI danego inNum
    fun currentHi(inNum: Int): Int? {
        val i = findIdx(inNum)
        if (i < 0) return null
        return parseLightsHi(list[i].name)
    }

    fun currentPrimary(inNum: Int): Int {
        val i = findIdx(inNum)
        return if (i < 0) 0 else list[i].outNum.coerceIn(1, 10)
    }

    // Wykonaj zamianę ról
    when {
        ra == null && rb == null -> return cfg

        // A → puste B
        ra != null && rb == null -> {
            if (ra.isHi) {
                setLightsHi(ra.inNum, outTo)
            } else {
                setPrimaryOut(ra.inNum, outTo)
            }
        }

        // B → puste A (odwrotnie)
        ra == null && rb != null -> {
            if (rb.isHi) {
                setLightsHi(rb.inNum, outFrom)
            } else {
                setPrimaryOut(rb.inNum, outFrom)
            }
        }

        // Oba zajęte – zamiana
        ra != null && rb != null -> {
            if (!ra.isHi && !rb.isHi) {
                // dwa primary
                val pa = currentPrimary(ra.inNum)
                val pb = currentPrimary(rb.inNum)
                setPrimaryOut(ra.inNum, pb)
                setPrimaryOut(rb.inNum, pa)
            } else if (ra.isHi && rb.isHi) {
                // dwa HI (różne LIGHTS) – zamień numery H
                setLightsHi(ra.inNum, outTo)
                setLightsHi(rb.inNum, outFrom)
            } else if (ra.isHi && !rb.isHi) {
                // A=HI, B=primary
                val hiA = outFrom
                val primB = currentPrimary(rb.inNum)
                setLightsHi(ra.inNum, primB)
                setPrimaryOut(rb.inNum, hiA)
            } else if (!ra.isHi && rb.isHi) {
                val primA = currentPrimary(ra.inNum)
                val hiB = outTo
                setPrimaryOut(ra.inNum, hiB)
                setLightsHi(rb.inNum, primA)
            }
        }
    }

    // LOW i HI tego samego LIGHTS nie mogą mieć tego samego OUT
    list.forEachIndexed { i, item ->
        if (!isLightsName(item.name)) return@forEachIndexed
        val hi = parseLightsHi(item.name) ?: return@forEachIndexed
        if (hi == item.outNum) {
            // konflikt – przesuń HI na wolny
            val used = list.flatMap { it2 ->
                buildList {
                    add(it2.outNum)
                    parseLightsHi(it2.name)?.let { add(it) }
                }
            }.toSet()
            val free = (1..10).firstOrNull { it !in used && it != item.outNum } ?: hi
            val base = item.name.substringBefore("_H").ifBlank { "LIGHTS" }
            list[i] = item.copy(name = "${base}_H$free".take(15))
        }
    }

    return list
}

val COL_ORANGE = Color(0xFFFF9800)
val COL_GREEN = Color(0xFF4CAF50)
val COL_WHITE = Color(0xFFF5F5F5)
val COL_BLUE = Color(0xFF2196F3)
val COL_RED = Color(0xFFF44336)
val COL_CYAN = Color(0xFF00BCD4)
val COL_OFF = Color(0xFF2A2A2A)
val COL_UNUSED = Color(0xFF1A1A1A)

fun colorForRow(row: ControlInRow, outIndexInRow: Int): Color {
    if (row.mode < 0) return COL_UNUSED
    return when (row.mode) {
        2, 3 -> COL_ORANGE
        6 -> COL_GREEN
        else -> {
            val sub = row.subtitle?.lowercase().orEmpty()
            val title = row.title.lowercase()
            when {
                sub.contains("hi") -> COL_BLUE
                sub.contains("low") || title == "lights" -> COL_WHITE
                title.contains("brake") || sub.contains("front") || sub.contains("rear") -> COL_RED
                title.contains("neutral") -> COL_WHITE
                else -> COL_CYAN
            }
        }
    }
}

fun applyCurve(t: Float, curve: Int): Float {
    val x = t.coerceIn(0f, 1f)
    return when (curve) {
        2 -> if (x < 0.5f) 0f else 1f
        1 -> x * x * (3f - 2f * x)
        else -> x
    }
}

@Composable
fun rememberBlinkLevel(active: Boolean, fadeSpeed: Int, curve: Int): Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, fadeSpeed, curve) {
        if (!active) {
            while (level > 0.01f) {
                level = (level - 0.04f).coerceAtLeast(0f)
                delay(16)
            }
            level = 0f
            return@LaunchedEffect
        }
        var phase = 0f
        val stepMs = fadeSpeed.coerceIn(4, 60).toLong()
        while (true) {
            phase += 0.04f
            if (phase >= 2f) phase -= 2f
            val raw = if (phase <= 1f) phase else (2f - phase)
            level = applyCurve(raw, curve)
            delay(stepMs)
        }
    }
    return level
}
