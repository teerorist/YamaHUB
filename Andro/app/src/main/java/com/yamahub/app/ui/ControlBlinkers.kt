package com.yamahub.app.ui

import com.yamahub.app.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Logika kierunkowskazów (ControlScreen tylko woła onDown/onUp).
 *
 * OFF + short  → N (LEFT:1 / RIGHT:1)
 * OFF + long   → N od razu, po 400 ms trzymania → NS (LEFT:2 / RIGHT:2)
 * aktywny + short → LEFT:1 / RIGHT:1 (ESP: restart N / off NS)
 * aktywny + long  → OFF
 * L+P naraz       → HAZARD
 */
object ControlBlinkers {
    private const val LONG_MS = 400L

    private var leftHeld = false
    private var rightHeld = false
    private var leftWasActive = false
    private var rightWasActive = false
    private var bothHeld = false
    private var leftProvisional = false
    private var rightProvisional = false
    private var leftJob: Job? = null
    private var rightJob: Job? = null

    fun reset() {
        leftJob?.cancel(); rightJob?.cancel()
        leftHeld = false; rightHeld = false
        leftWasActive = false; rightWasActive = false
        bothHeld = false
        leftProvisional = false; rightProvisional = false
    }

    fun onLeftDown(
        ble: BleManager,
        leftActive: Boolean,
        rightActive: Boolean,
        hazardOn: Boolean,
        scope: CoroutineScope
    ) {
        leftHeld = true
        leftWasActive = leftActive
        leftProvisional = false
        leftJob?.cancel()

        if (rightHeld) {
            bothHeld = true
            leftProvisional = false
            if (!hazardOn) ble.setHazard(true)
            return
        }
        if (hazardOn) return

        if (!leftActive) {
            if (rightActive) ble.sendCommand("RIGHT:0")
            ble.sendCommand("LEFT:1")
            leftProvisional = true
            leftJob = scope.launch {
                delay(LONG_MS)
                if (leftHeld && leftProvisional && !bothHeld) {
                    ble.sendCommand("LEFT:2")
                    leftProvisional = false
                }
            }
        }
    }

    fun onLeftUp(ble: BleManager, heldMs: Long, hazardOn: Boolean) {
        val wasBoth = bothHeld
        val wasProv = leftProvisional
        val isLong = heldMs >= LONG_MS
        leftHeld = false
        leftProvisional = false
        leftJob?.cancel()
        if (!rightHeld) bothHeld = false

        if (wasBoth) return
        if (hazardOn) {
            ble.setHazard(false)
            return
        }
        when {
            leftWasActive -> {
                if (isLong) ble.sendCommand("LEFT:0")
                else ble.sendCommand("LEFT:1")
            }
            wasProv && isLong -> ble.sendCommand("LEFT:2")
            else -> {}
        }
    }

    fun onRightDown(
        ble: BleManager,
        leftActive: Boolean,
        rightActive: Boolean,
        hazardOn: Boolean,
        scope: CoroutineScope
    ) {
        rightHeld = true
        rightWasActive = rightActive
        rightProvisional = false
        rightJob?.cancel()

        if (leftHeld) {
            bothHeld = true
            rightProvisional = false
            if (!hazardOn) ble.setHazard(true)
            return
        }
        if (hazardOn) return

        if (!rightActive) {
            if (leftActive) ble.sendCommand("LEFT:0")
            ble.sendCommand("RIGHT:1")
            rightProvisional = true
            rightJob = scope.launch {
                delay(LONG_MS)
                if (rightHeld && rightProvisional && !bothHeld) {
                    ble.sendCommand("RIGHT:2")
                    rightProvisional = false
                }
            }
        }
    }

    fun onRightUp(ble: BleManager, heldMs: Long, hazardOn: Boolean) {
        val wasBoth = bothHeld
        val wasProv = rightProvisional
        val isLong = heldMs >= LONG_MS
        rightHeld = false
        rightProvisional = false
        rightJob?.cancel()
        if (!leftHeld) bothHeld = false

        if (wasBoth) return
        if (hazardOn) {
            ble.setHazard(false)
            return
        }
        when {
            rightWasActive -> {
                if (isLong) ble.sendCommand("RIGHT:0")
                else ble.sendCommand("RIGHT:1")
            }
            wasProv && isLong -> ble.sendCommand("RIGHT:2")
            else -> {}
        }
    }
}
