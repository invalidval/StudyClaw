package com.zlearn

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.zlearn.ui.SplashScreen
import com.zlearn.ui.theme.MyApplicationTheme
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var readerModeEnabled = false
    private val readerFlags = NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V or
        NfcAdapter.FLAG_READER_NFC_BARCODE

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
    }

    private fun handleNfcIntent(intent: Intent?) {
        val payload = NfcUtil.extractNfcTextPayload(intent) ?: return
        if (payload.isNotBlank()) {
            NfcUtil.publishIncomingPayload(payload)
        }
    }

    private fun enableReaderModeIfNeeded() {
        if (readerModeEnabled) return
        val adapter = nfcAdapter ?: return
        adapter.enableReaderMode(this, { tag ->
            val payload = NfcUtil.extractPayloadFromTag(tag)
            if (!payload.isNullOrBlank()) {
                NfcUtil.publishIncomingPayload(payload)
            }
        }, readerFlags, null)
        readerModeEnabled = true
    }

    private fun disableReaderModeIfNeeded() {
        if (!readerModeEnabled) return
        nfcAdapter?.disableReaderMode(this)
        readerModeEnabled = false
    }
}
