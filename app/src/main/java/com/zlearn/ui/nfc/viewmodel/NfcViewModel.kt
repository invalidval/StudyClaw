package com.zlearn.ui.nfc.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import android.os.SystemClock
import android.util.Log
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcShareCodec
import com.zlearn.utils.NfcUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.zlearn.domain.usecase.QuestionUseCases
import com.zlearn.data.database.QuestionEntity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

sealed interface NfcUiState {
    data object Idle : NfcUiState
    data class Detected(val payload: String, val eventId: Long) : NfcUiState
    data class BroadcastDiscovering(val devices: List<BleShareTransport.BroadcastDeviceInfo>) : NfcUiState
    data class Importing(val payload: String, val eventId: Long, val preview: SharePreview? = null) : NfcUiState
    data class Result(val message: String, val preview: SharePreview? = null) : NfcUiState
    data class Error(val message: String) : NfcUiState
}

data class SharePreview(
    val sessionId: String,
    val senderDevice: String,
    val subject: String,
    val difficulty: Int,
    val itemCount: Int,
    val summary: String,
    val ocrText: String
)

@HiltViewModel
class NfcViewModel @Inject constructor(
    private val useCases: QuestionUseCases,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow<NfcUiState>(NfcUiState.Idle)
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()
    private var eventSeq: Long = 0
    private var lastPayload: String? = null
    private var lastEventAtMs: Long = 0
    private val debounceWindowMs = 800L
    private var importTimeoutJob: Job? = null
    private val importTimeoutMs = 45_000L

    companion object {
        private const val TAG = "NfcViewModel"
    }

    private fun isValidUuid(str: String): Boolean {
        return runCatching { java.util.UUID.fromString(str) }.isSuccess
    }

    private fun buildPreview(payload: com.zlearn.domain.model.SharePayload): SharePreview {
        val firstItem = payload.items.firstOrNull()
        return SharePreview(
            sessionId = payload.sessionId,
            senderDevice = payload.senderDevice,
            subject = firstItem?.subject.orEmpty(),
            difficulty = firstItem?.difficulty ?: 0,
            itemCount = payload.items.size,
            summary = firstItem?.summary.orEmpty(),
            ocrText = firstItem?.ocrText.orEmpty()
        )
    }

    init {
        viewModelScope.launch {
            NfcUtil.incomingPayload.collect { payload ->
                val now = SystemClock.elapsedRealtime()
                val isDuplicateBurst = payload == lastPayload && (now - lastEventAtMs) < debounceWindowMs
                if (isDuplicateBurst) return@collect

                lastPayload = payload
                lastEventAtMs = now
                eventSeq += 1
                _uiState.value = NfcUiState.Detected(payload = payload, eventId = eventSeq)
            }
        }
    }


    fun importDetectedPayload() {
        val current = _uiState.value
        if (current !is NfcUiState.Detected) return
        importTimeoutJob?.cancel()
        viewModelScope.launch {
            _uiState.value = NfcUiState.Importing(current.payload, current.eventId)
            importTimeoutJob = viewModelScope.launch {
                delay(importTimeoutMs)
                if (_uiState.value is NfcUiState.Importing) {
                    BleShareTransport.stopScanning()
                    _uiState.value = NfcUiState.Error("BLE 导入超时，请重试")
                }
            }
            // Start BLE scanning for data
            val startResult = BleShareTransport.startScanning(context) { data ->
                viewModelScope.launch {
                    try {
                        // 使用 exceptionOrNull() 提取解码异常，定位根本原因
                        val decodeResult = NfcShareCodec.decode(data)
                        val payload = decodeResult.getOrNull()
                        if (payload == null) {
                            val ex = decodeResult.exceptionOrNull()
                            Log.e(TAG, "decode FAILED: ${ex?.javaClass?.simpleName}: ${ex?.message}", ex)
                            Log.e(TAG, "raw data(len=${data.length}), last 80 chars: >>${data.takeLast(80)}<<")
                        }
                        val bleSessionId = payload?.sessionId ?: "<null>"
                        Log.d(TAG, "BLE data received, bleSessionId=$bleSessionId, dataLen=${data.length}, nfcPayload=${current.payload}")

                        // 严格校验：NFC payload 必须为合法 UUID，且与 BLE 数据的 sessionId 精确匹配
                        if (payload != null && isValidUuid(current.payload) && payload.sessionId == current.payload) {
                            Log.d(TAG, "session validation passed, importing ${payload.items.size} items")
                            val preview = buildPreview(payload)
                            _uiState.value = NfcUiState.Importing(data, current.eventId, preview)
                            delay(250)
                            var imported = 0
                            for (item in payload.items) {
                                val entity = QuestionEntity(
                                    id = 0,
                                    imagePath = "",
                                    ocrText = item.ocrText,
                                    aiAnalysis = item.aiAnalysis,
                                    summary = item.summary,
                                    subject = item.subject,
                                    difficulty = item.difficulty,
                                    createTime = System.currentTimeMillis(),
                                    isArchived = item.isArchived,
                                    archiveType = item.archiveType
                                )
                                useCases.addQuestion(entity)
                                imported++
                            }
                            val ackSent = BleShareTransport.sendAck(payload.sessionId) { ok ->
                                viewModelScope.launch {
                                    importTimeoutJob?.cancel()
                                    BleShareTransport.stopScanning()
                                    _uiState.value = if (ok) {
                                        NfcUiState.Result("已收到并导入: $imported 条题目", preview)
                                    } else {
                                        NfcUiState.Error("已导入，但未能向发送方回传确认")
                                    }
                                }
                            }
                            if (!ackSent) {
                                importTimeoutJob?.cancel()
                                BleShareTransport.stopScanning()
                                _uiState.value = NfcUiState.Result("已收到并导入: $imported 条题目", preview)
                            }
                        } else {
                            Log.w(TAG, "session validation FAILED: nfcIsUuid=${isValidUuid(current.payload)}, blePayloadNull=${payload == null}, nfcPayload=${current.payload.take(60)}, bleSessionId=$bleSessionId")
                            importTimeoutJob?.cancel()
                            BleShareTransport.stopScanning()
                            _uiState.value = NfcUiState.Error("接收数据失败或sessionId不匹配\nNFC: ${current.payload.take(40)}\nBLE: $bleSessionId")
                        }
                    } catch (e: Exception) {
                        importTimeoutJob?.cancel()
                        BleShareTransport.stopScanning()
                        _uiState.value = NfcUiState.Error("导入失败: ${e.message}")
                    }
                }
            }

            when (startResult) {
                BleShareTransport.ScanStartResult.Started -> Unit
                BleShareTransport.ScanStartResult.NotInitialized -> {
                    importTimeoutJob?.cancel()
                    _uiState.value = NfcUiState.Error("BLE 未初始化，请先进入主页面完成初始化")
                }
                BleShareTransport.ScanStartResult.BluetoothDisabled -> {
                    importTimeoutJob?.cancel()
                    _uiState.value = NfcUiState.Error("蓝牙未开启，请先打开蓝牙")
                }
                BleShareTransport.ScanStartResult.MissingPermission -> {
                    importTimeoutJob?.cancel()
                    _uiState.value = NfcUiState.Error("缺少 BLE 权限，请先授权 BLUETOOTH_SCAN / BLUETOOTH_CONNECT")
                }
                BleShareTransport.ScanStartResult.ScannerUnavailable -> {
                    importTimeoutJob?.cancel()
                    _uiState.value = NfcUiState.Error("系统未提供 BLE 扫描器，当前设备可能不支持")
                }
            }
            if (startResult != BleShareTransport.ScanStartResult.Started) {
                importTimeoutJob?.cancel()
                BleShareTransport.stopScanning()
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  广播模式：扫描周边设备，用户手动选择
    // ═══════════════════════════════════════════════
    fun startBroadcastScan() {
        importTimeoutJob?.cancel()
        _uiState.value = NfcUiState.BroadcastDiscovering(emptyList())
        val scanResult = BleShareTransport.startBroadcastScan(context) { device ->
            val current = _uiState.value
            if (current is NfcUiState.BroadcastDiscovering) {
                _uiState.value = current.copy(devices = current.devices + device)
            }
        }
        when (scanResult) {
            BleShareTransport.ScanStartResult.Started -> Unit
            else -> { BleShareTransport.stopScanning(); _uiState.value = NfcUiState.Error("广播扫描启动失败") }
        }
    }

    fun selectBroadcastDevice(device: BleShareTransport.BroadcastDeviceInfo) {
        val sid = device.sessionId ?: run {
            _uiState.value = NfcUiState.Error("未从该设备收到有效 sessionId，请确认对方已开启分享")
            return
        }
        BleShareTransport.stopScanning()
        importTimeoutJob?.cancel()
        viewModelScope.launch {
            _uiState.value = NfcUiState.Importing(sid, 0)
            importTimeoutJob = viewModelScope.launch {
                delay(importTimeoutMs)
                if (_uiState.value is NfcUiState.Importing) {
                    BleShareTransport.stopScanning()
                    _uiState.value = NfcUiState.Error("BLE 导入超时，请重试")
                }
            }
            BleShareTransport.connectToBroadcastDevice(context, device.address, sid) { data ->
                viewModelScope.launch {
                    try {
                        val payload = NfcShareCodec.decode(data).getOrNull()
                        if (payload != null && payload.sessionId == sid) {
                            val preview = buildPreview(payload)
                            _uiState.value = NfcUiState.Importing(sid, 0, preview)
                            delay(250)
                            var imported = 0
                            for (item in payload.items) {
                                useCases.addQuestion(QuestionEntity(
                                    id = 0, imagePath = "",
                                    ocrText = item.ocrText, aiAnalysis = item.aiAnalysis,
                                    summary = item.summary, subject = item.subject,
                                    difficulty = item.difficulty, createTime = System.currentTimeMillis(),
                                    isArchived = item.isArchived, archiveType = item.archiveType
                                ))
                                imported++
                            }
                            val ackSent = BleShareTransport.sendAck(payload.sessionId) { ok ->
                                viewModelScope.launch {
                                    importTimeoutJob?.cancel(); BleShareTransport.stopScanning()
                                    _uiState.value = if (ok) NfcUiState.Result("已收到并导入: $imported 条题目", preview)
                                    else NfcUiState.Error("已导入，但未能向发送方回传确认")
                                }
                            }
                            if (!ackSent) { importTimeoutJob?.cancel(); BleShareTransport.stopScanning()
                                _uiState.value = NfcUiState.Result("已收到并导入: $imported 条题目", preview) }
                        } else {
                            importTimeoutJob?.cancel(); BleShareTransport.stopScanning()
                            _uiState.value = NfcUiState.Error("接收数据失败或sessionId不匹配\n期望: $sid\n实际: ${payload?.sessionId ?: "<null>"}")
                        }
                    } catch (e: Exception) { importTimeoutJob?.cancel(); BleShareTransport.stopScanning()
                        _uiState.value = NfcUiState.Error("导入失败: ${e.message}") }
                }
            }
        }
    }

    fun reset() {
        importTimeoutJob?.cancel()
        importTimeoutJob = null
        BleShareTransport.stopScanning()
        lastPayload = null
        lastEventAtMs = 0
        eventSeq = 0
        _uiState.value = NfcUiState.Idle
    }
}
