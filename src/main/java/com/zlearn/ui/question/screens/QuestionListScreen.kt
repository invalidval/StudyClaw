package com.zlearn.ui.question.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.data.database.QuestionEntity
import com.zlearn.ui.question.components.GroupItemPosition
import com.zlearn.ui.question.components.QuestionCard
import androidx.navigation.NavController
import com.zlearn.viewmodel.AuthState
import com.zlearn.viewmodel.UserViewModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    modifier: Modifier = Modifier,
    viewModel: QuestionViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel(),
    navController: NavController
) {
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedQuestion: QuestionEntity? by remember { mutableStateOf(null) }
    var selectedArchiveType by remember { mutableStateOf<String?>(null) }
    val questions by viewModel.questions.collectAsState()
    // 自动刷新题目列表和归档类型
    LaunchedEffect(viewModel) {
        viewModel.loadQuestions()
        viewModel.loadArchiveTypes()
    }
    // 监听筛选归档类型变化，自动刷新filteredQuestions
    LaunchedEffect(selectedArchiveType) {
        if (selectedArchiveType != null) {
            viewModel.filterQuestionsByArchiveType(selectedArchiveType!!)
        } else {
            viewModel.loadQuestions()
        }
    }
    val archiveTypes by viewModel.archiveTypes.collectAsState()
    val filteredQuestions by viewModel.filteredQuestions.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val authState by userViewModel.authState.collectAsState()
    val token by userViewModel.token.collectAsState()
    var showArchiveTypeDialog by remember { mutableStateOf(false) }
    var archiveTypeInput by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val savedToken = token
            if (!savedToken.isNullOrBlank()) {
                showLoginDialog = false
                userViewModel.resetAuthState()
                viewModel.syncQuestions(savedToken)
            }
        }
    }

    LaunchedEffect(syncMessage) {
        if (!syncMessage.isNullOrBlank()) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add_question") }) {
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
                Button(
                    onClick = {
                        if (token.isNullOrBlank()) {
                            isRegisterMode = false
                            showLoginDialog = true
                        } else {
                            viewModel.syncQuestions(token!!)
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (syncing) "同步中..." else "同步")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!token.isNullOrBlank()) {
                    TextButton(
                        onClick = { showLogoutDialog = true },
                        enabled = !syncing
                    ) {
                        Text("退出登录")
                    }
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
            if (!syncMessage.isNullOrBlank()) {
                Text(
                    text = syncMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            // --- 分组展示归档内容 ---
            val displayQuestions = if (selectedArchiveType != null) filteredQuestions else questions
            if (selectedArchiveType != null) {
                // 只展示筛选结果，不分组
                val selectedTypeColor = archiveTypeColor(selectedArchiveType.orEmpty(), isDarkTheme)
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(displayQuestions.size) { index ->
                        val q = displayQuestions[index]
                        val position = when {
                            displayQuestions.size == 1 -> GroupItemPosition.Single
                            index == 0 -> GroupItemPosition.First
                            index == displayQuestions.lastIndex -> GroupItemPosition.Last
                            else -> GroupItemPosition.Middle
                        }
                        QuestionCard(
                            summary = if (q.summary.isNotBlank()) q.summary else q.ocrText.take(20),
                            position = position,
                            containerColor = selectedTypeColor,
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
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    archiveTypeList.forEach { type ->
                        val groupTitle = if (type.isBlank()) "未归档" else type
                        val groupQuestions = grouped[type] ?: emptyList()
                        val groupColor = archiveTypeColor(type, isDarkTheme)
                        if (groupQuestions.isNotEmpty()) {
                            item {
                                Text(
                                    groupTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 14.dp, start = 12.dp, bottom = 6.dp)
                                )
                            }
                            items(groupQuestions.size) { index ->
                                val q = groupQuestions[index]
                                val position = when {
                                    groupQuestions.size == 1 -> GroupItemPosition.Single
                                    index == 0 -> GroupItemPosition.First
                                    index == groupQuestions.lastIndex -> GroupItemPosition.Last
                                    else -> GroupItemPosition.Middle
                                }
                                QuestionCard(
                                    summary = if (q.summary.isNotBlank()) q.summary else q.ocrText.take(20),
                                    position = position,
                                    containerColor = groupColor,
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
        // add dialog removed: now using dedicated add_question page
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
        if (showLoginDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (authState !is AuthState.Loading) {
                        showLoginDialog = false
                        isRegisterMode = false
                        userViewModel.resetAuthState()
                    }
                },
                title = { Text(if (isRegisterMode) "注册后同步" else "登录后同步") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        if (authState is AuthState.Error) {
                            Text(
                                text = (authState as AuthState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isRegisterMode) {
                                userViewModel.register(username, password)
                            } else {
                                userViewModel.login(username, password)
                            }
                        },
                        enabled = authState !is AuthState.Loading
                    ) {
                        val loadingText = if (isRegisterMode) "注册中..." else "登录中..."
                        val normalText = if (isRegisterMode) "注册并同步" else "登录并同步"
                        Text(if (authState is AuthState.Loading) loadingText else normalText)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                isRegisterMode = !isRegisterMode
                                userViewModel.resetAuthState()
                            },
                            enabled = authState !is AuthState.Loading
                        ) {
                            Text(if (isRegisterMode) "已有账号？去登录" else "没有账号？去注册")
                        }
                        TextButton(
                            onClick = {
                                showLoginDialog = false
                                isRegisterMode = false
                                userViewModel.resetAuthState()
                            },
                            enabled = authState !is AuthState.Loading
                        ) {
                            Text("取消")
                        }
                    }
                }
            )
        }
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("退出登录") },
                text = { Text("确认退出当前账号吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        userViewModel.logout()
                        userViewModel.resetAuthState()
                        viewModel.clearSyncMessage()
                        showLoginDialog = false
                        isRegisterMode = false
                        showLogoutDialog = false
                        username = ""
                        password = ""
                    }) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("取消")
                    }
                }
            )
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

private fun archiveTypeColor(archiveType: String, isDarkTheme: Boolean): Color {
    val lightPalette = listOf(
        Color(0xFFFFF5D9),
        Color(0xFFE9F7E8),
        Color(0xFFEAF2FF),
        Color(0xFFFFEDEA),
        Color(0xFFF3EEFF),
        Color(0xFFE8F7F7),
        Color(0xFFFFF0E0),
        Color(0xFFEFF3D8)
    )
    val darkPalette = listOf(
        Color(0xFF4A4330),
        Color(0xFF304536),
        Color(0xFF2F3F58),
        Color(0xFF533638),
        Color(0xFF403654),
        Color(0xFF2E4648),
        Color(0xFF544335),
        Color(0xFF3F4731)
    )
    val key = if (archiveType.isBlank()) "__ungrouped__" else archiveType
    val colors = if (isDarkTheme) darkPalette else lightPalette
    return colors[key.hashCode().absoluteValue % colors.size]
}

