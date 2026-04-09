package com.zlearn.ui.focus.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "专注模式")
        // TODO: 展示专注计时、白噪音等
    }
}

