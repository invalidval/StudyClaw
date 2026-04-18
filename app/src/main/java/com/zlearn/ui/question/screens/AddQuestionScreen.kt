package com.zlearn.ui.question.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zlearn.data.database.QuestionEntity
import com.zlearn.ocr.OcrUtil
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQuestionScreen(
    navController: NavController,
    viewModel: QuestionViewModel = hiltViewModel()
) {
    var ocrText by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf(3) }
    var aiAnalysis by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var summaryEditedManually by remember { mutableStateOf(false) }
    var imagePath by remember { mutableStateOf("") }
    var ocrImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }
    var summaryLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    fun createCameraImageUri(): Uri {
        val imageFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun processImage(uri: Uri) {
        ocrImageUri = uri
        imagePath = uri.toString()
        ocrLoading = true
        scope.launch {
            val ocrResult = OcrUtil.recognizeTextFromUri(context, uri)
            ocrLoading = false
            if (ocrResult != null) {
                ocrText = ocrResult
                summaryLoading = true
                val generated = viewModel.generateSummary(ocrResult)
                val generatedSummary = if (generated.length > 20) generated.take(20) else generated
                // 若用户尚未手动编辑，自动填充摘要；否则保留用户输入
                if (!summaryEditedManually || summary.isBlank()) {
                    summary = generatedSummary
                }
                summaryLoading = false
                android.widget.Toast.makeText(context, "识别成功", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "识别失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) processImage(uri)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri
        if (success && uri != null) processImage(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加错题") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = ocrText,
                onValueChange = { ocrText = it },
                label = { Text("题干/OCR文本") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("学科") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = aiAnalysis,
                onValueChange = { aiAnalysis = it },
                label = { Text("AI解析(可选)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            OutlinedTextField(
                value = imagePath,
                onValueChange = { imagePath = it },
                label = { Text("图片路径(可选)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = summary,
                onValueChange = {
                    summary = it
                    summaryEditedManually = true
                },
                label = { Text("AI摘要(可编辑，默认自动生成)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                readOnly = false
            )

            if (summaryLoading) {
                Text("正在生成摘要...", color = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("难度:")
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { difficulty = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
                Text(difficulty.toString())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val uri = createCameraImageUri()
                        cameraImageUri = uri
                        takePictureLauncher.launch(uri)
                    },
                    enabled = !ocrLoading
                ) {
                    Text("拍照")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { pickImageLauncher.launch("image/*") }, enabled = !ocrLoading) {
                    Text("相册")
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (ocrLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else if (ocrImageUri != null) {
                    Text("已选择图片")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        if (ocrText.isNotBlank()) {
                            viewModel.addQuestion(
                                QuestionEntity(
                                    imagePath = imagePath,
                                    ocrText = ocrText,
                                    aiAnalysis = aiAnalysis,
                                    summary = summary,
                                    subject = subject,
                                    difficulty = difficulty,
                                    createTime = System.currentTimeMillis(),
                                    isArchived = false
                                )
                            )
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text("添加")
                }
            }
        }
    }
}

