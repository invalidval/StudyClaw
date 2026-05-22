package com.zlearn.utils

import android.content.Intent
import android.os.Build
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object NfcUtil {
    sealed interface SenderShareState {
        data object Idle : SenderShareState
        data class WaitingAck(val sessionId: String) : SenderShareState
        data class Completed(val message: String) : SenderShareState
        data class Error(val message: String) : SenderShareState
    }

    private val _incomingPayload = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val incomingPayload: SharedFlow<String> = _incomingPayload.asSharedFlow()

    private val _receiverEnabled = MutableStateFlow(false)
    val receiverEnabled: StateFlow<Boolean> = _receiverEnabled.asStateFlow()

    private val _senderEnabled = MutableStateFlow(false)
    val senderEnabled: StateFlow<Boolean> = _senderEnabled.asStateFlow()

    private val _outgoingPayload = MutableStateFlow<String?>(null)
    val outgoingPayload: StateFlow<String?> = _outgoingPayload.asStateFlow()

    private val _senderShareState = MutableStateFlow<SenderShareState>(SenderShareState.Idle)
    val senderShareState: StateFlow<SenderShareState> = _senderShareState.asStateFlow()

    fun setReceiverEnabled(enabled: Boolean) {
        _receiverEnabled.value = enabled
    }

    fun setSenderEnabled(enabled: Boolean) {
        _senderEnabled.value = enabled
    }

    fun setOutgoingPayload(payload: String?) {
        _outgoingPayload.value = payload
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearIncomingPayload() {
        _incomingPayload.resetReplayCache()
    }

    fun clearOutgoingPayload() {
        _outgoingPayload.value = null
    }

    fun setSenderShareState(state: SenderShareState) {
        _senderShareState.value = state
    }

    fun clearSenderShareState() {
        _senderShareState.value = SenderShareState.Idle
    }

    fun publishIncomingPayload(payload: String) {
        if (!_receiverEnabled.value) return
        _incomingPayload.tryEmit(payload)
    }

    fun extractNfcTextPayload(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (
            action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return null

        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Array<NdefMessage>::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        if (rawMessages != null) {
            val messages = rawMessages.mapNotNull { it as? NdefMessage }
            val firstRecord = messages.firstOrNull()?.records?.firstOrNull()
            val textPayload = firstRecord?.let { decodeRecordToText(it) }
            if (!textPayload.isNullOrBlank()) return textPayload
        }

        // 没有NDEF文本时，至少回传Tag ID，便于UI感知“已触发NFC”
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val id = tag?.id ?: return null
        val hex = id.joinToString(separator = "") { b -> "%02X".format(b) }
        return "TAG_ID:$hex"
    }

    fun extractPayloadFromTag(tag: Tag?): String? {
        if (tag == null) return null

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            val message = runCatching {
                ndef.cachedNdefMessage ?: run {
                    ndef.connect()
                    try {
                        ndef.ndefMessage
                    } finally {
                        runCatching { ndef.close() }
                    }
                }
            }.getOrNull()

            val record = message?.records?.firstOrNull()
            val payload = record?.let { decodeRecordToText(it) }
            if (!payload.isNullOrBlank()) return payload
        }

        val id = tag.id ?: return null
        val hex = id.joinToString(separator = "") { b -> "%02X".format(b) }
        return "TAG_ID:$hex"
    }

    /** HCE 服务的 AID，与 aid_filter.xml 中保持一致 */
    private const val HCE_AID_HEX = "F05A4C4541524E"

    /**
     * 尝试通过 IsoDep 读取 HCE (Host-based Card Emulation) 服务暴露的 sessionId。
     *
     * 必须先用 SELECT AID 通知 Android NFC 控制器将后续 APDU 路由到我们的
     * [ShareHceService]，否则默认路由到钱包（交通卡/门禁卡）等其他 HCE 服务。
     *
     * 协议：SELECT by AID → 9000 → READ BINARY → sessionId + 9000
     *
     * @return 成功读到的 sessionId 字符串，失败时返回 null。
     */
    fun extractPayloadFromIsoDep(tag: Tag?): String? {
        if (tag == null) return null

        val isoDep = IsoDep.get(tag) ?: return null
        return runCatching {
            isoDep.connect()
            isoDep.timeout = 3000

            // 1. SELECT by AID — 这一步是关键：让 NFC 控制器路由到我们的 HCE 服务
            val aidBytes = hexToBytes(HCE_AID_HEX)
            val selectApdu = byteArrayOf(
                0x00.toByte(),            // CLA
                0xA4.toByte(),            // INS: SELECT
                0x04.toByte(),            // P1: by AID
                0x00.toByte(),            // P2
                aidBytes.size.toByte()    // Lc: AID length
            ) + aidBytes + byteArrayOf(0x00.toByte()) // AID bytes + Le

            Log.d("NfcUtil", "HCE SELECT AID: $HCE_AID_HEX")
            val selectResponse = isoDep.transceive(selectApdu)
            if (selectResponse.size < 2
                || selectResponse[selectResponse.size - 2] != 0x90.toByte()
                || selectResponse[selectResponse.size - 1] != 0x00.toByte()
            ) {
                Log.w("NfcUtil", "HCE SELECT failed: ${selectResponse.toHex()}")
                return@runCatching null
            }

            // 2. READ BINARY: 读取 sessionId 数据
            val readApdu = byteArrayOf(
                0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
            )
            val response = isoDep.transceive(readApdu)
            // 响应 = data + 2 字节状态字 (SW1 SW2)
            if (response.size >= 2
                && response[response.size - 2] == 0x90.toByte()
                && response[response.size - 1] == 0x00.toByte()
            ) {
                val data = response.copyOfRange(0, response.size - 2)
                String(data, Charsets.UTF_8)
            } else {
                Log.w("NfcUtil", "HCE READ response status not OK: ${response.toHex()}")
                null
            }
        }.getOrNull()?.also {
            runCatching { isoDep.close() }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = " ") { b -> "%02X".format(b) }

    private fun decodeRecordToText(record: NdefRecord): String? {
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null

        return if (
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
            record.type.contentEquals(NdefRecord.RTD_TEXT)
        ) {
            val status = payload[0].toInt()
            val languageLength = status and 0x3F
            val textStart = 1 + languageLength
            if (textStart >= payload.size) return null
            payload.copyOfRange(textStart, payload.size).toString(Charsets.UTF_8)
        } else {
            payload.toString(Charsets.UTF_8)
        }
    }
}
