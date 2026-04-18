package com.zlearn.ui.question.screens

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun QuestionCard(summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding(16.dp),
            maxLines = 1
        )
    }
}
