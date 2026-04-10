package com.zlearn.ui.nfc.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import android.os.SystemClock
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
    data class Importing(val payload: String, val eventId: Long) : NfcUiState
    data class Result(val message: String) : NfcUiState
    data class Error(val message: String) : NfcUiState
}

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
    private val importTimeoutMs = 20_000L

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
                        val payload = NfcShareCodec.decode(data).getOrNull()
                        val shouldValidateSession = current.payload.isNotBlank() && !current.payload.startsWith("TAG_ID:")
                        if (payload != null && (!shouldValidateSession || payload.sessionId == current.payload)) {
                            importTimeoutJob?.cancel()
                            BleShareTransport.stopScanning()
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
                            _uiState.value = NfcUiState.Result("导入成功: $imported 条题目")
                        } else {
                            importTimeoutJob?.cancel()
                            BleShareTransport.stopScanning()
                            _uiState.value = NfcUiState.Error("接收数据失败或sessionId不匹配")
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

    fun reset() {
        importTimeoutJob?.cancel()
        BleShareTransport.stopScanning()
        _uiState.value = NfcUiState.Idle
    }
}
