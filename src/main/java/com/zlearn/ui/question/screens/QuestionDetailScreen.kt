package com.zlearn.ui.question.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography


@Composable
fun QuestionDetailScreen(id: Int, modifier: Modifier = Modifier, viewModel: QuestionViewModel = hiltViewModel()) {
    val questions by viewModel.questions.collectAsState()
    // 调试输出
    LaunchedEffect(questions, id) {
        println("[DEBUG] DetailScreen id=$id, questions=${questions.map { it.id }}")
    }
    val question = questions.find { it.id == id }
    if (question == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到题目或数据正在加载...\nID: $id", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "题目详情", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "题干：", style = MaterialTheme.typography.labelMedium)
        Markdown(content = question.ocrText, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "学科：${question.subject}")
        Text(text = "难度：${question.difficulty}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "AI解析：", style = MaterialTheme.typography.labelMedium)
        Markdown(content = question.aiAnalysis, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        if (question.imagePath.isNotBlank()) {
            Text(text = "图片路径：${question.imagePath}")
            // 可选：加载图片展示
        }
        Text(text = "创建时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(question.createTime))}")
    }
}
