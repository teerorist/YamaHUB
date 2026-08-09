package com.yamahub.app.ui

import androidx.compose.ui.graphics.Color
import com.yamahub.app.InputCfgItem

data class ControlInRow(
    val inNum: Int,
    val mode: Int,
    val title: String,
    val subtitle: String?,
    val outNums: List<Int>
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

fun titleFor(item: InputCfgItem): String {
    val n = displayName(item.name)
    return when (item.mode) {
        2 -> "KIERUNEK L"
        3 -> "KIERUNEK P"
        6 -> "STARTER"
        else -> when {
            isLightsName(item.name) -> "LIGHTS"
            isBrakeName(item.name) -> "BRAKE"
            n.isNotBlank() -> n
            else -> "IN_${item.inNum}"
        }
    }
}

fun subtitleFor(
    item: InputCfgItem,
    lights: List<InputCfgItem>,
    brakes: List<InputCfgItem>
): String? {
    if (isLightsName(item.name) && lights.size >= 2) {
        val idx = lights.indexOfFirst { it.inNum == item.inNum }
        return if (idx <= 0) "LOW BEAM" else "HI BEAM"
    }
    if (isBrakeName(item.name) && brakes.size >= 2) {
        val idx = brakes.indexOfFirst { it.inNum == item.inNum }
        return if (idx <= 0) "front" else "rear"
    }
    return null
}

fun outNumsFor(item: InputCfgItem, lightsCount: Int): List<Int> {
    val primary = item.outNum.coerceIn(1, 10)
    if (isLightsName(item.name) && lightsCount < 2) {
        val hi = Regex("""LIGHTS_H(\d+)""", RegexOption.IGNORE_CASE)
            .find(item.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.coerceIn(1, 10)
        if (hi != null && hi != primary) return listOf(primary, hi)
    }
    return listOf(primary)
}

fun buildRows(cfg: List<InputCfgItem>): List<ControlInRow> {
    val usable = cfg.filter { item ->
        when {
            item.mode == 4 || item.mode == 5 -> false
            isNeutral(item) -> false
            else -> true
        }
    }
    val lights = usable.filter { isLightsName(it.name) && it.mode == 0 }
        .sortedBy { it.inNum }
    val brakes = usable.filter { isBrakeName(it.name) && it.mode == 1 }
        .sortedBy { it.inNum }
    val lc = lights.size

    return usable
        .sortedBy { it.outNum.coerceIn(1, 10) }
        .map { item ->
            ControlInRow(
                inNum = item.inNum,
                mode = item.mode,
                title = titleFor(item),
                subtitle = subtitleFor(item, lights, brakes),
                outNums = outNumsFor(item, lc)
            )
        }
}

/** Kolory OUT – te same co na ESP. */
val COL_ORANGE = Color(0xFFFF9800)
val COL_GREEN = Color(0xFF4CAF50)
val COL_WHITE = Color(0xFFF5F5F5)
val COL_BLUE = Color(0xFF2196F3)
val COL_RED = Color(0xFFF44336)
val COL_CYAN = Color(0xFF00BCD4)
val COL_OFF = Color(0xFF2A2A2A)

fun colorForRow(row: ControlInRow, outIndexInRow: Int): Color {
    return when (row.mode) {
        2, 3 -> COL_ORANGE
        6 -> COL_GREEN
        else -> {
            val sub = row.subtitle?.lowercase().orEmpty()
            val title = row.title.lowercase()
            when {
                sub.contains("hi") || title.contains("hi beam") -> COL_BLUE
                sub.contains("low") ||
                    (title == "lights" && row.outNums.size == 2 && outIndexInRow == 0) -> COL_WHITE
                title == "lights" && row.outNums.size == 2 && outIndexInRow == 1 -> COL_BLUE
                title == "lights" && row.outNums.size == 1 -> COL_WHITE
                title.contains("brake") || sub.contains("front") || sub.contains("rear") -> COL_RED
                else -> COL_CYAN
            }
        }
    }
}

/** Krzywa fade jak na ESP: 0 liniowa, 1 płynna, 2 ostra. */
fun applyCurve(t: Float, curve: Int): Float {
    val x = t.coerceIn(0f, 1f)
    return when (curve) {
        2 -> if (x < 0.5f) 0f else 1f
        1 -> {
            // smoothstep
            x * x * (3f - 2f * x)
        }
        else -> x
    }
}

/**
 * Lokalna animacja trójkąta 0→1→0 jak mruganie kierunków.
 * stepMs ≈ cfg.fadeSpeed (ms między skokami jasności).
 */
@Composable
fun rememberBlinkLevel(active: Boolean, fadeSpeed: Int, curve: Int): Float {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, fadeSpeed, curve) {
        if (!active) {
            // dokończ fade-out
            while (level > 0.01f) {
                level = (level - 0.04f).coerceAtLeast(0f)
                delay(16)
            }
            level = 0f
            return@LaunchedEffect
        }
        var phase = 0f // 0..2 (w górę + w dół)
        val stepMs = fadeSpeed.coerceIn(4, 60).toLong()
        // pełny cykl ~ (255/8)*fadeSpeed*2 ms na ESP → tu ciągły trójkąt
        while (true) {
            // phase 0..1 up, 1..2 down
            phase += 0.04f
            if (phase >= 2f) phase -= 2f
            val raw = if (phase <= 1f) phase else (2f - phase)
            level = applyCurve(raw, curve)
            delay(stepMs)
        }
    }
    return level
}

