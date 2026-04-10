package com.zlearn.ui.question.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zlearn.data.database.QuestionEntity
import com.zlearn.domain.model.ShareItem
import com.zlearn.domain.model.SharePayload
import com.zlearn.ui.components.AiInputBar  // 导入封装组件
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcShareCodec
import com.zlearn.utils.NfcUtil
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun QuestionDetailScreen(
    id: Int,
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    viewModel: QuestionViewModel = hiltViewModel()
) {
    val questions by viewModel.questions.collectAsState()
    val aiStreamResponse by viewModel.aiStreamResponse.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()  // 需要在 ViewModel 中添加

    val question = questions.find { it.id == id }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    if (question == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到题目或数据正在加载...\nID: $id")
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 可滚动内容
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // 题目卡片（保持原有代码）
            QuestionCard(question = question)

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    val sessionId = UUID.randomUUID().toString()
                    NfcUtil.setOutgoingPayload(sessionId)
                    val hashInput = "${question.ocrText}|${question.summary}|${question.subject}"
                    val hash = MessageDigest.getInstance("SHA-256")
                        .digest(hashInput.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    val payload = SharePayload(
                        sessionId = sessionId,
                        senderDevice = android.os.Build.MODEL ?: "android",
                        createdAt = System.currentTimeMillis(),
                        items = listOf(
                            ShareItem(
                                contentHash = hash,
                                ocrText = question.ocrText,
                                summary = question.summary,
                                subject = question.subject,
                                difficulty = question.difficulty,
                                aiAnalysis = question.aiAnalysis,
                                isArchived = question.isArchived,
                                archiveType = question.archiveType ?: ""
                            )
                        )
                    )
                    val encoded = NfcShareCodec.encode(payload)
                    BleShareTransport.startAdvertising(context, encoded)
                    android.widget.Toast.makeText(context, "已准备分享，请让接收方打开NFC页后轻触", android.widget.Toast.LENGTH_SHORT).show()
                    navController?.navigate("nfc/sender")
                }) {
                    Text("NFC分享")
                }
            }

            // AI 回复内容
            if (aiStreamResponse.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("大模型回复：", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 20.dp))
                Markdown(content = aiStreamResponse, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }

            Spacer(modifier = Modifier.height(80.dp))  // 给底部输入框留空间
        }

        // 底部悬浮输入栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
                .padding(16.dp)
        ) {
            AiInputBar(
                input = input,
                onInputChange = { input = it },
                onSend = {
                    if (input.startsWith("/wa ")) {
                        val newAnalysis = input.removePrefix("/wa ").trim()
                        viewModel.updateAiAnalysis(id, newAnalysis)
                        input = ""
                    } else if (input.isNotBlank()) {
                        val prompt = buildString {
                            append("你现在是答题助手。请结合如下题目信息回答用户问题。你的回答必须简短，不使用emoji。\n")
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
                        input = ""
                    }
                },
                isLoading = isLoading,
                placeholder = "试试智能小助手",
                buttonText = "发送"
            )
        }
    }
}

// 将题目卡片抽取为独立组件（可选，保持代码整洁）
@Composable
fun QuestionCard(question: QuestionEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
        }
    }
}