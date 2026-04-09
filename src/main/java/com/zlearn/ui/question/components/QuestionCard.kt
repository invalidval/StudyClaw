package com.zlearn.ui.question.components

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable

@Composable
fun QuestionCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Text(text = title, modifier = Modifier.padding(16.dp))
    }
}
