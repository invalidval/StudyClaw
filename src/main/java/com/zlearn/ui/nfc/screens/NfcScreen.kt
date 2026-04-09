package com.zlearn.ui.nfc.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NfcScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "NFC 功能区")
        // TODO: 展示 NFC 读写、标签状态等
    }
}

