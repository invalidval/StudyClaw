package com.zlearn.utils

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * HCE (Host-based Card Emulation) 服务，在发送模式时通过 NFC 向接收方暴露 sessionId。
 *
 * 协议：
 *   SELECT (00 A4 04) → 返回 9000 表示成功
 *   其他 APDU     → 返回 sessionId 数据 + 9000
 *
 * 仅当 [sessionData] 非空（发送方已设置待分享会话）时才会响应数据，
 * 否则返回 6A82 (file not found)，由系统路由到钱包等其他 HCE 服务。
 */
class ShareHceService : HostApduService() {

    companion object {
        private const val TAG = "ShareHceService"

        /** SELECT 成功的状态字 */
        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00)

        /** 未找到数据（会话未初始化） */
        val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        @Volatile
        private var sessionData: ByteArray? = null

        @Volatile
        var isHceActive: Boolean = false
            private set

        fun setSessionData(sessionId: String) {
            sessionData = sessionId.toByteArray(Charsets.UTF_8)
            isHceActive = true
            Log.d(TAG, "HCE session data set, sessionId=$sessionId")
        }

        fun clearSessionData() {
            sessionData = null
            isHceActive = false
            Log.d(TAG, "HCE session data cleared")
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (sessionData == null) {
            Log.d(TAG, "HCE APDU ignored: no session data")
            return STATUS_FILE_NOT_FOUND
        }

        if (commandApdu.size < 4) {
            return STATUS_FILE_NOT_FOUND
        }

        val cla = commandApdu[0].toInt() and 0xFF
        val ins = commandApdu[1].toInt() and 0xFF
        val p1 = commandApdu[2].toInt() and 0xFF

        Log.d(TAG, "HCE APDU received: CLA=${String.format("%02X", cla)} INS=${String.format("%02X", ins)} P1=${String.format("%02X", p1)}")

        return when {
            // SELECT by AID: 00 A4 04 xx
            cla == 0x00 && ins == 0xA4 && p1 == 0x04 -> {
                Log.d(TAG, "HCE SELECT acknowledged")
                STATUS_SUCCESS
            }
            // READ BINARY or generic read: return session data
            else -> {
                val data = sessionData!!
                Log.d(TAG, "HCE returning session data, bytes=${data.size}")
                data + STATUS_SUCCESS
            }
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE deactivated, reason=$reason")
    }
}
