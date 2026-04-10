package com.zlearn.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.MainActivity
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import okhttp3.*
import android.os.Process
import com.zlearn.MyApplication
import com.zlearn.utils.AppMode

@Composable
fun SplashScreen() {
    val questionViewModel: QuestionViewModel = hiltViewModel()
    val context = LocalContext.current
    val hostActivity = context as? Activity
    val isSplashHost = hostActivity is SplashActivity

    if (!isSplashHost) {
        // MainActivity directly renders main content; splash host handles the cold-start gate.
        var allowed by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            val application = context.applicationContext as MyApplication
            if (application.appMode == AppMode.PRE) {
                Log.d("SplashScreen", "Starting IP check for PRE mode")
                val client = OkHttpClient()
                allowed = try {
                    val request = Request.Builder()
                        .url("http://10.129.94.211:8080/api/check_ip")
                        .build()
                    Log.d("SplashScreen", "Sending request to check_ip")
                    val response = withContext(Dispatchers.IO) {
                        client.newCall(request).execute()
                    }
                    Log.d("SplashScreen", "Response received: ${response.code}")
                    val isAllowed = response.isSuccessful
                    Log.d("SplashScreen", "isAllowed: $isAllowed")
                    isAllowed
                } catch (e: Exception) {
                    Log.e("SplashScreen", "Exception during IP check", e)
                    // Toast.makeText(context, "IP check exception", Toast.LENGTH_SHORT).show()
                    false
                }
                Log.d("SplashScreen", "allowed: $allowed")
                if (!allowed) {
                    Log.d("SplashScreen", "Killing process due to IP check failure")
                    Process.killProcess(Process.myPid())
                }
            }
        }
        if (allowed) {
            MainScreen()
        }
        return
    }

    var readyToEnterMain by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val application = context.applicationContext as MyApplication
        if (application.appMode == AppMode.PRE) {
            // Toast.makeText(context, "Checking IP for PRE mode in Splash", Toast.LENGTH_SHORT).show()
            val client = OkHttpClient()
            val allowed = try {
                val request = Request.Builder()
                    .url("http://10.129.94.211:8080/api/check_ip")
                    .build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val isAllowed = response.isSuccessful
                isAllowed
            } catch (_: Exception) {
                // Toast.makeText(context, "IP check exception in Splash", Toast.LENGTH_SHORT).show()
                false
            }
            if (!allowed) {
                Process.killProcess(Process.myPid())
            }
        }
        questionViewModel.loadQuestions()
        // Keep splash visible during initial load; fallback covers empty database first launch.
        delay(700)
        readyToEnterMain = true
    }

    LaunchedEffect(readyToEnterMain) {
        if (readyToEnterMain) {
            context.startActivity(Intent(context, MainActivity::class.java))
            hostActivity.finish()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "StudyClaw",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AI 助力，轻松学习",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = "Created by ZCY & ChatGPT",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
