package com.zlearn.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.*

object BleShareTransport {
    private const val TAG = "BleShareTransport"

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
    fun startAdvertising(ctx: Context, data: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ||
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                // Permissions not granted, cannot advertise
                Log.w(TAG, "startAdvertising skipped: missing permission")
                return
            }
        }
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
                @Suppress("DEPRECATION")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
            }
        })
        gattServer?.addService(service)

        // 开始广播
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
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
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed, code=$errorCode")
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
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d(TAG, "scan result from ${result.device?.address}")
                val device = result.device
                currentGatt = device.connectGatt(ctx, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.requestMtu(512) // 请求更大MTU
                            gatt.discoverServices()
                        }
                    }

                    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                        // MTU changed
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        val service = gatt.getService(SERVICE_UUID)
                        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                        gatt.readCharacteristic(characteristic)
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        val data = String(value, Charsets.UTF_8)
                        onDataReceived?.invoke(data)
                        gatt.close()
                        stopScanning()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        @Suppress("DEPRECATION")
                        val data = String(characteristic.value, Charsets.UTF_8)
                        onDataReceived?.invoke(data)
                        gatt.close()
                        stopScanning()
                    }
                })
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "scan failed, code=$errorCode")
                isScanning = false
            }
        }
        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
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
