package com.zlearn.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.util.*

object BleShareTransport {
    private const val TAG = "BleShareTransport"
    private const val ACK_PREFIX = "ACK:"

    sealed interface AdvertiseStartResult {
        data object Started : AdvertiseStartResult
        data object NotInitialized : AdvertiseStartResult
        data object BluetoothDisabled : AdvertiseStartResult
        data object MissingPermission : AdvertiseStartResult
        data object AdvertiserUnavailable : AdvertiseStartResult
        data object GattServerOpenFailed : AdvertiseStartResult
        data class Failed(val errorCode: Int) : AdvertiseStartResult
    }

    sealed interface ScanStartResult {
        data object Started : ScanStartResult
        data object NotInitialized : ScanStartResult
        data object BluetoothDisabled : ScanStartResult
        data object MissingPermission : ScanStartResult
        data object ScannerUnavailable : ScanStartResult
    }

    private const val SERVICE_UUID_STRING = "12345678-1234-1234-1234-123456789abc"
    private const val CHAR_NOTIFY_UUID_STRING = "87654321-4321-4321-4321-cba987654321"
    private const val ACK_CHARACTERISTIC_UUID_STRING = "11111111-2222-3333-4444-555555555555"
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val SERVICE_UUID = UUID.fromString(SERVICE_UUID_STRING)
    private val CHAR_NOTIFY_UUID = UUID.fromString(CHAR_NOTIFY_UUID_STRING)
    private val ACK_CHARACTERISTIC_UUID = UUID.fromString(ACK_CHARACTERISTIC_UUID_STRING)

    // 广播模式：把 sessionId 作为 16 字节 raw UUID 放到 scan response 中
    private const val MANUFACTURER_ID = 0xFFFD

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    // ── 发送端 ──
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var serverMtu = 23
    private var notifyData: ByteArray? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var isSendingNotify = false
    private var onAckReceived: ((String) -> Unit)? = null
    private var onAckWriteCompleted: ((Boolean) -> Unit)? = null

    // ── 接收端 ──
    private var scanner: BluetoothLeScanner? = null
    private var currentGatt: BluetoothGatt? = null
    private var currentAckCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var onDataReceived: ((String) -> Unit)? = null
    // 通知累积
    private var notifyAccumulator = ByteArray(0)
    private var notifyTotalSize = -1

    private var applicationContext: Context? = null

    fun init(ctx: Context) {
        applicationContext = ctx.applicationContext
        bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun setOnAckReceivedListener(listener: ((String) -> Unit)?) {
        onAckReceived = listener
    }

    private fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun hasPermission(permission: String): Boolean =
        applicationContext?.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ═══════════════════════════════════════════════
    //  发送端：BLE 广播 + GATT 服务 (NOTIFY 推送数据)
    // ═══════════════════════════════════════════════
    @SuppressLint("MissingPermission")
    fun startAdvertising(ctx: Context, data: String, sessionId: String, includeSessionInScan: Boolean, onResult: (AdvertiseStartResult) -> Unit) {
        if (bluetoothAdapter == null || bluetoothManager == null) { onResult(AdvertiseStartResult.NotInitialized); return }
        if (!isBluetoothEnabled()) { onResult(AdvertiseStartResult.BluetoothDisabled); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                onResult(AdvertiseStartResult.MissingPermission); return
            }
        }

        stopAdvertising()

        notifyData = data.toByteArray(Charsets.UTF_8)
        isSendingNotify = false
        Log.d(TAG, "notify data ready, totalBytes=${notifyData?.size}")

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // NOTIFY 特征值 — 发送端推送数据
        val notifyChar = BluetoothGattCharacteristic(CHAR_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        val cccd = BluetoothGattDescriptor(CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        notifyChar.addDescriptor(cccd)
        service.addCharacteristic(notifyChar)
        notifyCharacteristic = notifyChar

        // ACK 特征值 — 接收端回传确认
        val ackChar = BluetoothGattCharacteristic(ACK_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE)
        service.addCharacteristic(ackChar)

        gattServer = bluetoothManager?.openGattServer(ctx, object : BluetoothGattServerCallback() {
            override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
                Log.d(TAG, "server MTU changed, device=${device?.address}, mtu=$mtu")
                serverMtu = mtu
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                if (descriptor.uuid == CCCD_UUID &&
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.d(TAG, "notifications enabled by ${device.address}")
                    if (responseNeeded)
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    // 启动通知推送
                    startNotifyPush(device, notifyChar)
                } else {
                    if (responseNeeded)
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                if (characteristic.uuid == ACK_CHARACTERISTIC_UUID) {
                    val ackText = String(value, Charsets.UTF_8)
                    Log.d(TAG, "received ack from ${device.address}: $ackText")
                    val sessionId = ackText.removePrefix(ACK_PREFIX).trim()
                    onAckReceived?.invoke(sessionId)
                    if (responseNeeded)
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } else {
                    if (responseNeeded)
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        })
        if (gattServer == null) { onResult(AdvertiseStartResult.GattServerOpenFailed); return }
        gattServer?.addService(service)

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            gattServer?.close(); gattServer = null
            onResult(AdvertiseStartResult.AdvertiserUnavailable); return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true).build()
        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // 广播模式需要显示设备名以便用户区分
            .addServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        // Scan Response：仅广播模式携带 sessionId，NFC 模式不暴露
        val scanResponseData = if (includeSessionInScan) {
            val sessionUuid = UUID.fromString(sessionId)
            val sidBytes = ByteBuffer.allocate(16)
                .putLong(sessionUuid.mostSignificantBits)
                .putLong(sessionUuid.leastSignificantBits).array()
            AdvertiseData.Builder()
                .addManufacturerData(MANUFACTURER_ID, sidBytes).build()
        } else null
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "BLE advertising started")
                onResult(AdvertiseStartResult.Started)
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed, code=$errorCode")
                gattServer?.close(); gattServer = null
                onResult(AdvertiseStartResult.Failed(errorCode))
            }
        }
        if (scanResponseData != null)
            advertiser?.startAdvertising(settings, advData, scanResponseData, advertiseCallback)
        else
            advertiser?.startAdvertising(settings, advData, advertiseCallback)
    }

