package com.yamahub.app.ui

import com.yamahub.app.BleManager

/**
 * Kierunki: apka tylko wciska/puszcza IN_XX.
 * Short/long/HAZARD/N/NS liczy ESP (jak fizyczny przycisk).
 */
object ControlBlinkers {
    fun reset() {}

    fun onDown(ble: BleManager, inNum: Int) {
        if (inNum !in 1..10) return
        ble.sendCommand("IN:$inNum:1")
    }

    fun onUp(ble: BleManager, inNum: Int) {
        if (inNum !in 1..10) return
        ble.sendCommand("IN:$inNum:0")
    }
}
