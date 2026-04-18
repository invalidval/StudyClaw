package com.zlearn.ui.qrcode.screens

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.utils.QrCodeUtil
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.InputStream
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(viewModel: QuestionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var scanResult by remember { mutableStateOf<String?>(null) }
    val previewPayload by viewModel.remoteQuestion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = inputStream?.use { stream -> BitmapFactory.decodeStream(stream) }
            if (bitmap != null) {
                val text = QrCodeUtil.decodeFromBitmap(bitmap)
                if (!text.isNullOrBlank()) {
                    handleScannedText(text, viewModel)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("扫码导入", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 扫描框容器，增加圆角和阴影效果
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // 保持正方形
                    .clip(RoundedCornerShape(24.dp)),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp
            ) {
                if (hasCameraPermission) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                CompoundBarcodeView(ctx).apply {
                                    val formats = listOf(BarcodeFormat.QR_CODE)
                                    barcodeView.decoderFactory = DefaultDecoderFactory(
                                        formats,
                                        mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"),
                                        null,
                                        2
                                    )
                                    decodeContinuous { result ->
                                        if (scanResult != result.text) {
                                            scanResult = result.text
                                            handleScannedText(result.text, viewModel)
                                        }
                                    }
                                    resume()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 覆盖美化的扫描引导层
                        ScannerOverlay()
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("请求相机权限中...", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 操作按钮组
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从相册选择")
                }
            }
        }
    }

    // 识别结果弹窗
    if (previewPayload != null || isLoading || error != null) {
        Dialog(onDismissRequest = {
            viewModel.clearRemoteQuestion()
            viewModel.clearError()
            scanResult = null
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()
                ) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (error != null) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("加载失败", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { viewModel.clearError(); scanResult = null }) {
                                Text("重试")
                            }
                        }
                    } else if (previewPayload != null) {
                        val item = previewPayload!!
                        Text("识别到题目", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        // 题目内容支持滚动
                        Box(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text("摘要：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(item.summary)
                                Spacer(modifier = Modifier.height(12.dp))

                                Text("题干：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(item.ocrText)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("学科：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text(item.subject)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("难度：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text(
                                            text = when (item.difficulty) {
                                                1, 2-> "简单"
                                                3 -> "中等"
                                                4 -> "有点难"
                                                5 -> "困难"
                                                else -> "白送？地狱级？"
                                            }
                                        )
                                    }
                                }

                                if (item.aiAnalysis.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("AI 解析：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            item.aiAnalysis,
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("最后更改：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                val date = java.util.Date(item.updatedAt)
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                Text(format.format(date), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                viewModel.clearRemoteQuestion()
                                scanResult = null
                            }) {
                                Text("取消")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                viewModel.addQuestion(
                                    com.zlearn.data.database.QuestionEntity(
                                        ocrText = item.ocrText,
                                        summary = item.summary,
                                        subject = item.subject,
                                        difficulty = item.difficulty,
                                        aiAnalysis = item.aiAnalysis,
                                        isArchived = item.isArchived,
                                        archiveType = item.archiveType ?: "",
                                        imagePath = "",
                                        createTime = System.currentTimeMillis()
                                    )
                                )
                                viewModel.clearRemoteQuestion()
                                scanResult = null
                            }) {
                                Text("确认加入题库")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleScannedText(text: String, viewModel: QuestionViewModel) {
    if (text.startsWith("studyclaw://question/")) {
        val idStr = text.removePrefix("studyclaw://question/")
        val cloudId = idStr.toIntOrNull()
        if (cloudId != null) {
            viewModel.fetchQuestionFromCloud(cloudId)
        }
    }
}

@Composable
fun ScannerOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 扫描框装饰
        Box(
            modifier = Modifier
                .size(260.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )

        // 扫描动态线（简单演示）
        Text(
            "正在扫描中...",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}
