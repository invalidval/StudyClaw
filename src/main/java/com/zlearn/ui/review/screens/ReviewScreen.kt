package com.zlearn.ui.review.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "复习区")
        // TODO: 展示归档错题、复习进度等
    }
}

