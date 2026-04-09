package com.zlearn.ui.question.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment.Companion.CenterHorizontally


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
    val aiStreamResponse by viewModel.aiStreamResponse.collectAsState()
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    Card(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "题目详情", style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(text = "题干", style = MaterialTheme.typography.titleMedium)
            Markdown(content = question.ocrText, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            HorizontalDivider()
            Row(Modifier.padding(vertical = 8.dp)) {
                Text(text = "学科：${question.subject}", modifier = Modifier.weight(1f))
                Text(text = "难度：${question.difficulty}")
            }
            HorizontalDivider()
            Text(text = "AI解析", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Markdown(content = question.aiAnalysis, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            HorizontalDivider()
            if (question.imagePath.isNotBlank()) {
                Text(text = "题目图片", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                // 图片加载示例（如用 Coil 可替换）
                /*
                Image(
                    painter = rememberAsyncImagePainter(question.imagePath),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .align(CenterHorizontally),
                    contentScale = ContentScale.Crop
                )
                */
                Text(text = question.imagePath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(CenterHorizontally))
            }
            Text(
                text = "创建时间：" + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(question.createTime)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("向大模型提问或输入 /wa 写入解析") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (input.startsWith("/wa ")) {
                        val newAnalysis = input.removePrefix("/wa ").trim()
                        viewModel.updateAiAnalysis(id, newAnalysis)
                    } else if (input.isNotBlank()) {
                        val prompt = buildString {
                            append("你现在是答题助手。请结合如下题目信息回答用户问题。\n")
                            append("题干：${question.ocrText}\n")
                            append("学科：${question.subject}\n")
                            append("难度：${question.difficulty}\n")
                            if (question.aiAnalysis.isNotBlank()) {
                                append("已有AI解析：${question.aiAnalysis}\n")
                            }
                            append("用户提问：$input")
                        }
                        viewModel.clearAiStreamResponse()
                        scope.launch {
                            viewModel.chatWithAiStream(prompt)
                        }
                    }
                    input = ""
                },
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("发送")
            }
            if (aiStreamResponse.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("大模型回复：", style = MaterialTheme.typography.labelMedium)
                Markdown(content = aiStreamResponse, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
