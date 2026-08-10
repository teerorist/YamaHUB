package com.yamahub.app.ui

import com.yamahub.app.InputCfgItem
import com.yamahub.app.displayName

object Mode {
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
enum class FnKind {
    LEFT, RIGHT, LIGHTS, BRAKE, NEUTRAL, STARTER, BUTTON, SENSOR, DISABLED
}

var nextSlotId = 1L

data class FnSlot(
    val kind: FnKind,
    val variant: Int = 1,
    val outNum: Int = 1,
    /** Drugi OUT tylko przy 1× LIGHTS (LOW = outNum, HI = outNum2). 0 = brak. */
    val outNum2: Int = 0,
    val customName: String = "",
    val id: Long = nextSlotId++
)

fun FnSlot.title(): String = when (kind) {
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

fun FnSlot.subtitle(lightsCount: Int, brakesCount: Int): String? = when (kind) {
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
fun FnSlot.isFixed(): Boolean = when (kind) {
    FnKind.LEFT, FnKind.RIGHT, FnKind.NEUTRAL, FnKind.STARTER -> true
    FnKind.LIGHTS, FnKind.BRAKE -> variant <= 1
    else -> false
}

fun FnSlot.hasOutPicker(): Boolean = when (kind) {
    FnKind.DISABLED, FnKind.SENSOR -> false
    else -> true
}

fun FnSlot.toMode(): Int = when (kind) {
    FnKind.LEFT -> Mode.LEFT
    FnKind.RIGHT -> Mode.RIGHT
    FnKind.LIGHTS, FnKind.BUTTON -> Mode.TOGGLE
    FnKind.BRAKE, FnKind.NEUTRAL -> Mode.MOMENT
    FnKind.SENSOR -> Mode.SENSOR
    FnKind.DISABLED -> Mode.DISABLED
    FnKind.STARTER -> Mode.STARTER
}

fun FnSlot.toWireName(lightsCount: Int): String = when (kind) {
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

/** Nazwa bez podkreśleń – lokalny helper na String. */
private fun prettyName(raw: String): String =
    raw.replace('_', ' ').trim()

fun InputCfgItem.toFnSlot(): FnSlot {
    val n = name.trim()
    val nd = prettyName(n).lowercase()
    val pretty = this.displayName()
    val o = outNum.coerceIn(1, 10)
    return when (mode) {
        Mode.LEFT -> FnSlot(kind = FnKind.LEFT, outNum = o)
        Mode.RIGHT -> FnSlot(kind = FnKind.RIGHT, outNum = o)
        Mode.SENSOR -> FnSlot(
            kind = FnKind.SENSOR,
            outNum = 0,
            customName = pretty.ifBlank { "SENSOR" }
        )
        Mode.DISABLED -> FnSlot(kind = FnKind.DISABLED, outNum = o)
        Mode.STARTER -> FnSlot(kind = FnKind.STARTER, outNum = o)
        Mode.MOMENT -> when {
            nd.contains("neutral") -> FnSlot(kind = FnKind.NEUTRAL, outNum = o)
            nd.contains("brake") && (nd.contains("rear") || nd.contains("2")) ->
                FnSlot(kind = FnKind.BRAKE, variant = 2, outNum = o)
            nd.contains("starter") -> FnSlot(kind = FnKind.STARTER, outNum = o)
            else -> FnSlot(kind = FnKind.BRAKE, variant = 1, outNum = o)
        }
        else -> when {
            nd.contains("starter") -> FnSlot(kind = FnKind.STARTER, outNum = o)
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
                FnSlot(kind = FnKind.LIGHTS, variant = v, outNum = o, outNum2 = hi)
            }
            else -> FnSlot(
                kind = FnKind.BUTTON,
                outNum = o,
                customName = pretty.ifBlank { "BUTTON" }
            )
        }
    }
}

fun defaultSlots(): List<FnSlot> = listOf(
    FnSlot(kind = FnKind.LEFT, outNum = 1),
    FnSlot(kind = FnKind.RIGHT, outNum = 5),
    FnSlot(kind = FnKind.LIGHTS, variant = 1, outNum = 3, outNum2 = 7),
    FnSlot(kind = FnKind.BRAKE, variant = 1, outNum = 4),
    FnSlot(kind = FnKind.NEUTRAL, outNum = 6),
    FnSlot(kind = FnKind.STARTER, outNum = 10),
    FnSlot(kind = FnKind.BUTTON, outNum = 2, customName = "BUTTON"),
    FnSlot(kind = FnKind.BUTTON, outNum = 8, customName = "BUTTON"),
    FnSlot(kind = FnKind.DISABLED, outNum = 9),
    FnSlot(kind = FnKind.DISABLED, outNum = 1)
)

/** Uzupełnia brakujące wymagane funkcje; nie rusza istniejącego STARTER / LIGHTS OUT. */
fun normalizeSlots(raw: List<FnSlot>): List<FnSlot> {
    val list = raw.take(10).toMutableList()
    while (list.size < 10) {
        list.add(FnSlot(kind = FnKind.DISABLED, outNum = (list.size + 1).coerceAtMost(10)))
    }

    fun ensureOne(kind: FnKind, variant: Int = 1, outFallback: Int) {
        if (list.none { it.kind == kind && it.variant == variant }) {
            val free = list.indexOfFirst {
                it.kind == FnKind.DISABLED || it.kind == FnKind.BUTTON
            }
            if (free >= 0) {
                list[free] = FnSlot(kind = kind, variant = variant, outNum = outFallback)
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
                if (leftSeen) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
                else leftSeen = true
            }
            FnKind.RIGHT -> {
                if (rightSeen) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
                else rightSeen = true
            }
            FnKind.LIGHTS -> {
                lights++
                if (lights > 2) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
                else list[i] = s.copy(variant = lights)
            }
            FnKind.BRAKE -> {
                brakes++
                if (brakes > 2) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
                else list[i] = s.copy(variant = brakes)
            }
            FnKind.NEUTRAL -> {
                if (neutralSeen) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
                else neutralSeen = true
            }
            FnKind.STARTER -> {
                if (starterSeen) list[i] = FnSlot(kind = FnKind.DISABLED, outNum = s.outNum)
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
