package com.yamahub.app.ui

import com.yamahub.app.InputCfgItem

/** Kategorie wejść zsynchronizowane z ESP */
object Mode {
    const val BUTTON = 0
    const val SENSOR = 1
    const val DISABLED = 2
}

/** Funkcje logiczne zdefiniowane w ESP (FunctionID) */
enum class FnKind(val id: Int) {
    LEFT(1),
    RIGHT(2),
    LIGHTS_1(3),
    LIGHTS_2(4),
    BRAKE_1(5),
    BRAKE_2(6),
    NEUTRAL(7),
    CLUTCH(8),
    STARTER(9),
    OIL(10),
    FUEL(11),
    USER(12),
    DISABLED(0)
}

enum class FnCategory { BUTTON, SENSOR, DISABLED }

fun modeToCategory(mode: Int): FnCategory = when (mode) {
    Mode.SENSOR -> FnCategory.SENSOR
    Mode.DISABLED -> FnCategory.DISABLED
    else -> FnCategory.BUTTON
}

var nextSlotId = 1L

data class FnSlot(
    val inNum: Int,
    val category: FnCategory,
    val kind: FnKind,
    /** Główne wyjście (HI Beam / Blinker / Brake / Starter / User Out) */
    val outPrimary: Int = 0,
    /** Pomocnicze wyjście (LOW Beam w 1x LIGHTS) */
    val outSecondary: Int = 0,
    /** Czy funkcja steruje wyjściem (dla USER) */
    val outputEnabled: Boolean = false,
    /** Port wyjściowy sterowany przez funkcję (dla USER) */
    val outputIndex: Int = 0,
    val customName: String = "",
    val isFixed: Boolean = false,
    val isOutLocked: Boolean = false,
    val id: Long = nextSlotId++
)

/** Czy funkcja jest systemowa (nie można zmienić jej rodzaju, tylko port OUT) */
fun FnSlot.isSystem(): Boolean = kind != FnKind.USER && kind != FnKind.DISABLED

/** Pola, które lecą na ESP — bez id UI. */
fun FnSlot.samePersist(other: FnSlot?): Boolean {
    if (other == null) return false
    return category == other.category &&
        kind == other.kind &&
        outPrimary == other.outPrimary &&
        outSecondary == other.outSecondary &&
        outputEnabled == other.outputEnabled &&
        outputIndex == other.outputIndex &&
        customName == other.customName
}

fun kindPickerLabel(kind: FnKind, hubLabel: String? = null): String = when (kind) {
    FnKind.LIGHTS_2 -> "LOW BEAM"
    FnKind.BRAKE_2 -> "BRAKE"
    else -> hubLabel ?: kind.name
}

/** Tytuł na liście slotów — 1x/2x LIGHTS i BRAKE. */
fun FnSlot.title(
    modeDefinitions: List<com.yamahub.app.ModeDefinition> = emptyList(),
    lightsCount: Int = 1,
    brakesCount: Int = 1
): String {
    if (category == FnCategory.DISABLED || kind == FnKind.DISABLED) return "DISABLED"
    return when (kind) {
        FnKind.USER -> customName.ifBlank { category.name }
        FnKind.LIGHTS_1 -> if (lightsCount < 2) "LIGHTS" else "HI BEAM"
        FnKind.LIGHTS_2 -> "LOW BEAM"
        FnKind.BRAKE_1 -> if (brakesCount < 2) "BRAKES" else "FRONT BRAKE"
        FnKind.BRAKE_2 -> "REAR BRAKE"
        else -> modeDefinitions.find { it.id == kind.id && it.category == category.ordinal }?.label
            ?: kind.name
    }
}

