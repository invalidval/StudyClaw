package com.zlearn.ui.qrcode.screens

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.utils.QrCodeUtil
import com.zlearn.utils.NfcShareCodec
import com.zlearn.domain.model.SharePayload
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
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(viewModel: QuestionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var scanResult by remember { mutableStateOf<String?>(null) }
    var previewPayload by remember { mutableStateOf<SharePayload?>(null) }
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
                scanResult = text
                if (!text.isNullOrBlank()) {
                    val payload = NfcShareCodec.decode(text).getOrNull()
                    previewPayload = payload
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
                                            val payload = NfcShareCodec.decode(result.text).getOrNull()
                                            if (payload != null && payload.items.isNotEmpty()) {
                                                scanResult = result.text
                                                previewPayload = payload
                                            }
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
    if (previewPayload != null) {
        val item = previewPayload!!.items.firstOrNull()
        if (item != null) {
            Dialog(onDismissRequest = {
                previewPayload = null
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
                        Text("识别到题目", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        // 题目内容支持滚动
                        Box(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text("题干：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(item.ocrText)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("学科：${item.subject}")
                                Text("难度：${item.difficulty}")
                                if (item.aiAnalysis.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("AI 解析：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text(item.aiAnalysis)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                previewPayload = null
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
                                        archiveType = item.archiveType,
                                        imagePath = "",
                                        createTime = System.currentTimeMillis()
                                    )
                                )
                                previewPayload = null
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
        ) {
            // 四个角落的高亮
            val cornerColor = MaterialTheme.colorScheme.primary
            val cornerLength = 20.dp
            val thickness = 4.dp

            // 这里可以添加更复杂的角标绘制逻辑，简单起见用Border即可满足大部分美感
        }

        // 扫描动态线（简单演示）
        Text(
            "正在扫描中...",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}
