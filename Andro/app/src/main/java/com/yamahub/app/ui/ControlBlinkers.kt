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
    private const val LONG_MS = 500L
    private const val SYNC_WINDOW_MS = 150L

    private var leftHeld = false
    private var rightHeld = false
    private var leftWasActive = false
    private var rightWasActive = false
    private var bothHeld = false
    private var leftProvisional = false
    private var rightProvisional = false
    private var leftNsTriggered = false
    private var rightNsTriggered = false
    private var leftJob: Job? = null
    private var rightJob: Job? = null

    fun reset() {
        leftJob?.cancel(); rightJob?.cancel()
        leftHeld = false; rightHeld = false
        leftWasActive = false; rightWasActive = false
        bothHeld = false
        leftProvisional = false; rightProvisional = false
        leftNsTriggered = false; rightNsTriggered = false
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
        leftNsTriggered = false
        
        // If we were waiting for a right blink to start, cancel it and start hazard
        if (rightJob != null && rightProvisional) {
            rightJob?.cancel()
            rightJob = null
            bothHeld = true
            ble.setHazard(true)
            return
        }

        leftJob?.cancel()

        if (rightHeld) {
            bothHeld = true
            leftProvisional = false
            if (!hazardOn) ble.setHazard(true)
            return
        }
        if (hazardOn) return

        if (!leftActive) {
            leftProvisional = true
            leftJob = scope.launch {
                // SYNC WINDOW: Wait a bit to see if RIGHT is also pressed
                delay(SYNC_WINDOW_MS)
                
                if (rightActive) ble.sendCommand("RIGHT:0")
                ble.sendCommand("LEFT:1")
                
                // Continue waiting for LONG press (NS mode)
                delay(LONG_MS - SYNC_WINDOW_MS)
                if (leftHeld && leftProvisional && !bothHeld) {
                    ble.sendCommand("LEFT:2")
                    leftNsTriggered = true
                    leftProvisional = false
                }
            }
        }
    }

    fun onLeftUp(ble: BleManager, heldMs: Long, hazardOn: Boolean) {
        val wasBoth = bothHeld
        val wasNs = leftNsTriggered
        val isLong = heldMs >= LONG_MS
        leftHeld = false
        leftProvisional = false
        leftNsTriggered = false
        leftJob?.cancel()
        leftJob = null
        if (!rightHeld) bothHeld = false

        if (wasBoth) return
        if (hazardOn) {
            ble.setHazard(false)
            return
        }
        
        if (wasNs) return 

        when {
            leftWasActive -> {
                if (isLong) ble.sendCommand("LEFT:0")
                else ble.sendCommand("LEFT:1")
            }
            heldMs >= SYNC_WINDOW_MS && isLong -> ble.sendCommand("LEFT:2")
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
        rightNsTriggered = false

        // If we were waiting for a left blink to start, cancel it and start hazard
        if (leftJob != null && leftProvisional) {
            leftJob?.cancel()
            leftJob = null
            bothHeld = true
            ble.setHazard(true)
            return
        }

        rightJob?.cancel()

        if (leftHeld) {
            bothHeld = true
            rightProvisional = false
            if (!hazardOn) ble.setHazard(true)
            return
        }
        if (hazardOn) return

        if (!rightActive) {
            rightProvisional = true
            rightJob = scope.launch {
                // SYNC WINDOW: Wait a bit to see if LEFT is also pressed
                delay(SYNC_WINDOW_MS)
                
                if (leftActive) ble.sendCommand("LEFT:0")
                ble.sendCommand("RIGHT:1")
                
                // Continue waiting for LONG press (NS mode)
                delay(LONG_MS - SYNC_WINDOW_MS)
                if (rightHeld && rightProvisional && !bothHeld) {
                    ble.sendCommand("RIGHT:2")
                    rightNsTriggered = true
                    rightProvisional = false
                }
            }
        }
    }

    fun onRightUp(ble: BleManager, heldMs: Long, hazardOn: Boolean) {
        val wasBoth = bothHeld
        val wasNs = rightNsTriggered
        val isLong = heldMs >= LONG_MS
        rightHeld = false
        rightProvisional = false
        rightNsTriggered = false
        rightJob?.cancel()
        rightJob = null
        if (!leftHeld) bothHeld = false

        if (wasBoth) return
        if (hazardOn) {
            ble.setHazard(false)
            return
        }

        if (wasNs) return 

        when {
            rightWasActive -> {
                if (isLong) ble.sendCommand("RIGHT:0")
                else ble.sendCommand("RIGHT:1")
            }
            heldMs >= SYNC_WINDOW_MS && isLong -> ble.sendCommand("RIGHT:2")
            else -> {}
        }
    }
}
