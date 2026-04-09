package com.zlearn.ui.question.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.data.database.QuestionEntity
import com.zlearn.ui.question.components.QuestionCard
import androidx.compose.foundation.text.BasicTextField
import kotlinx.coroutines.delay
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.zlearn.ocr.OcrUtil
import kotlinx.coroutines.launch

@Composable
fun QuestionListScreen(
    modifier: Modifier = Modifier,
    viewModel: QuestionViewModel = hiltViewModel(),
    navController: NavController
) {
    val questions by viewModel.questions.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    var summaryResponse by remember { mutableStateOf("") }
    var summaryLoading by remember { mutableStateOf(false) }
    var aiInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newOcrText by remember { mutableStateOf("") }
    var newSubject by remember { mutableStateOf("") }
    var newDifficulty by remember { mutableStateOf(3) }
    var newAiAnalysis by remember { mutableStateOf("") }
    var newSummary by remember { mutableStateOf("") }
    var newImagePath by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var ocrImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        ocrImageUri = uri
        if (uri != null) {
            ocrLoading = true
            coroutineScope.launch {
                val ocrResult = OcrUtil.recognizeTextFromUri(context, uri)
                ocrLoading = false
                if (ocrResult != null) {
                    newOcrText = ocrResult
                    // OCR后自动生成summary（独立调用AI）
                    summaryLoading = true
                    summaryResponse = ""
                    coroutineScope.launch {
                        val summary = viewModel.generateSummary(ocrResult)
                        summaryResponse = summary
                        newSummary = if (summary.length > 20) summary.take(20) else summary
                        summaryLoading = false
                    }
                    // 识别成功反馈
                    android.widget.Toast.makeText(context, "识别成功", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "识别失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            // AI对话区
            OutlinedTextField(
                value = aiInput,
                onValueChange = { aiInput = it },
                label = { Text("和大模型对话...") },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            Button(onClick = { viewModel.chatWithAi(aiInput) }, enabled = aiInput.isNotBlank()) {
                Text("发送到AI")
            }
            // 渐进式AI回复
            var animatedReply by remember { mutableStateOf("") }
            val lastResponse = aiResponse ?: ""
            LaunchedEffect(lastResponse) {
                animatedReply = ""
                for (i in lastResponse.indices) {
                    animatedReply = lastResponse.substring(0, i + 1)
                    delay(18) // 打字速度，可调整
                }
            }
            if (!aiResponse.isNullOrBlank()) {
                Text("AI回复：$animatedReply", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary)
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(questions.size) { index ->
                    val q = questions[index]
                    QuestionCard(summary = if (q.summary.isNotBlank()) q.summary else q.ocrText.take(20), onClick = {
                        println("[DEBUG] Card clicked, id=${q.id}")
                        navController.navigate("question_detail/${q.id}")
                    })
                }
            }
        }
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加题目") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newOcrText,
                            onValueChange = { newOcrText = it },
                            label = { Text("题干/OCR文本") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        OutlinedTextField(
                            value = newSubject,
                            onValueChange = { newSubject = it },
                            label = { Text("学科") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        OutlinedTextField(
                            value = newAiAnalysis,
                            onValueChange = { newAiAnalysis = it },
                            label = { Text("AI解析(可选)") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                         OutlinedTextField(
                             value = newImagePath,
                             onValueChange = { newImagePath = it },
                             label = { Text("图片路径(可选)") },
                             modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                         )
                        // summary只读或隐藏，若需显示可用只读框
                        OutlinedTextField(
                            value = newSummary,
                            onValueChange = {},
                            label = { Text("AI摘要(自动生成)") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            enabled = false,
                            readOnly = true
                        )
                        if (summaryLoading) {
                            Text("正在生成摘要...", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("难度:")
                            Slider(
                                value = newDifficulty.toFloat(),
                                onValueChange = { newDifficulty = it.toInt() },
                                valueRange = 1f..5f,
                                steps = 3,
                                modifier = Modifier.weight(1f)
                            )
                            Text(newDifficulty.toString())
                        }
                        // 新增：OCR图片选择与识别按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        pickImageLauncher.launch("image/*")
                                    }, enabled = !ocrLoading) {
                                        Text("选择图片/拍照")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (ocrLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else if (ocrImageUri != null) {
                                        Text("已选择图片")
                                    }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newOcrText.isNotBlank()) {
                            viewModel.addQuestion(
                                QuestionEntity(
                                    imagePath = newImagePath,
                                    ocrText = newOcrText,
                                    aiAnalysis = newAiAnalysis,
                                    summary = newSummary,
                                    subject = newSubject,
                                    difficulty = newDifficulty,
                                    createTime = System.currentTimeMillis(),
                                    isArchived = false
                                )
                            )
                            showAddDialog = false
                            newOcrText = ""
                            newSubject = ""
                            newAiAnalysis = ""
                            newSummary = ""
                            newImagePath = ""
                            newDifficulty = 3
                        }
                    }) { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                }
            )
        }
    }
    // AI解析自动填充（仅用于AI解析，不影响summary）
    LaunchedEffect(aiResponse) {
        if (!aiResponse.isNullOrBlank()) {
            newAiAnalysis = aiResponse ?: ""
        }
    }
}
