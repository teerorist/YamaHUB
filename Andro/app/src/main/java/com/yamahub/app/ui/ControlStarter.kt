package com.yamahub.app.ui

import com.yamahub.app.BleManager

/**
 * STARTER – jak fizyczny IN_10 (hold).
 * Stan gotowosci pochodzi z interlocku na ESP.
 */
object ControlStarter {
    fun canStart(ble: BleManager): Boolean = ble.starterEnabled

    fun onDown(ble: BleManager, inNum: Int) {
        if (!canStart(ble)) return
        if (inNum in 1..10) ble.sendCommand("IN:$inNum:1")
    }

    fun onUp(ble: BleManager, inNum: Int) {
        // release zawsze – nawet gdy neutral zgasł w trakcie hold
        if (inNum in 1..10) ble.sendCommand("IN:$inNum:0")
    }
}