    private fun startNotifyPush(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        val data = notifyData ?: return
        if (isSendingNotify) return
        isSendingNotify = true

        Thread {
            try {
                val maxChunkSize = minOf((serverMtu - 3).coerceAtLeast(20), 500)

                // 第一包: 4 字节总长度(大端) + 数据
                val firstDataLen = minOf(maxChunkSize - 4, data.size)
                val firstChunk = ByteArray(4 + firstDataLen)
                writeIntBE(firstChunk, 0, data.size)
                System.arraycopy(data, 0, firstChunk, 4, firstDataLen)

                characteristic.value = firstChunk
                var ok = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                if (!ok) { Log.w(TAG, "notify first chunk failed"); return@Thread }

                var offset = firstDataLen
                while (offset < data.size) {
                    Thread.sleep(30)
                    val end = minOf(offset + maxChunkSize, data.size)
                    val chunk = data.copyOfRange(offset, end)
                    characteristic.value = chunk
                    ok = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                    if (!ok) { Log.w(TAG, "notify chunk at $offset failed"); break }
                    offset = end
                }
                Log.d(TAG, "notify push complete: sent ${data.size} bytes")
            } finally {
                isSendingNotify = false
            }
        }.start()
    }

    private fun writeIntBE(buf: ByteArray, off: Int, v: Int) {
        buf[off]     = ((v shr 24) and 0xFF).toByte()
        buf[off + 1] = ((v shr 16) and 0xFF).toByte()
        buf[off + 2] = ((v shr 8)  and 0xFF).toByte()
        buf[off + 3] = (v and 0xFF).toByte()
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        advertiseCallback = null
        gattServer?.close()
        gattServer = null
        notifyData = null
        notifyCharacteristic = null
        isSendingNotify = false
        onAckReceived = null
        onAckWriteCompleted = null
    }

