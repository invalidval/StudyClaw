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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import com.zlearn.data.database.QuestionEntity
import com.zlearn.domain.model.ShareItem
import com.zlearn.domain.model.SharePayload
import com.zlearn.ui.components.AiInputBar  // 导入封装组件
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcShareCodec
import com.zlearn.utils.NfcUtil
import com.zlearn.utils.PermissionUtil
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
@Suppress("ModifierParameter")
fun QuestionDetailScreen(
    id: Int,
    modifier: Modifier = Modifier,
    navController: NavController?,
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
    val uiHandler = remember { Handler(Looper.getMainLooper()) }
    val requiredBlePermissions = remember { PermissionUtil.requiredBlePermissions() }
    var pendingShareStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (!granted) {
            android.widget.Toast.makeText(context, "缺少 BLE 权限，无法开始分享", android.widget.Toast.LENGTH_SHORT).show()
            pendingShareStart = false
            return@rememberLauncherForActivityResult
        }
        if (pendingShareStart) {
            pendingShareStart = false
            question?.let {
                startShare(
                    question = it,
                    context = context,
                    navController = navController,
                    uiHandler = uiHandler
                )
            }
        }
    }

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
                    if (!PermissionUtil.hasBlePermissions(context)) {
                        pendingShareStart = true
                        permissionLauncher.launch(requiredBlePermissions)
                    } else {
                        startShare(
                            question = question,
                            context = context,
                            navController = navController,
                            uiHandler = uiHandler
                        )
                    }
                }) {
                    Text("NFC分享")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = {
                    navController?.navigate("qrcode/generate/${question.id}")
                }) {
                    Text("生成二维码")
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
                placeholder = "问问大模型，或输入 /wa 更新AI解析",
                buttonText = "发送"
            )
        }
    }
}

private fun startShare(
    question: QuestionEntity,
    context: android.content.Context,
    navController: NavController?,
    uiHandler: Handler
) {
    val sessionId = UUID.randomUUID().toString()
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

    BleShareTransport.startAdvertising(context, encoded) { result ->
        uiHandler.post {
            when (result) {
                BleShareTransport.AdvertiseStartResult.Started -> {
                    // Register ACK listener after advertising starts. startAdvertising() calls
                    // stopAdvertising() internally, which clears previous listeners.
                    BleShareTransport.setOnAckReceivedListener { ackSessionId ->
                        if (ackSessionId == sessionId) {
                            uiHandler.post {
                                NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Completed("对方已收到并导入"))
                                NfcUtil.setOutgoingPayload(null)
                                BleShareTransport.stopAdvertising()
                                android.widget.Toast.makeText(context, "对方已收到并导入", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.WaitingAck(sessionId))
                    NfcUtil.setOutgoingPayload(sessionId)
                    android.widget.Toast.makeText(context, "已开始发送，等待对方确认", android.widget.Toast.LENGTH_SHORT).show()
                    navController?.navigate("nfc/sender")
                }

                BleShareTransport.AdvertiseStartResult.NotInitialized -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("BLE 未初始化，请重试"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "BLE 未初始化，请重试", android.widget.Toast.LENGTH_SHORT).show()
                }

                BleShareTransport.AdvertiseStartResult.BluetoothDisabled -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("蓝牙未开启，请先打开蓝牙"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "蓝牙未开启，请先打开蓝牙", android.widget.Toast.LENGTH_SHORT).show()
                }

                BleShareTransport.AdvertiseStartResult.MissingPermission -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("缺少 BLE 权限，请先授权"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "缺少 BLE 权限，请先授权", android.widget.Toast.LENGTH_SHORT).show()
                }

                BleShareTransport.AdvertiseStartResult.AdvertiserUnavailable -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("当前设备不支持 BLE 广播"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "当前设备不支持 BLE 广播", android.widget.Toast.LENGTH_SHORT).show()
                }

                BleShareTransport.AdvertiseStartResult.GattServerOpenFailed -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("GATT 服务启动失败，请重试"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "GATT 服务启动失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                }

                is BleShareTransport.AdvertiseStartResult.Failed -> {
                    NfcUtil.setSenderShareState(NfcUtil.SenderShareState.Error("BLE 广播失败，错误码: ${result.errorCode}"))
                    BleShareTransport.setOnAckReceivedListener(null)
                    android.widget.Toast.makeText(context, "BLE 广播失败，错误码: ${result.errorCode}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
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