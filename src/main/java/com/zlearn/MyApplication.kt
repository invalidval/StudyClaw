package com.zlearn

import android.app.Application
import com.zlearn.utils.AppMode
import com.zlearn.utils.BleShareTransport
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import okhttp3.*
import android.os.Process
import java.io.IOException

@HiltAndroidApp
class MyApplication : Application() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    // 手动设置应用模式：DEV（开发）、PRE（预发布）、REL（发布）
    val appMode = AppMode.PRE  // 更改此值以切换模式

    override fun onCreate() {
        super.onCreate()
        BleShareTransport.init(this)

        if (appMode == AppMode.PRE) {
            startHeartbeat()
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("${BuildConfig.BASE_URL}api/heartbeat")
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                } catch (_: IOException) {
                    // Heartbeat failed, crash the app
                    Process.killProcess(Process.myPid())
                }
                delay(3000L)
            }
        }
    }

    private suspend fun checkIpAllowed(): Boolean {
        return try {
            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}api/check_ip")
                .build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                // 简单解析JSON，检查"allowed": true
                responseBody?.contains("\"allowed\":true") == true
            } else {
                false
            }
        } catch (_: IOException) {
            false
        }
    }
}
