package com.yamahub.app

import com.yamahub.app.InputCfgItem

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque
import java.util.UUID

data class ModeDefinition(
    val id: Int,
    val category: Int,
    val label: String,
    val flags: Int
)

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val CHAR_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val urgentQueue: ArrayDeque<String> = ArrayDeque()
    private val idleQueue: ArrayDeque<String> = ArrayDeque()
    private val incomingCfgParts = mutableMapOf<Int, InputCfgItem>()
    private val incomingModeParts = mutableListOf<ModeDefinition>()

    @Volatile private var writeInFlight = false
    private val writeWatchdog = Runnable { finishWrite() }

    private fun isIdleCmd(cmd: String): Boolean =
        cmd == "GET" || cmd == "GET_CFG" || cmd == "GET_INCFG" || cmd.startsWith("SPEED:")

    @Volatile
    var isConnected: Boolean = false
        private set

    var lastReceivedInputCfg: List<InputCfgItem>? = null
        private set

    var onConnectionChanged: ((Boolean) -> Unit)? = null
    var onStateReceived: ((List<Boolean>) -> Unit)? = null
    var onInputStates: ((List<Boolean>) -> Unit)? = null
    var onConfigReceived: ((fade: Int, blinks: Int, curve: Int, acSpeed: Int, autoCancel: Boolean?, autoLights: Boolean?) -> Unit)? = null
    var onInputCfg: ((List<InputCfgItem>) -> Unit)? = null
    var onModeDefinitions: ((List<ModeDefinition>) -> Unit)? = null
    var starterEnabled: Boolean = false
        private set
    var onStarterEnabled: ((Boolean) -> Unit)? = null
    var onRawMessage: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            Log.d(TAG, "connectGatt: $address")
            bluetoothGatt?.close()
            bluetoothGatt = null
            characteristic = null
            urgentQueue.clear()
            idleQueue.clear()
            writeInFlight = false
            bluetoothGatt = device.connectGatt(
                context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            Log.e(TAG, "connect error", e)
            isConnected = false
            mainHandler.post { onConnectionChanged?.invoke(false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
        characteristic = null
        urgentQueue.clear()
        idleQueue.clear()
        writeInFlight = false
        mainHandler.removeCallbacks(writeWatchdog)
        isConnected = false
        starterEnabled = false
        mainHandler.post { onConnectionChanged?.invoke(false) }
    }

    fun sendCommand(cmd: String) {
        mainHandler.post {
            enqueue(cmd)
            pumpWrite()
        }
    }

    private fun enqueue(cmd: String) {
        when {
            cmd == "GET" -> {
                idleQueue.removeAll { it == "GET" }
                idleQueue.addLast(cmd)
            }
            cmd.startsWith("SPEED:") -> {
                idleQueue.removeAll { it.startsWith("SPEED:") }
                idleQueue.addLast(cmd)
            }
            cmd == "GET_CFG" || cmd == "GET_INCFG" -> {
                idleQueue.removeAll { it == cmd }
                idleQueue.addLast(cmd)
            }
            cmd.startsWith("OUT:") -> {
                val n = cmd.removePrefix("OUT:").substringBefore(':')
                urgentQueue.removeAll { it.startsWith("OUT:$n:") }
                urgentQueue.addLast(cmd)
            }
            else -> urgentQueue.addLast(cmd)
        }
    }

    private fun finishWrite() {
        mainHandler.removeCallbacks(writeWatchdog)
        writeInFlight = false
        pumpWrite()
    }

    @SuppressLint("MissingPermission")
    private fun pumpWrite() {
        if (writeInFlight) return
        val ch = characteristic
        val gatt = bluetoothGatt
        if (ch == null || gatt == null) return
        val cmd = urgentQueue.pollFirst() ?: idleQueue.pollFirst() ?: return
        writeInFlight = true
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        try {
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(
                    ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.value = bytes
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    gatt.writeCharacteristic(ch)
                }
            }
            Log.d(TAG, "Wysłano: $cmd → $ok")
            if (!ok) {
                writeInFlight = false
                if (isIdleCmd(cmd)) idleQueue.addFirst(cmd) else urgentQueue.addFirst(cmd)
                mainHandler.postDelayed({ pumpWrite() }, 30)
            } else {
                mainHandler.postDelayed(writeWatchdog, 40)
            }
        } catch (e: Exception) {
            writeInFlight = false
            Log.e(TAG, "sendCommand error", e)
            mainHandler.postDelayed({ pumpWrite() }, 30)
        }
    }

    fun requestState() = sendCommand("GET")
    fun requestConfig() = sendCommand("GET_CFG")
    fun requestInputCfg() = sendCommand("GET_INCFG")
    fun requestModeDefinitions() = sendCommand("GET_MODES")

    fun setInputCfgV4(
        inNum: Int,
        category: Int,
        functionId: Int,
        outPrimary: Int,
        outSecondary: Int,
        outputEnabled: Boolean,
        outputNum: Int,
        name: String
    ) {
        val safe = name.replace(" ", "_").replace(";", "_").replace(",", "_").take(15)
        sendCommand("SET_INCFG_V4:$inNum,$category,$functionId,$outPrimary," +
            "$outSecondary,${if (outputEnabled) 1 else 0},$outputNum,$safe")
    }

    fun commitInputCfg() = sendCommand("SET_INCFG_COMMIT")

    fun setConfig(
        fade: Int,
        blinks: Int,
        curve: Int,
        acSpeed: Int,
        autoCancel: Boolean,
        autoLights: Boolean
    ) {
        sendCommand(
            "SET_CFG:$fade,$blinks,$curve,$acSpeed," +
                "${if (autoCancel) 1 else 0},${if (autoLights) 1 else 0}"
        )
    }

    fun setInputCfg(
        inNum: Int,
        mode: Int,
        outNum: Int,
        name: String,
        outputEnabled: Boolean = false,
        outputNum: Int = outNum
    ) {
        val safe = name
            .replace(" ", "_")
            .replace(";", "_")
            .replace(",", "_")
            .take(15)
            .ifBlank { "IN_$inNum" }
        sendCommand("SET_INCFG:$inNum,$mode,$outNum," +
            "${if (outputEnabled) 1 else 0},$outputNum,$safe")
    }

    fun setOutput(num: Int, on: Boolean) {
        sendCommand("OUT:$num:${if (on) 1 else 0}")
    }

    fun setHazard(on: Boolean) {
        sendCommand("HAZARD:${if (on) 1 else 0}")
    }

    fun sendSpeed(kmh: Float) {
        sendCommand("SPEED:$kmh")
    }

    private fun handleMessage(msg: String) {
        Log.d(TAG, "Otrzymano: $msg")
        mainHandler.post {
            onRawMessage?.invoke(msg)
            when {
                msg == "MODES_START" -> {
                    incomingModeParts.clear()
                    Log.d(TAG, "Mode definitions transfer started")
                }
                msg.startsWith("MODEPART:") -> {
                    val p = msg.removePrefix("MODEPART:").split(",")
                    if (p.size >= 4) {
                        val def = ModeDefinition(
                            id = p[0].toIntOrNull() ?: 0,
                            category = p[1].toIntOrNull() ?: 0,
                            label = p[2],
                            flags = p[3].toIntOrNull() ?: 0
                        )
                        incomingModeParts.add(def)
                        Log.d(TAG, "Mode part received: ${def.label} (cat ${def.category})")
                    }
                }
                msg == "MODES_DONE" -> {
                    val finalModes = incomingModeParts.toList()
                    Log.d(TAG, "Mode definitions transfer complete. Total: ${finalModes.size}")
                    onModeDefinitions?.invoke(finalModes)
                }
                msg == "INCFG_START" -> {
                    incomingCfgParts.clear()
                }
                msg.startsWith("INPART:") -> {
                    val p = msg.removePrefix("INPART:").split(",")
                    if (p.size >= 10) {
                        val index = p[0].toIntOrNull() ?: return@post
                        val item = InputCfgItem(
                            inNum = index + 1,
                            mode = p[1].toIntOrNull() ?: 0,
                            outNum = p[3].toIntOrNull() ?: 0,
                            functionId = p[2].toIntOrNull() ?: 0,
                            outSecondary = p[4].toIntOrNull() ?: 0,
                            outputEnabled = p[5] == "1",
                            outputNum = p[6].toIntOrNull() ?: 0,
                            isFixed = p[7] == "1",
                            isOutLocked = p[8] == "1",
                            name = p.drop(9).joinToString(",").ifBlank { "IN_${index + 1}" }
                        )
                        incomingCfgParts[index] = item
                        Log.d(TAG, "Config part received: ${incomingCfgParts.size}/10")
                    }
                }
                msg == "INCFG_DONE" -> {
                    val list = (0 until 10).mapNotNull { incomingCfgParts[it] }
                    Log.d(TAG, "INCFG transfer complete. Items: ${list.size}/10")
                    if (list.size == 10) {
                        lastReceivedInputCfg = list
                        onInputCfg?.invoke(list)
                    } else {
                        Log.e(TAG, "INCFG transfer INCOMPLETE! Missing parts.")
                    }
                }
                msg.startsWith("STATE:") -> {
                    val bits = msg.removePrefix("STATE:")
                    val list = bits.map { it == '1' }
                    if (list.size >= 10) onStateReceived?.invoke(list.take(10))
                }
                msg.startsWith("INSTATE:") -> {
                    val bits = msg.removePrefix("INSTATE:")
                    val list = bits.map { it == '1' }
                    if (list.size >= 10) onInputStates?.invoke(list.take(10))
                }
                msg.startsWith("CFG:") -> {
                    val p = msg.removePrefix("CFG:").split(",")
                    if (p.size >= 3) {
                        val rawAc = p.getOrNull(3)?.toIntOrNull() ?: 20
                        val autoCancel = if (p.size >= 5)
                            (p[4].toIntOrNull() ?: 0) != 0
                        else
                            null
                        val autoLights = if (p.size >= 6)
                            (p[5].toIntOrNull() ?: 0) != 0
                        else
                            null
                        val ac = if (rawAc in 5..30) rawAc else 20
                        onConfigReceived?.invoke(
                            p[0].toIntOrNull() ?: 12,
                            p[1].toIntOrNull() ?: 3,
                            p[2].toIntOrNull() ?: 1,
                            ac,
                            autoCancel,
                            autoLights
                        )
                    }
                }
                msg.startsWith("INCFG:") -> {
                    val body = msg.removePrefix("INCFG:")
                        .substringBefore('\u0000')
                        .trim()
                    val list = mutableListOf<InputCfgItem>()
                    body.split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEachIndexed { idx, part ->
                            val p = part.split(",")
                            if (p.size >= 2) {
                                val extended = p.size >= 5 &&
                                    (p[2] == "0" || p[2] == "1") &&
                                    (p[3].toIntOrNull() ?: 0) in 1..10
                                val name = p.drop(if (extended) 4 else 2).joinToString(",")
                                    .trim { ch -> ch <= ' ' || ch == '\u0000' }
                                    .ifBlank { "IN_${idx + 1}" }
                                list.add(
                                    InputCfgItem(
                                        inNum = idx + 1,
                                        mode = p[0].toIntOrNull() ?: 0,
                                        outNum = p[1].toIntOrNull() ?: (idx + 1),
                                        name = name,
                                        outputEnabled = if (extended)
                                            p[2].toIntOrNull() == 1 else false,
                                        outputNum = if (extended)
                                            p[3].toIntOrNull() ?: (p[1].toIntOrNull() ?: (idx + 1))
                                        else
                                            p[1].toIntOrNull() ?: (idx + 1)
                                    )
                                )
                            }
                        }
                    Log.d(TAG, "INCFG sparsowano: ${list.size}")
                    if (list.size in 9..10) onInputCfg?.invoke(list)
                }
                msg.startsWith("STARTER_ENABLED:") -> {
                    val enabled = msg.removePrefix("STARTER_ENABLED:").trim() == "1"
                    starterEnabled = enabled
                    onStarterEnabled?.invoke(enabled)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    mainHandler.post { onConnectionChanged?.invoke(true) }
                    val ok = gatt.requestMtu(517)
                    Log.d(TAG, "requestMtu(517) → $ok")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    lastReceivedInputCfg = null
                    starterEnabled = false
                    characteristic = null
                    urgentQueue.clear()
                    idleQueue.clear()
                    writeInFlight = false
                    mainHandler.removeCallbacks(writeWatchdog)
                    mainHandler.post { onConnectionChanged?.invoke(false) }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu status=$status")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            val ch = service.getCharacteristic(CHAR_UUID) ?: return
            characteristic = ch
            gatt.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCCD_UUID)
            if (desc != null) {
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(desc)
            } else {
                mainHandler.post {
                    sendCommand("GET")
                    sendCommand("GET_CFG")
                    sendCommand("GET_INCFG")
                    sendCommand("GET_MODES")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "Descriptor write status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post {
                    sendCommand("GET")
                    sendCommand("GET_CFG")
                    sendCommand("GET_INCFG")
                    sendCommand("GET_MODES")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite status=$status")
            mainHandler.post { finishWrite() }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val bytes = characteristic.value ?: return
            val msg = bytes.toString(Charsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
            handleMessage(msg)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val msg = value.toString(Charsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
            handleMessage(msg)
        }
    }
}