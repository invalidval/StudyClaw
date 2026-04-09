package com.zlearn.ui.question.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QuestionListScreen(modifier: Modifier = Modifier) {
    // TODO: Replace with real data and ViewModel
    val questions = listOf("题目1", "题目2", "题目3")
    LazyColumn(modifier = modifier) {
        items(questions.size) { index ->
            QuestionCard(title = questions[index])
        }
    }
}

@Composable
fun QuestionDetailScreen(question: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "题目详情：$question")
        // TODO: 展示题目图片、AI解析、备注等
    }
}

