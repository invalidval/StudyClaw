package com.zlearn.ui.nfc.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.nfc.viewmodel.NfcUiState
import com.zlearn.ui.nfc.viewmodel.NfcViewModel
import com.zlearn.ui.nfc.viewmodel.SharePreview
import com.zlearn.utils.BleShareTransport
import com.zlearn.utils.NfcUtil
import com.zlearn.utils.PermissionUtil

@Composable
fun NfcScreen(
    mode: String,
    modifier: Modifier = Modifier,
    viewModel: NfcViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val outgoingPayload by NfcUtil.outgoingPayload.collectAsState()
    val senderShareState by NfcUtil.senderShareState.collectAsState()
    val isReceiverMode = mode == "receiver"
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    val requiredPermissions = remember { PermissionUtil.requiredBlePermissions() }
    val hasBlePermissions = PermissionUtil.hasBlePermissions(context)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) viewModel.importDetectedPayload()
    }

    DisposableEffect(mode) {
        NfcUtil.setReceiverEnabled(isReceiverMode)
        onDispose {
            NfcUtil.setReceiverEnabled(false)
            NfcUtil.clearIncomingPayload()
            NfcUtil.clearOutgoingPayload()
            NfcUtil.clearSenderShareState()
            BleShareTransport.stopScanning()
            BleShareTransport.stopAdvertising()
            viewModel.reset()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderModeCard(isReceiverMode = isReceiverMode)

            if (!outgoingPayload.isNullOrBlank() && !isReceiverMode) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("待发送会话已准备", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "保持当前页面，轻触后会自动广播会话。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        )
                        OutlinedButton(onClick = {
                            NfcUtil.setOutgoingPayload(null)
                            NfcUtil.clearSenderShareState()
                            BleShareTransport.stopAdvertising()
                        }) { Text("清空待发送") }
                    }
                }
            }

            when (val state = uiState) {
                NfcUiState.Idle -> {
                    StateCard(
                        title = "等待 NFC 轻触唤醒",
                        message = "保持手机靠近，检测到请求后会显示导入预览。",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                is NfcUiState.Detected -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("检测到分享请求", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "会话序号：${state.eventId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                            )
                            Text(
                                "Payload：${state.payload}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                            )
                            FilledTonalButton(onClick = { viewModel.importDetectedPayload() }) {
                                Text("确认导入")
                            }
                        }
                    }
                }

                is NfcUiState.Importing -> {
                    SharePreviewStageCard(preview = state.preview, statusText = "已收到，正在导入")
                    StateCard(
                        title = "正在导入错题",
                        message = "请保持应用在前台，导入完成后会给出结果。",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        footer = {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    )
                }

                is NfcUiState.Result -> {
                    SharePreviewStageCard(preview = state.preview, statusText = "已收到并完成导入")
                    StateCard(
                        title = "导入完成",
                        message = state.message,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        footer = {
                            Button(onClick = { viewModel.reset() }) { Text("完成") }
                        }
                    )
                }

                is NfcUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("出现错误", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.86f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.message.contains("权限") && !hasBlePermissions) {
                                    OutlinedButton(onClick = { showPermissionDialog = true }) {
                                        Text("申请 BLE 权限")
                                    }
                                }
                                Button(onClick = { viewModel.reset() }) { Text("重试") }
                            }
                        }
                    }
                }
            }

            if (uiState is NfcUiState.Importing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.4.dp)
                }
            }
        }

        if (!isReceiverMode) {
            SenderCenterStatusCard(
                state = senderShareState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要 BLE 权限") },
            text = { Text("导入错题需要以下权限：${requiredPermissions.joinToString()}") },
            confirmButton = {
                Button(onClick = {
                    permissionLauncher.launch(requiredPermissions)
                    showPermissionDialog = false
                }) { Text("继续授权") }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HeaderModeCard(isReceiverMode: Boolean) {
    val tone = if (isReceiverMode) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val modeText = if (isReceiverMode) "接收模式" else "发送模式"
    val description = if (isReceiverMode) {
        "仅在当前页面开启接收，离开页面后自动关闭。"
    } else {
        "当前仅发送，不会主动开启接收监听。"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tone,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("NFC 分享", style = MaterialTheme.typography.headlineSmall)
            Text(modeText, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun StateCard(
    title: String,
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    footer: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            footer?.invoke()
        }
    }
}

@Composable
private fun SenderCenterStatusCard(
    state: NfcUtil.SenderShareState,
    modifier: Modifier = Modifier,
) {
    if (state is NfcUtil.SenderShareState.Idle) return

    Card(
        modifier = modifier.fillMaxWidth(0.86f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is NfcUtil.SenderShareState.WaitingAck -> {
                    Text("发送中", style = MaterialTheme.typography.titleMedium)
                    Text("等待对方确认接收", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is NfcUtil.SenderShareState.Completed -> {
                    Text("发送完成", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }

                is NfcUtil.SenderShareState.Error -> {
                    Text("发送失败", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }

                NfcUtil.SenderShareState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SharePreviewStageCard(
    preview: SharePreview?,
    statusText: String,
) {
    val actualPreview = preview ?: return
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialOffsetY = { -it }
        ) + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(statusText, style = MaterialTheme.typography.titleMedium)
                Text("来自：${actualPreview.senderDevice}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("学科：${actualPreview.subject.ifBlank { "未填写" }}")
                Text("难度：${actualPreview.difficulty}")
                Text("题目数：${actualPreview.itemCount}")
                if (actualPreview.summary.isNotBlank()) Text("摘要：${actualPreview.summary}")
                if (actualPreview.ocrText.isNotBlank()) Text("题干：${actualPreview.ocrText.take(80)}")
            }
        }
    }
}
