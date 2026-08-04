package com.yamahub.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque
import java.util.UUID

data class InputCfgItem(
    val inNum: Int,
    val mode: Int,
    val outNum: Int,
    val name: String
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

    private val writeQueue: ArrayDeque<String> = ArrayDeque()
    @Volatile private var writeInFlight = false

    @Volatile
    var isConnected: Boolean = false
        private set

    var onConnectionChanged: ((Boolean) -> Unit)? = null
    var onStateReceived: ((List<Boolean>) -> Unit)? = null
    var onConfigReceived: ((fade: Int, blinks: Int, curve: Int, acSpeed: Int) -> Unit)? = null
    var onInputCfg: ((List<InputCfgItem>) -> Unit)? = null
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
            writeQueue.clear()
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
        writeQueue.clear()
        writeInFlight = false
        isConnected = false
        mainHandler.post { onConnectionChanged?.invoke(false) }
    }

    fun sendCommand(cmd: String) {
        mainHandler.post {
            writeQueue.addLast(cmd)
            pumpWrite()
        }
    }

    @SuppressLint("MissingPermission")
    private fun pumpWrite() {
        if (writeInFlight) return
        val ch = characteristic
        val gatt = bluetoothGatt
        if (ch == null || gatt == null) {
            if (writeQueue.isNotEmpty()) {
                Log.d(TAG, "Brak charakterystyki, kolejka=${writeQueue.size}")
            }
            return
        }
        val cmd = writeQueue.pollFirst() ?: return
        writeInFlight = true
        try {
            ch.value = cmd.toByteArray(Charsets.UTF_8)
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val ok = gatt.writeCharacteristic(ch)
            Log.d(TAG, "Wysłano: $cmd → $ok")
            if (!ok) {
                writeInFlight = false
                writeQueue.addFirst(cmd)
                mainHandler.postDelayed({ pumpWrite() }, 40)
            }
        } catch (e: Exception) {
            writeInFlight = false
            Log.e(TAG, "sendCommand error", e)
            mainHandler.postDelayed({ pumpWrite() }, 40)
        }
    }

    fun requestState() = sendCommand("GET")
    fun requestConfig() = sendCommand("GET_CFG")
    fun requestInputCfg() = sendCommand("GET_INCFG")

    fun setConfig(fade: Int, blinks: Int, curve: Int, acSpeed: Int) {
        sendCommand("SET_CFG:$fade,$blinks,$curve,$acSpeed")
    }

    fun setInputCfg(inNum: Int, mode: Int, outNum: Int, name: String) {
        val safe = name
            .replace(" ", "_")
            .replace(";", "_")
            .replace(",", "_")
            .take(15)
            .ifBlank { "IN_$inNum" }
        sendCommand("SET_INCFG:$inNum,$mode,$outNum,$safe")
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
                msg.startsWith("STATE:") -> {
                    val bits = msg.removePrefix("STATE:")
                    val list = bits.map { it == '1' }
                    if (list.size >= 10) onStateReceived?.invoke(list.take(10))
                }
                msg.startsWith("CFG:") -> {
                    val p = msg.removePrefix("CFG:").split(",")
                    if (p.size >= 3) {
                        onConfigReceived?.invoke(
                            p[0].toIntOrNull() ?: 12,
                            p[1].toIntOrNull() ?: 3,
                            p[2].toIntOrNull() ?: 0,
                            p.getOrNull(3)?.toIntOrNull() ?: 20
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
                                val name = p.drop(2).joinToString(",")
                                    .trim { ch -> ch <= ' ' || ch == '\u0000' }
                                    .ifBlank { "IN_${idx + 1}" }
                                list.add(
                                    InputCfgItem(
                                        inNum = idx + 1,
                                        mode = p[0].toIntOrNull() ?: 0,
                                        outNum = p[1].toIntOrNull() ?: (idx + 1),
                                        name = name
                                    )
                                )
                            }
                        }
                    Log.d(TAG, "INCFG sparsowano: ${list.size}")
                    if (list.size == 9) onInputCfg?.invoke(list)
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
                    mainHandler.post { onConnectionChanged?.invoke(true) }
                    val ok = gatt.requestMtu(517)
                    Log.d(TAG, "requestMtu(517) → $ok")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    characteristic = null
                    writeQueue.clear()
                    writeInFlight = false
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
            writeInFlight = false
            mainHandler.post { pumpWrite() }
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