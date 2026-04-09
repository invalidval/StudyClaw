package com.zlearn.ui.question.screens

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuestionCard(title: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Text(text = title)
    }
}

