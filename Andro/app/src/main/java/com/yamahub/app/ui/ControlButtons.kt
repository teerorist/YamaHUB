package com.yamahub.app.ui

import com.yamahub.app.BleManager

/**
 * BUTTON (toggle) i MOMENT (np. BRAKE) – bez NEUTRAL (tylko podgląd).
 */
object ControlButtons {

    fun momentDown(ble: BleManager, row: ControlInRow) {
        if (row.title == "NEUTRAL") return
        row.outNums.forEach { ble.setOutput(it, true) }
    }

    fun momentUp(ble: BleManager, row: ControlInRow) {
        if (row.title == "NEUTRAL") return
        row.outNums.forEach { ble.setOutput(it, false) }
    }

    /** Toggle na release (short i long – to samo, aż pojawią się osobne gesty). */
    fun toggleUp(
        ble: BleManager,
        row: ControlInRow,
        outLevel: (Int) -> Float
    ) {
        val primary = row.outNums.firstOrNull() ?: return
        val on = outLevel(primary) > 0.5f
        row.outNums.forEach { ble.setOutput(it, !on) }
    }
}
