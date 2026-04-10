package com.zlearn.utils

import android.content.Intent
import android.os.Build
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object NfcUtil {
    private val _incomingPayload = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val incomingPayload: SharedFlow<String> = _incomingPayload.asSharedFlow()

    private val _receiverEnabled = MutableStateFlow(false)
    val receiverEnabled: StateFlow<Boolean> = _receiverEnabled.asStateFlow()

    private val _outgoingPayload = MutableStateFlow<String?>(null)
    val outgoingPayload: StateFlow<String?> = _outgoingPayload.asStateFlow()

    fun setReceiverEnabled(enabled: Boolean) {
        _receiverEnabled.value = enabled
    }

    fun setOutgoingPayload(payload: String?) {
        _outgoingPayload.value = payload
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
