package com.yamahub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay

/** Reprezentacja wiersza na ekranie sterowania - całkowicie pasywna */
data class ControlInRow(
    val inNum: Int,
    val functionId: Int,
    val title: String,
    val subtitle: String?,
    val primaryOut: Int
)

/** Helper do wyciągania portów świateł (tylko na potrzeby Dashboardu) */
fun lightsOutsFromCfg(cfg: List<InputCfgItem>): Pair<Int, Int> {
    val l1 = cfg.firstOrNull { it.functionId == FnKind.LIGHTS_1.id }
    val l2 = cfg.firstOrNull { it.functionId == FnKind.LIGHTS_2.id }
    
    return when {
        l1 != null && l2 != null -> l2.outNum to l1.outNum
        l1 != null -> l1.outSecondary to l1.outNum
        else -> 0 to 0
    }
}

/**
 * Buduje listę wierszy sterowania.
 * Jeden slot może wygenerować dwa wiersze (LIGHTS/LOW lub STARTER/KILL).
 */
fun buildRows(cfg: List<InputCfgItem>): List<ControlInRow> {
    val slots = cfg.map { it.toFnSlot() }
    val lc = slots.count { it.kind == FnKind.LIGHTS_1 || it.kind == FnKind.LIGHTS_2 }
    val bc = slots.count { it.kind == FnKind.BRAKE_1 || it.kind == FnKind.BRAKE_2 }
    val rowsByOut = mutableMapOf<Int, ControlInRow>()

    slots.forEach { s ->
        if (s.kind == FnKind.DISABLED) return@forEach

        if (s.outPrimary in 1..10) {
            rowsByOut.putIfAbsent(s.outPrimary, ControlInRow(
                inNum = s.inNum,
                functionId = s.kind.id,
                title = when (s.kind) {
                    FnKind.LIGHTS_1 -> "HI BEAM"
                    FnKind.STARTER -> "STARTER"
                    FnKind.KILL_SWITCH -> "KILL SWITCH"
                    else -> s.title(emptyList(), lc, bc)
                },
                subtitle = null,
                primaryOut = s.outPrimary
            ))
        }

        if (s.kind == FnKind.LIGHTS_1 && lc < 2 && s.outSecondary in 1..10) {
            rowsByOut.putIfAbsent(s.outSecondary, ControlInRow(
                inNum = s.inNum,
                functionId = FnKind.LIGHTS_2.id,
                title = "LOW BEAM",
                subtitle = null,
                primaryOut = s.outSecondary
            ))
        }

        if (s.kind == FnKind.STARTER && s.outSecondary in 1..10) {
            rowsByOut.putIfAbsent(s.outSecondary, ControlInRow(
                inNum = s.inNum,
                functionId = FnKind.KILL_SWITCH.id,
                title = "KILL SWITCH",
                subtitle = null,
                primaryOut = s.outSecondary
            ))
        }

    }
    return (1..10).map { out ->
        rowsByOut[out] ?: ControlInRow(
            inNum = 0,
            functionId = 0,
            title = "DISABLED",
            subtitle = null,
            primaryOut = out
        )
    }
}

/** 
 * Funkcja zamiany portów - teraz znacznie prostsza, 
 * bo operuje tylko na primaryOut. 
 */
fun swapOutAssignment(
    cfg: List<InputCfgItem>,
    outFrom: Int,
    outTo: Int
): List<InputCfgItem> {
    if (outFrom !in 1..10 || outTo !in 1..10 || outFrom == outTo) return cfg

    val list = cfg.map { it.copy() }.toMutableList()
    
    fun swapPort(port: Int): Int = when (port) {
        outFrom -> outTo
        outTo -> outFrom
        else -> port
    }

    for (i in list.indices) {
        val item = list[i]
        list[i] = item.copy(
            outNum = swapPort(item.outNum),
            outSecondary = swapPort(item.outSecondary)
        )
    }
    
    return list
}

val COL_ORANGE = Color(0xFFFF9800)
val COL_GREEN = Color(0xFF4CAF50)
val COL_WHITE = Color(0xFFF5F5F5)
val COL_BLUE = Color(0xFF2196F3)
val COL_RED = Color(0xFFF44336)
val COL_CYAN = Color(0xFF00BCD4)

/** Wybiera kolor dla wyjścia na podstawie jego functionId przesłanego z ESP */
fun colorForRow(row: ControlInRow): Color = when (row.functionId) {
    1, 2 -> COL_ORANGE // Kierunki
    3 -> COL_BLUE      // HI Beam
    4 -> COL_WHITE     // LOW Beam
    5, 6 -> COL_RED    // Hamulce
    7, 9 -> COL_GREEN  // Neutral, Starter
    10, 11, 13 -> COL_RED  // Oil, Fuel, Kill Switch
    12 -> COL_CYAN     // User defined
    else -> COL_CYAN
}

fun applyCurve(t: Float, curve: Int): Float {
    val x = t.coerceIn(0f, 1f)
    return when (curve) {
        2 -> if (x < 0.5f) 0f else 1f
        1 -> x * x * (3f - 2f * x)
        else -> x
    }
}

data class BlinkPair(val left: Float, val right: Float)

@Composable
fun rememberBlinkPair(
    leftActive: Boolean,
    rightActive: Boolean,
    hazardOn: Boolean,
    fadeSpeed: Int,
    curve: Int
): BlinkPair {
    val leftSolo = rememberBlinkLevel(leftActive && !hazardOn, fadeSpeed, curve)
    val rightSolo = rememberBlinkLevel(rightActive && !hazardOn, fadeSpeed, curve)
    val hazardLevel = rememberBlinkLevel(hazardOn, fadeSpeed, curve)
    return BlinkPair(
        left = if (hazardOn) hazardLevel else leftSolo,
        right = if (hazardOn) hazardLevel else rightSolo
    )
}

@Composable
fun rememberBlinkLevel(active: Boolean, fadeSpeed: Int, curve: Int): Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, fadeSpeed, curve) {
        if (!active) {
            while (level > 0.01f) {
                level = (level - 0.08f).coerceAtLeast(0f)
                delay(32)
            }
            level = 0f
            return@LaunchedEffect
        }
        var phase = 0f
        val stepMs = maxOf(40L, fadeSpeed.coerceIn(4, 60).toLong())
        val phaseStep = 0.04f * (stepMs / 12f).coerceIn(1f, 4f)
        while (true) {
            phase += phaseStep
            if (phase >= 2f) phase -= 2f
            val raw = if (phase <= 1f) phase else (2f - phase)
            level = applyCurve(raw, curve)
            delay(stepMs)
        }
    }
    return level
}
