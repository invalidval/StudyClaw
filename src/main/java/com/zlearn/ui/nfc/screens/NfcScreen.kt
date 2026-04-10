package com.zlearn.ui.nfc.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zlearn.ui.nfc.viewmodel.NfcUiState
import com.zlearn.ui.nfc.viewmodel.NfcViewModel
import com.zlearn.utils.PermissionUtil
import com.zlearn.utils.NfcUtil

@Composable
fun NfcScreen(
    mode: String = "receiver",
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
        if (result.values.all { it }) {
            viewModel.importDetectedPayload()
        }
    }

    LaunchedEffect(hasBlePermissions) {
        if (hasBlePermissions && showPermissionDialog) {
            showPermissionDialog = false
        }
    }

    DisposableEffect(Unit) {
        NfcUtil.setReceiverEnabled(isReceiverMode)
        onDispose {
            NfcUtil.setReceiverEnabled(false)
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
            Button(onClick = { NfcUtil.setOutgoingPayload(null) }) {
                Text("清空待发送")
            }
        }

        when (val state = uiState) {
            NfcUiState.Idle -> {
                Text("等待 NFC 轻触唤醒...")
            }

            is NfcUiState.Detected -> {
                Text("检测到分享请求")
                Text("Payload: ${state.payload}")
                Text("会话序号: ${state.eventId}")
                Button(onClick = { viewModel.importDetectedPayload() }) {
                    Text("确认导入")
                }
            }

            is NfcUiState.Importing -> {
                Text("正在导入...")
                CircularProgressIndicator()
            }

            is NfcUiState.Result -> {
                Text(state.message)
                Button(onClick = { viewModel.reset() }) {
                    Text("完成")
                }
            }

            is NfcUiState.Error -> {
                Text("错误: ${state.message}")
                if (state.message.contains("权限") && !hasBlePermissions) {
                    Button(onClick = {
                        showPermissionDialog = true
                    }) {
                        Text("申请 BLE 权限")
                    }
                }
                Button(onClick = { viewModel.reset() }) {
                    Text("重试")
                }
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
                }) {
                    Text("继续授权")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