/** Tekst pod tytułem: pokazuje tylko porty OUT */
fun FnSlot.subtitle(lightsCount: Int): String? {
    if (kind == FnKind.DISABLED) return null
    
    // Dla świateł w trybie 1x pokazujemy oba porty
    if (kind == FnKind.LIGHTS_1 && lightsCount < 2) {
        val lowStr = if (outSecondary > 0) " · OUT %02d".format(outSecondary) else ""
        return "OUT %02d%s".format(outPrimary, lowStr)
    }
    
    if (kind == FnKind.USER) {
        val on = category == FnCategory.BUTTON || outputEnabled
        return if (on && outputIndex > 0) "OUT %02d".format(outputIndex) else null
    }
    if (category == FnCategory.SENSOR && kind != FnKind.BRAKE_1 && kind != FnKind.BRAKE_2) {
        return if (outputEnabled && outPrimary > 0) "OUT %02d".format(outPrimary) else null
    }

    // Dla reszty pokazujemy primary jeśli > 0
    return if (outPrimary > 0) "OUT %02d".format(outPrimary) else null
}

/** Mapowanie surowej ramki z ESP na obiekt domeny Androida */
fun InputCfgItem.toFnSlot(): FnSlot {
    val category = modeToCategory(this.mode)
    val kind = FnKind.entries.find { it.id == this.functionId } ?: FnKind.DISABLED
    
    return FnSlot(
        inNum = this.inNum,
        category = category,
        kind = kind,
        outPrimary = this.outNum,
        outSecondary = this.outSecondary,
        outputEnabled = this.outputEnabled,
        outputIndex = this.outputNum,
        customName = this.name.replace("_", " ").trim(),
        isFixed = this.isFixed,
        isOutLocked = this.isOutLocked
    )
}

/** Zmiana funkcji na wolnym slocie. Systemowych nie rusza. */
fun applyKindChange(slots: List<FnSlot>, index: Int, kind: FnKind): List<FnSlot> {
    val list = slots.toMutableList()
    val old = list.getOrNull(index) ?: return slots
    if (old.isFixed) return slots
    if (kind != FnKind.USER && kind != FnKind.DISABLED &&
        list.any { it.id != old.id && it.kind == kind }
    ) return slots

    var slot = old.copy(kind = kind, customName = "")
    slot = slot.copy(
        category = when (kind) {
            FnKind.LEFT, FnKind.RIGHT, FnKind.LIGHTS_1, FnKind.LIGHTS_2, FnKind.STARTER ->
                FnCategory.BUTTON
            FnKind.BRAKE_1, FnKind.BRAKE_2, FnKind.NEUTRAL, FnKind.CLUTCH, FnKind.OIL, FnKind.FUEL ->
                FnCategory.SENSOR
            FnKind.DISABLED -> FnCategory.DISABLED
            FnKind.USER -> old.category
        },
        isOutLocked = kind == FnKind.BRAKE_2
    )

    if (kind == FnKind.LIGHTS_2) {
        val l1 = list.indexOfFirst { it.kind == FnKind.LIGHTS_1 }
        if (l1 >= 0 && list[l1].outSecondary in 1..10) {
            slot = slot.copy(outPrimary = list[l1].outSecondary)
            list[l1] = list[l1].copy(outSecondary = 0)
        }
    }
    if (kind == FnKind.BRAKE_2) {
        val b1 = list.firstOrNull { it.kind == FnKind.BRAKE_1 }
        if (b1 != null) slot = slot.copy(outPrimary = b1.outPrimary)
    }
    if (old.kind == FnKind.LIGHTS_2 && kind != FnKind.LIGHTS_2) {
        val l1 = list.indexOfFirst { it.kind == FnKind.LIGHTS_1 }
        if (l1 >= 0 && list[l1].outSecondary !in 1..10 && old.outPrimary in 1..10) {
            list[l1] = list[l1].copy(outSecondary = old.outPrimary)
        }
    }
    if (kind == FnKind.DISABLED) {
        slot = slot.copy(
            outPrimary = 0,
            outSecondary = 0,
            outputEnabled = false,
            isOutLocked = false
        )
    }
    list[index] = slot
    return list
}

/** Uzupełnia listę do 10 slotów */
fun normalizeSlots(raw: List<FnSlot>): List<FnSlot> {
    val list = raw.take(10).toMutableList()
    while (list.size < 10) {
        val n = list.size + 1
        list.add(FnSlot(inNum = n, category = FnCategory.DISABLED, kind = FnKind.DISABLED))
    }
    return list
}