    // ═══════════════════════════════════════════════
    //  接收端：扫描 + 连接 + 订阅 NOTIFY
    // ═══════════════════════════════════════════════
    @SuppressLint("MissingPermission")
    fun startScanning(ctx: Context, onReceived: (String) -> Unit): ScanStartResult {
        if (isScanning) stopScanning()
        val adapter = bluetoothAdapter ?: return ScanStartResult.NotInitialized
        if (!isBluetoothEnabled()) return ScanStartResult.BluetoothDisabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                return ScanStartResult.MissingPermission
        }
        val localScanner = adapter.bluetoothLeScanner ?: return ScanStartResult.ScannerUnavailable
        onDataReceived = onReceived
        scanner = localScanner
        val scanStartAt = SystemClock.elapsedRealtime()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning || currentGatt != null) return
                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
                val hasTargetService = serviceUuids.contains(SERVICE_UUID)
                val fallbackAllowed = (SystemClock.elapsedRealtime() - scanStartAt) >= 4_000L
                if (!hasTargetService && !fallbackAllowed) return

                Log.d(TAG, "scan result from ${result.device?.address}")
                val device = result.device
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "connect failed, status=$status")
                            gatt.close(); currentGatt = null; return
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                            val mtuOk = runCatching { gatt.requestMtu(517) }.getOrDefault(false)
                            Log.d(TAG, "connected, mtuRequested=$mtuOk")
                            if (!mtuOk) discover(gatt)
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            gatt.close(); currentGatt = null
                        }
                    }

                    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                        Log.d(TAG, "mtu changed, mtu=$mtu status=$status")
                        discover(gatt)
                    }

                    private fun discover(gatt: BluetoothGatt) {
                        try { gatt.javaClass.getMethod("refresh").invoke(gatt) } catch (_: Exception) {}
                        gatt.discoverServices()
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        Log.d(TAG, "onServicesDiscovered, status=$status")
                        if (status != BluetoothGatt.GATT_SUCCESS) { gatt.close(); currentGatt = null; return }

                        val service = gatt.getService(SERVICE_UUID)
                        val notifyChar = service?.getCharacteristic(CHAR_NOTIFY_UUID)
                        currentAckCharacteristic = service?.getCharacteristic(ACK_CHARACTERISTIC_UUID)

                        if (notifyChar == null) {
                            val discovered = gatt.services?.joinToString { it.uuid.toString() }.orEmpty()
                            Log.w(TAG, "notify characteristic not found, services=[$discovered]")
                            gatt.close(); currentGatt = null; return
                        }

                        // 订阅 NOTIFY
                        notifyAccumulator = ByteArray(0)
                        notifyTotalSize = -1
                        gatt.setCharacteristicNotification(notifyChar, true)
                        val cccd = notifyChar.getDescriptor(CCCD_UUID)
                        if (cccd != null) {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            val wOk = gatt.writeDescriptor(cccd)
                            Log.d(TAG, "CCCD write requested=$wOk")
                        } else {
                            Log.w(TAG, "CCCD descriptor not found on notify char")
                            gatt.close(); currentGatt = null
                        }
                    }

                    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                        Log.d(TAG, "CCCD write completed, status=$status")
                    }

                    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                        Log.d(TAG, "notify received, bytes=${value.size}")
                        if (notifyTotalSize == -1 && value.size >= 4) {
                            notifyTotalSize = (value[0].toInt() and 0xFF shl 24) or
                                    (value[1].toInt() and 0xFF shl 16) or
                                    (value[2].toInt() and 0xFF shl 8) or
                                    (value[3].toInt() and 0xFF)
                            notifyAccumulator = value.copyOfRange(4, value.size)
                        } else {
                            notifyAccumulator += value
                        }
                        Log.d(TAG, "notify progress: ${notifyAccumulator.size}/$notifyTotalSize")
                        if (notifyTotalSize > 0 && notifyAccumulator.size >= notifyTotalSize) {
                            val data = String(notifyAccumulator, Charsets.UTF_8)
                            Log.d(TAG, "notify data complete, bytes=${notifyAccumulator.size}, first100=${data.take(100)}")
                            onDataReceived?.invoke(data)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        if (characteristic.uuid == ACK_CHARACTERISTIC_UUID) {
                            val ok = status == BluetoothGatt.GATT_SUCCESS
                            Log.d(TAG, "ack write completed(deprecated), status=$status ok=$ok")
                            onAckWriteCompleted?.invoke(ok)
                            onAckWriteCompleted = null
                            gatt.close(); currentGatt = null; currentAckCharacteristic = null
                        }
                    }
                }
                currentGatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "scan failed, code=$errorCode"); isScanning = false
            }
        }
        scanner?.startScan(emptyList(), scanSettings, scanCallback)
        isScanning = true
        return ScanStartResult.Started
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(closeGatt: Boolean = true) {
        if (isScanning) {
            scanCallback?.let { runCatching { scanner?.stopScan(it) }.onFailure { e -> Log.w(TAG, "stopScan failed: ${e.message}") } }
        }
        isScanning = false
        scanCallback = null
        notifyAccumulator = ByteArray(0)
        notifyTotalSize = -1
        if (closeGatt) {
            currentGatt?.close()
            currentGatt = null
            currentAckCharacteristic = null
        }
        onDataReceived = null
    }

    // ═══════════════════════════════════════════════
    //  广播模式：扫描 + 手动选择设备
    // ═══════════════════════════════════════════════
    data class BroadcastDeviceInfo(
        val deviceName: String,
        val address: String,
        val sessionId: String?
    )

    fun extractSessionIdFromScan(result: ScanResult): String? {
        val data = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return null
        if (data.size != 16) return null
        return try {
            val bb = ByteBuffer.wrap(data)
            UUID(bb.long, bb.long).toString()
        } catch (_: Exception) { null }
    }

    @SuppressLint("MissingPermission")
    fun startBroadcastScan(ctx: Context, onDevice: (BroadcastDeviceInfo) -> Unit): ScanStartResult {
        if (isScanning) stopScanning()
        val adapter = bluetoothAdapter ?: return ScanStartResult.NotInitialized
        if (!isBluetoothEnabled()) return ScanStartResult.BluetoothDisabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                return ScanStartResult.MissingPermission
        }
        scanner = adapter.bluetoothLeScanner ?: return ScanStartResult.ScannerUnavailable
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val seen = mutableSetOf<String>()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning) return
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
                if (!uuids.contains(SERVICE_UUID)) return
                val addr = result.device.address
                if (addr in seen) return
                seen.add(addr)
                val name = result.scanRecord?.deviceName ?: result.device.name ?: addr.takeLast(5)
                val sid = extractSessionIdFromScan(result)
                onDevice(BroadcastDeviceInfo(name, addr, sid))
            }
            override fun onScanFailed(errorCode: Int) { Log.e(TAG, "broadcast scan failed, code=$errorCode") }
        }
        scanner?.startScan(emptyList(), scanSettings, scanCallback)
        isScanning = true
        return ScanStartResult.Started
    }

    @SuppressLint("MissingPermission")
    fun connectToBroadcastDevice(ctx: Context, address: String, sessionId: String, onReceived: (String) -> Unit) {
        val adapter = bluetoothAdapter ?: return
        val device = adapter.getRemoteDevice(address)
        onDataReceived = onReceived
        notifyAccumulator = ByteArray(0)
        notifyTotalSize = -1
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) { gatt.close(); currentGatt = null; return }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                    val mtuOk = runCatching { gatt.requestMtu(517) }.getOrDefault(false)
                    if (!mtuOk) { try { gatt.javaClass.getMethod("refresh").invoke(gatt) } catch (_: Exception) {}; gatt.discoverServices() }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { gatt.close(); currentGatt = null }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                try { gatt.javaClass.getMethod("refresh").invoke(gatt) } catch (_: Exception) {}
                gatt.discoverServices()
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) { gatt.close(); currentGatt = null; return }
                val notifyChar = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_NOTIFY_UUID)
                currentAckCharacteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(ACK_CHARACTERISTIC_UUID)
                if (notifyChar == null) { gatt.close(); currentGatt = null; return }
                notifyAccumulator = ByteArray(0); notifyTotalSize = -1
                gatt.setCharacteristicNotification(notifyChar, true)
                val cccd = notifyChar.getDescriptor(CCCD_UUID) ?: return
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (notifyTotalSize == -1 && value.size >= 4) {
                    notifyTotalSize = (value[0].toInt() and 0xFF shl 24) or (value[1].toInt() and 0xFF shl 16) or
                        (value[2].toInt() and 0xFF shl 8) or (value[3].toInt() and 0xFF)
                    notifyAccumulator = value.copyOfRange(4, value.size)
                } else { notifyAccumulator += value }
                if (notifyTotalSize > 0 && notifyAccumulator.size >= notifyTotalSize) {
                    val data = String(notifyAccumulator, Charsets.UTF_8)
                    Log.d(TAG, "broadcast notify complete, bytes=${notifyAccumulator.size}")
                    onDataReceived?.invoke(data)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == ACK_CHARACTERISTIC_UUID) {
                    onAckWriteCompleted?.invoke(status == BluetoothGatt.GATT_SUCCESS)
                    onAckWriteCompleted = null; gatt.close(); currentGatt = null; currentAckCharacteristic = null
                }
            }
        }
        currentGatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun sendAck(sessionId: String, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val gatt = currentGatt ?: return false
        val characteristic = currentAckCharacteristic ?: return false
        val payload = "$ACK_PREFIX$sessionId".toByteArray(Charsets.UTF_8)
        onAckWriteCompleted = onComplete
        Log.d(TAG, "sendAck started, sessionId=$sessionId payloadBytes=${payload.size}")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                gatt.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            true
        } catch (_: Throwable) { false }
    }
}
