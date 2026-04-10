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
import java.util.*

object BleShareTransport {
    private const val TAG = "BleShareTransport"

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
    private const val CHARACTERISTIC_UUID_STRING = "87654321-4321-4321-4321-cba987654321"

    private val SERVICE_UUID = UUID.fromString(SERVICE_UUID_STRING)
    private val CHARACTERISTIC_UUID = UUID.fromString(CHARACTERISTIC_UUID_STRING)

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var currentGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var isScanning = false

    private var onDataReceived: ((String) -> Unit)? = null

    private var applicationContext: Context? = null

    fun init(ctx: Context) {
        applicationContext = ctx.applicationContext
        bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private fun hasPermission(permission: String): Boolean {
        return applicationContext?.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    // 发送端：开始广播并提供数据
    @SuppressLint("MissingPermission")
    fun startAdvertising(
        ctx: Context,
        data: String,
        onResult: (AdvertiseStartResult) -> Unit
    ) {
        if (bluetoothAdapter == null || bluetoothManager == null) {
            onResult(AdvertiseStartResult.NotInitialized)
            return
        }
        if (!isBluetoothEnabled()) {
            onResult(AdvertiseStartResult.BluetoothDisabled)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ||
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.w(TAG, "startAdvertising skipped: missing permission")
                onResult(AdvertiseStartResult.MissingPermission)
                return
            }
        }

        stopAdvertising()

        // 创建服务
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        @Suppress("DEPRECATION")
        characteristic.value = data.toByteArray(Charsets.UTF_8)
        service.addCharacteristic(characteristic)

        gattServer = bluetoothManager?.openGattServer(ctx, object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.d(TAG, "onCharacteristicReadRequest from ${device.address}, offset=$offset")
                @Suppress("DEPRECATION")
                val fullValue = characteristic.value ?: ByteArray(0)
                if (offset > fullValue.size) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                    return
                }
                val slice = fullValue.copyOfRange(offset, fullValue.size)
                Log.d(TAG, "sendResponse chunk=${slice.size}, total=${fullValue.size}, offset=$offset")
                @Suppress("DEPRECATION")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            }
        })
        if (gattServer == null) {
            Log.e(TAG, "openGattServer returned null")
            onResult(AdvertiseStartResult.GattServerOpenFailed)
            return
        }
        gattServer?.addService(service)

        // 开始广播
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser unavailable")
            gattServer?.close()
            gattServer = null
            onResult(AdvertiseStartResult.AdvertiserUnavailable)
            return
        }
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val advertiseData = AdvertiseData.Builder()
            // Keep payload compact to reduce ADVERTISE_FAILED_DATA_TOO_LARGE risk.
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "BLE advertising started")
                onResult(AdvertiseStartResult.Started)
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed, code=$errorCode")
                gattServer?.close()
                gattServer = null
                onResult(AdvertiseStartResult.Failed(errorCode))
            }
        }
        advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused")
    fun stopAdvertising() {
        advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        advertiseCallback = null
        gattServer?.close()
        gattServer = null
    }

    // 接收端：扫描并接收数据
    @SuppressLint("MissingPermission")
    fun startScanning(ctx: Context, onReceived: (String) -> Unit): ScanStartResult {
        if (isScanning) {
            stopScanning()
        }
        val adapter = bluetoothAdapter ?: return ScanStartResult.NotInitialized
        if (!isBluetoothEnabled()) return ScanStartResult.BluetoothDisabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                // Permissions not granted, cannot scan
                return ScanStartResult.MissingPermission
            }
        }
        val localScanner = adapter.bluetoothLeScanner ?: return ScanStartResult.ScannerUnavailable
        onDataReceived = onReceived
        scanner = localScanner
        val scanStartAt = SystemClock.elapsedRealtime()
        var isConnecting = false
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning || isConnecting) return

                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
                val hasTargetService = serviceUuids.contains(SERVICE_UUID)
                val fallbackConnectAllowed = (SystemClock.elapsedRealtime() - scanStartAt) >= 4_000L
                if (!hasTargetService && !fallbackConnectAllowed) return

                Log.d(TAG, "scan result from ${result.device?.address}")
                val device = result.device
                isConnecting = true
                var serviceDiscoveryStarted = false
                val callback = object : BluetoothGattCallback() {
                    private fun startServiceDiscovery(gatt: BluetoothGatt) {
                        if (serviceDiscoveryStarted) return
                        serviceDiscoveryStarted = true
                        gatt.discoverServices()
                    }

                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "connect failed, status=$status")
                            isConnecting = false
                            gatt.close()
                            return
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                            val mtuRequested = runCatching { gatt.requestMtu(517) }.getOrDefault(false)
                            Log.d(TAG, "connected, mtuRequested=$mtuRequested")
                            if (!mtuRequested) {
                                startServiceDiscovery(gatt)
                            }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            isConnecting = false
                            gatt.close()
                        }
                    }

                    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                        Log.d(TAG, "mtu changed, mtu=$mtu status=$status")
                        startServiceDiscovery(gatt)
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        Log.d(TAG, "onServicesDiscovered, status=$status")
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "discoverServices failed, status=$status")
                            isConnecting = false
                            gatt.close()
                            return
                        }
                        val service = gatt.getService(SERVICE_UUID)
                        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic == null) {
                            val discovered = gatt.services?.joinToString { it.uuid.toString() }.orEmpty()
                            Log.w(TAG, "target characteristic not found, services=[$discovered]")
                            isConnecting = false
                            gatt.close()
                            return
                        }
                        @Suppress("DEPRECATION")
                        val readStarted = gatt.readCharacteristic(characteristic)
                        Log.d(TAG, "readCharacteristic requested=$readStarted")
                        if (!readStarted) {
                            isConnecting = false
                            gatt.close()
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "characteristic read failed, status=$status")
                            isConnecting = false
                            gatt.close()
                            return
                        }
                        Log.d(TAG, "characteristic read success, bytes=${value.size}")
                        val data = String(value, Charsets.UTF_8)
                        onDataReceived?.invoke(data)
                        isConnecting = false
                        gatt.close()
                        stopScanning()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(TAG, "characteristic read failed(deprecated), status=$status")
                            isConnecting = false
                            gatt.close()
                            return
                        }
                        @Suppress("DEPRECATION")
                        val value = characteristic.value
                        Log.d(TAG, "characteristic read success(deprecated), bytes=${value.size}")
                        val data = String(value, Charsets.UTF_8)
                        onDataReceived?.invoke(data)
                        isConnecting = false
                        gatt.close()
                        stopScanning()
                    }
                }
                currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(ctx, false, callback)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "scan failed, code=$errorCode")
                isScanning = false
                isConnecting = false
            }
        }
        // Some devices drop filtered results unexpectedly, so we scan broadly and validate in callback.
        scanner?.startScan(emptyList(), scanSettings, scanCallback)
        isScanning = true
        return ScanStartResult.Started
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning) {
            scanCallback?.let { callback ->
                runCatching { scanner?.stopScan(callback) }
                    .onFailure { e -> Log.w(TAG, "stopScan failed: ${e.message}") }
            }
        }
        isScanning = false
        scanCallback = null
        currentGatt?.close()
        currentGatt = null
        onDataReceived = null
    }
}
