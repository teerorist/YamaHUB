package com.yamahub.app.ui

import com.yamahub.app.BleManager

/**
 * STARTER – jak fizyczny IN_10 (hold).
 * Działa tylko gdy NEUTRAL = 1 (na razie: DashboardTestState.neutral).
 */
object ControlStarter {
    fun canStart(): Boolean = DashboardTestState.neutral

    fun onDown(ble: BleManager) {
        if (!canStart()) return
        ble.sendCommand("IN10:1")
    }

    fun onUp(ble: BleManager) {
        // release zawsze – nawet gdy neutral zgasł w trakcie hold
        ble.sendCommand("IN10:0")
    }
}
