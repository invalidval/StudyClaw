package com.zlearn

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.zlearn.ui.SplashScreen
import com.zlearn.ui.theme.MyApplicationTheme
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcUtil
import com.zlearn.utils.ShareHceService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var readerModeEnabled = false
    private var foregroundDispatchEnabled = false
    private val readerFlags = NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V or
        NfcAdapter.FLAG_READER_NFC_BARCODE or
        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        BleShareTransport.init(this)
        handleNfcIntent(intent)

        lifecycleScope.launch {
            NfcUtil.receiverEnabled.collect { enabled ->
                if (enabled) enableReaderModeIfNeeded() else disableReaderModeIfNeeded()
            }
        }
        lifecycleScope.launch {
            NfcUtil.senderEnabled.collect { enabled ->
                if (enabled) enableForegroundNfcDispatch() else disableForegroundNfcDispatch()
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SplashScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        disableReaderModeIfNeeded()
        disableForegroundNfcDispatch()
    }

    override fun onResume() {
        super.onResume()
        // 恢复前台调度（如果发送模式仍活跃）
        if (NfcUtil.senderEnabled.value) enableForegroundNfcDispatch()
    }

    private fun handleNfcIntent(intent: Intent?) {
        // 发送模式下不处理任何 NFC Intent（由接收方读发送方，发送方仅做 HCE 响应）
        if (NfcUtil.senderEnabled.value) return
        val payload = NfcUtil.extractNfcTextPayload(intent) ?: return
        if (payload.isNotBlank()) {
            NfcUtil.publishIncomingPayload(payload)
        }
    }

    private fun enableReaderModeIfNeeded() {
        if (readerModeEnabled) return
        val adapter = nfcAdapter ?: return
        adapter.enableReaderMode(this, { tag ->
            val isoDepPayload = NfcUtil.extractPayloadFromIsoDep(tag)
            val tagPayload = isoDepPayload ?: NfcUtil.extractPayloadFromTag(tag)
            Log.d("MainActivity", "NFC tag detected: isoDep=${isoDepPayload?.take(40)}, ndef=${tagPayload?.take(40)}")
            if (!tagPayload.isNullOrBlank()) {
                NfcUtil.publishIncomingPayload(tagPayload)
            }
        }, readerFlags, null)
        readerModeEnabled = true
    }

    private fun disableReaderModeIfNeeded() {
        if (!readerModeEnabled) return
        nfcAdapter?.disableReaderMode(this)
        readerModeEnabled = false
    }

    // ── 发送端前台调度：拦截 NFC Intent，防止跳转其他应用 ──
    private fun enableForegroundNfcDispatch() {
        if (foregroundDispatchEnabled) return
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        foregroundDispatchEnabled = true
        Log.d("MainActivity", "foreground NFC dispatch enabled (sender mode)")
    }

    private fun disableForegroundNfcDispatch() {
        if (!foregroundDispatchEnabled) return
        nfcAdapter?.disableForegroundDispatch(this)
        foregroundDispatchEnabled = false
        Log.d("MainActivity", "foreground NFC dispatch disabled")
    }
}
