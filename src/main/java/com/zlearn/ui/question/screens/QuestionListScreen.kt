package com.zlearn.ui.question.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.zlearn.ocr.OcrUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    modifier: Modifier = Modifier,
    viewModel: QuestionViewModel = hiltViewModel(),
    navController: NavController
) {
    val questions by viewModel.questions.collectAsState()
    var summaryLoading by remember { mutableStateOf(false) }
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
                    newSummary = ""
                    coroutineScope.launch {
                        val summary = viewModel.generateSummary(ocrResult)
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
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedQuestion: QuestionEntity? by remember { mutableStateOf(null) }
    var selectedArchiveType by remember { mutableStateOf<String?>(null) }
    val archiveTypes by viewModel.archiveTypes.collectAsState()
    val filteredQuestions by viewModel.filteredQuestions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showArchiveTypeDialog by remember { mutableStateOf(false) }
    var archiveTypeInput by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            // --- 筛选归档类型 ---
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFilterMenu = true }) {
                    Text("筛选归档:")
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    DropdownMenuItem(onClick = {
                        selectedArchiveType = null
                        showFilterMenu = false
                    }, text = { Text("全部") })
                    archiveTypes.forEach { type ->
                        DropdownMenuItem(onClick = {
                            selectedArchiveType = type
                            viewModel.filterQuestionsByArchiveType(type)
                            showFilterMenu = false
                        }, text = { Text(type) })
                    }
                }
                if (selectedArchiveType != null) {
                    Button(onClick = {
                        selectedArchiveType = null
                    }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("清除筛选")
                    }
                }
            }
            // --- 分组展示归档内容 ---
            val displayQuestions = if (selectedArchiveType != null) filteredQuestions else questions
            if (selectedArchiveType != null) {
                // 只展示筛选结果，不分组
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayQuestions.size) { index ->
                        val q = displayQuestions[index]
                        QuestionCard(
                            summary = if (q.summary.isNotBlank()) q.summary else q.ocrText.take(20),
                            onClick = {
                                println("[DEBUG] Card clicked, id=${q.id}")
                                navController.navigate("question_detail/${q.id}")
                            },
                            onLongPress = {
                                selectedQuestion = q
                                showActionSheet = true
                            }
                        )
                    }
                }
            } else {
                // 分组展示所有归档内容
                val allQuestions = questions
                val grouped = allQuestions.groupBy { it.archiveType ?: "" }
                val archiveTypeList = listOf("") + archiveTypes.filter { it.isNotBlank() && it != "" }
                archiveTypeList.forEach { type ->
                    val groupTitle = if (type.isBlank()) "未归档" else type
                    val groupQuestions = grouped[type] ?: emptyList()
                    if (groupQuestions.isNotEmpty()) {
                        Text(groupTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, start = 12.dp, bottom = 4.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth(), userScrollEnabled = false) {
                            items(groupQuestions.size) { index ->
                                val q = groupQuestions[index]
                                QuestionCard(
                                    summary = if (q.summary.isNotBlank()) q.summary else q.ocrText.take(20),
                                    onClick = {
                                        println("[DEBUG] Card clicked, id=${q.id}")
                                        navController.navigate("question_detail/${q.id}")
                                    },
                                    onLongPress = {
                                        selectedQuestion = q
                                        showActionSheet = true
                                    }
                                )
                            }
                        }
                    }
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
        if (showActionSheet && selectedQuestion != null) {
            ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
                Column(Modifier.padding(16.dp)) {
                    TextButton(onClick = {
                        viewModel.deleteQuestion(selectedQuestion!!)
                        showActionSheet = false
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        showArchiveTypeDialog = true
                        showActionSheet = false
                        archiveTypeInput = ""
                    }) { Text("归档") }
                    TextButton(onClick = { showActionSheet = false }) { Text("取消") }
                }
            }
        }
        if (showArchiveTypeDialog && selectedQuestion != null) {
            AlertDialog(
                onDismissRequest = { showArchiveTypeDialog = false },
                title = { Text("选择或输入归档类型") },
                text = {
                    Column {
                        archiveTypes.forEach { type ->
                            Button(onClick = {
                                viewModel.archiveQuestion(selectedQuestion!!.id, type)
                                showArchiveTypeDialog = false
                            }, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(type)
                            }
                        }
                        OutlinedTextField(
                            value = archiveTypeInput,
                            onValueChange = { archiveTypeInput = it },
                            label = { Text("新建归档类型") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (archiveTypeInput.isNotBlank()) {
                            viewModel.archiveQuestion(selectedQuestion!!.id, archiveTypeInput)
                            showArchiveTypeDialog = false
                        }
                    }) { Text("确定归档") }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveTypeDialog = false }) { Text("取消") }
                }
            )
        }
    }

}
