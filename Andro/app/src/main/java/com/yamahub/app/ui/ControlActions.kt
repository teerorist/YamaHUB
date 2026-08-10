package com.yamahub.app.ui

import com.yamahub.app.BleManager
import kotlinx.coroutines.CoroutineScope

/**
 * Router gestów z ControlScreen → właściwy moduł funkcji.
 * ControlScreen NIE zna LEFT:1 / toggle / starter – tylko woła onDown/onUp.
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
            2 -> ControlBlinkers.onLeftDown(ble, leftActive, rightActive, hazardOn, scope)
            3 -> ControlBlinkers.onRightDown(ble, leftActive, rightActive, hazardOn, scope)
            6 -> ControlStarter.onDown(ble)
            1 -> ControlButtons.momentDown(ble, row)
            0 -> if (row.title == "LIGHTS") {
                ControlLights.onDown(ble, row)
            } else {
                /* toggle – akcja na up */
            }
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
            2 -> ControlBlinkers.onLeftUp(ble, heldMs, hazardOn)
            3 -> ControlBlinkers.onRightUp(ble, heldMs, hazardOn)
            6 -> ControlStarter.onUp(ble)
            1 -> ControlButtons.momentUp(ble, row)
            0 -> if (row.title == "LIGHTS") {
                ControlLights.onUp(ble, row, heldMs, outLevel)
            } else {
                ControlButtons.toggleUp(ble, row, outLevel)
            }
            else -> {}
        }
    }
}
