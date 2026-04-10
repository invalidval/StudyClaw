package com.zlearn.ui.question.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontWeight

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
            FloatingActionButton(
                onClick = { navController.navigate("add_question") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            // --- 顶部控制栏 ---
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 筛选按钮
                    FilterChip(
                        selected = selectedArchiveType != null,
                        onClick = { showFilterMenu = true },
                        label = {
                            val displayText = when {
                                selectedArchiveType == null -> "全部归档"
                                selectedArchiveType == "" -> "未归档"
                                else -> selectedArchiveType!!
                            }
                            Text(text = displayText)
                        },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (selectedArchiveType != null) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp).clickable { selectedArchiveType = null }
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 同步按钮 (IconButton 风格)
                    FilledTonalIconButton(
                        onClick = {
                            if (token.isNullOrBlank()) {
                                isRegisterMode = false
                                showLoginDialog = true
                            } else {
                                viewModel.syncQuestions(token!!)
                            }
                        },
                        enabled = !syncing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 退出登录按钮 (简洁风格)
                    if (!token.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { showLogoutDialog = true },
                            border = null,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("登出", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // 筛选菜单
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                selectedArchiveType = null
                                showFilterMenu = false
                            },
                            text = { Text("全部") }
                        )
                        DropdownMenuItem(
                            onClick = {
                                selectedArchiveType = ""
                                viewModel.filterQuestionsByArchiveType("")
                                showFilterMenu = false
                            },
                            text = { Text("未归档") }
                        )
                        archiveTypes.filter { it.isNotBlank() }.forEach { type ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedArchiveType = type
                                    viewModel.filterQuestionsByArchiveType(type)
                                    showFilterMenu = false
                                },
                                text = { Text(type) }
                            )
                        }
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
            var passwordVisible by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = {
                    if (authState !is AuthState.Loading) {
                        showLoginDialog = false
                        isRegisterMode = false
                        userViewModel.resetAuthState()
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                confirmButton = {
                    Button(
                        onClick = {
                            if (isRegisterMode) {
                                userViewModel.register(username, password)
                            } else {
                                userViewModel.login(username, password)
                            }
                        },
                        enabled = authState !is AuthState.Loading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isRegisterMode) "立即注册" else "登录")
                        }
                    }
                },
                dismissButton = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = {
                                isRegisterMode = !isRegisterMode
                                userViewModel.resetAuthState()
                            },
                            enabled = authState !is AuthState.Loading
                        ) {
                            Text(
                                if (isRegisterMode) "已有账号？点击登录" else "没有账号？点击注册",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (isRegisterMode) "创建账号" else "欢迎回来",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isRegisterMode) "注册以同步您的学习资料" else "请登录以继续同步",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            placeholder = { Text("请输入用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            placeholder = { Text("请输入密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        if (authState is AuthState.Error) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = (authState as AuthState.Error).message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
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
