package com.yamahub.app.ui

import com.yamahub.app.BleManager

/**
 * LIGHTS na ControlScreen.
 * short → toggle LOW (pierwszy OUT wiersza)
 * long  → toggle HI  (drugi OUT, jeśli jest; inaczej LOW)
 *
 * Docelowo gesty z InputSettings – tu domyślne zachowanie.
 */
object ControlLights {
    fun onDown(ble: BleManager, row: ControlInRow) {
        // stan zmieniamy na up (short/long)
    }

    fun onUp(
        ble: BleManager,
        row: ControlInRow,
        heldMs: Long,
        outLevel: (Int) -> Float
    ) {
        val outs = row.outNums
        if (outs.isEmpty()) return
        val isLong = heldMs >= 400L
        val target = when {
            isLong && outs.size >= 2 -> outs[1]
            else -> outs[0]
        }
        val on = outLevel(target) > 0.5f
        ble.setOutput(target, !on)
    }
}
