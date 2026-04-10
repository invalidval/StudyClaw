package com.zlearn.ui.nfc.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            BleShareTransport.stopScanning()
            BleShareTransport.stopAdvertising()
            viewModel.reset()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "NFC 分享", style = MaterialTheme.typography.headlineSmall)
        Text(if (isReceiverMode) "当前为接收模式（仅本页面开启）" else "当前为发送模式（不会开启接收）")

        if (!outgoingPayload.isNullOrBlank() && !isReceiverMode) {
            Text("待发送会话已准备")
            CircularProgressIndicator()
            Button(onClick = {
                NfcUtil.setOutgoingPayload(null)
                BleShareTransport.stopAdvertising()
            }) { Text("清空待发送") }
        }

        when (val state = uiState) {
            NfcUiState.Idle -> Text("等待 NFC 轻触唤醒...")

            is NfcUiState.Detected -> {
                Text("检测到分享请求")
                Text("Payload: ${state.payload}")
                Text("会话序号: ${state.eventId}")
                Button(onClick = { viewModel.importDetectedPayload() }) { Text("确认导入") }
            }

            is NfcUiState.Importing -> {
                SharePreviewStageCard(preview = state.preview, statusText = "已收到，正在导入...")
                Spacer(modifier = Modifier.height(8.dp))
                Text("正在导入...")
                CircularProgressIndicator()
            }

            is NfcUiState.Result -> {
                SharePreviewStageCard(preview = state.preview, statusText = "已收到并完成导入")
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.message)
                Button(onClick = { viewModel.reset() }) { Text("完成") }
            }

            is NfcUiState.Error -> {
                Text("错误: ${state.message}")
                if (state.message.contains("权限") && !hasBlePermissions) {
                    Button(onClick = { showPermissionDialog = true }) { Text("申请 BLE 权限") }
                }
                Button(onClick = { viewModel.reset() }) { Text("重试") }
            }
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
