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
import androidx.compose.foundation.layout.size
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
import com.zlearn.utils.ShareHceService

@Composable
fun NfcScreen(
    mode: String,
    modifier: Modifier = Modifier,
    viewModel: NfcViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState
    val outgoingPayload by NfcUtil.outgoingPayload.collectAsState()
    val senderShareState by NfcUtil.senderShareState.collectAsState()
    val isReceiverMode = mode == "receiver"
    val isSenderBroadcast = mode == "sender-broadcast"
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
        NfcUtil.setSenderEnabled(!isReceiverMode) // 发送端拦截 NFC Intent
        onDispose {
            NfcUtil.setReceiverEnabled(false)
            NfcUtil.setSenderEnabled(false)
            NfcUtil.clearIncomingPayload()
            NfcUtil.clearOutgoingPayload()
            NfcUtil.clearSenderShareState()
            BleShareTransport.stopScanning()
            BleShareTransport.stopAdvertising()
            ShareHceService.clearSessionData()
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
            HeaderModeCard(isReceiverMode = isReceiverMode, isSenderBroadcast = isSenderBroadcast)

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
                            if (isSenderBroadcast) "对方在广播模式中搜索即可发现你的设备。"
                            else "保持当前页面，轻触后会自动广播会话。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        )
                        OutlinedButton(onClick = {
                            NfcUtil.setOutgoingPayload(null)
                            NfcUtil.clearSenderShareState()
                            BleShareTransport.stopAdvertising()
                            ShareHceService.clearSessionData()
                        }) { Text("清空待发送") }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            //  接收方 UI：NFC 等待 + 广播搜索
            // ═══════════════════════════════════════════════
            if (isReceiverMode) {
                when (state) {
                    is NfcUiState.Detected -> {
                        val isValidSession = runCatching { java.util.UUID.fromString(state.payload) }.isSuccess
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isValidSession) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isValidSession) "检测到分享请求" else "未识别到分享信号", style = MaterialTheme.typography.titleMedium)
                                if (isValidSession) {
                                    Text("会话序号：${state.eventId}", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
                                    FilledTonalButton(onClick = { viewModel.importDetectedPayload() }) { Text("确认导入") }
                                } else {
                                    Text("读到了非预期的 NFC 数据（可能是交通卡/门禁卡）。", style = MaterialTheme.typography.bodyMedium)
                                    Text("请换个位置重新轻触发送方手机。", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f))
                                }
                            }
                        }
                    }
                    is NfcUiState.Importing -> {
                        SharePreviewStageCard(preview = state.preview, statusText = "已收到，正在导入")
                        StateCard(title = "正在导入错题", message = "请保持应用在前台，导入完成后会给出结果。",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            footer = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) })
                    }
                    is NfcUiState.Result -> {
                        SharePreviewStageCard(preview = state.preview, statusText = "已收到并完成导入")
                        StateCard(title = "导入完成", message = state.message,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            footer = { Button(onClick = { viewModel.reset() }) { Text("完成") } })
                    }
                    is NfcUiState.Error -> {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("出现错误", style = MaterialTheme.typography.titleMedium)
                                Text(state.message, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.86f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.message.contains("权限") && !hasBlePermissions) {
                                        OutlinedButton(onClick = { showPermissionDialog = true }) { Text("申请 BLE 权限") }
                                    }
                                    Button(onClick = { viewModel.reset() }) { Text("重试") }
                                }
                            }
                        }
                    }
                    else -> {
                        // 空闲状态：显示 NFC 和广播两个入口
                        StateCard(title = "NFC 碰触唤醒", message = "保持手机靠近发送方，检测到会话后自动弹出导入预览。",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        // 广播搜索卡片
                        BroadcastSearchCard(viewModel = viewModel, uiState = state)
                    }
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
private fun HeaderModeCard(isReceiverMode: Boolean, isSenderBroadcast: Boolean = false) {
    val tone = if (isReceiverMode) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val modeText = when {
        isReceiverMode -> "接收模式"
        isSenderBroadcast -> "发送模式（广播）"
        else -> "发送模式（NFC）"
    }
    val description = when {
        isReceiverMode -> "仅在当前页面开启接收，离开页面后自动关闭。"
        isSenderBroadcast -> "对方通过 BLE 扫描设备列表发现你，无需 NFC 碰触。"
        else -> "等待对方靠近。对方将在 NFC 碰触后获取会话并导入。"
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
            Text("互传", style = MaterialTheme.typography.headlineSmall)
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
private fun BroadcastSearchCard(viewModel: NfcViewModel, uiState: NfcUiState) {
    val isScanning = uiState is NfcUiState.BroadcastDiscovering
    val devices = (uiState as? NfcUiState.BroadcastDiscovering)?.devices ?: emptyList()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("广播搜索", style = MaterialTheme.typography.titleMedium)
                    Text("无需 NFC，通过 BLE 扫描附近正在分享的设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f))
                }
                if (!isScanning) {
                    FilledTonalButton(onClick = { viewModel.startBroadcastScan() }) { Text("开始扫描") }
                } else {
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("停止") }
                }
            }
            if (isScanning) {
                HorizontalDivider()
                if (devices.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在扫描附近设备...", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                } else {
                    Text("发现 ${devices.size} 台设备", style = MaterialTheme.typography.labelMedium)
                    devices.forEach { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = { viewModel.selectBroadcastDevice(device) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.deviceName, style = MaterialTheme.typography.titleSmall)
                                    Text(if (device.sessionId != null) "会话已就绪" else "等待会话",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (device.sessionId != null) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        if (device.sessionId != null) "可连接" else "无数据",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
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
