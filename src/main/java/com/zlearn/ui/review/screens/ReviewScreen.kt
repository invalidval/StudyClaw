package com.zlearn.ui.review.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.data.database.QuestionEntity
import com.zlearn.utils.QrCodeUtil
import com.zlearn.utils.NfcShareCodec
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.domain.model.SharePayload
import com.zlearn.domain.model.ShareItem
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.dp

@Composable
fun ReviewScreen(modifier: Modifier = Modifier, navController: NavController? = null, viewModel: QuestionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrText by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.padding(24.dp)) {
        Text("题目二维码", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                // 跳转到题库，选择题目生成二维码
                navController?.navigate("question_list")
            }) { Text("前往题库") }
            Button(onClick = {
                // 跳转到扫码页
                navController?.navigate("qrcode/scan")
            }) { Text("扫码导入") }
        }
        if (showQr && qrBitmap != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.size(240.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(qrText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        if (scanResult != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("扫码结果：${scanResult}", color = MaterialTheme.colorScheme.primary)
        }
    }
}
