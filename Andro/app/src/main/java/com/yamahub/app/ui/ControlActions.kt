package com.yamahub.app.ui

import com.yamahub.app.BleManager
import kotlinx.coroutines.CoroutineScope

/**
 * ControlScreen: stany i sterowanie po OUT_XX (bez logiki IN).
 * IN press/release (Dashboard test / fizyczny) → ControlBlinkers.
 */
object ControlActions {

    fun reset() = ControlBlinkers.reset()

    fun onDown(
        ble: BleManager,
        row: ControlInRow,
        leftActive: Boolean,
        rightActive: Boolean,
        hazardOn: Boolean,
        scope: CoroutineScope
    ) {
        when (row.mode) {
            6 -> ControlStarter.onDown(ble, row.inNum)
            1 -> ControlButtons.momentDown(ble, row)
            else -> {}
        }
    }

    fun onUp(
        ble: BleManager,
        row: ControlInRow,
        heldMs: Long,
        leftActive: Boolean,
        rightActive: Boolean,
        hazardOn: Boolean,
        outLevel: (Int) -> Float
    ) {
        when (row.mode) {
            6 -> ControlStarter.onUp(ble, row.inNum)
            1 -> ControlButtons.momentUp(ble, row)
            else -> ControlButtons.toggleUp(ble, row, outLevel)
        }
    }
}
