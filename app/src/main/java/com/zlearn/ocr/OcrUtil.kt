package com.zlearn.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

object OcrUtil {
    /**
     * 识别图片中的文字，返回识别结果字符串。
     * @param context Context
     * @param imageUri 图片Uri
     * @return 识别到的文本，失败返回null
     */
    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri): String? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val image = InputImage.fromFilePath(context, imageUri)
                    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val result = Tasks.await(recognizer.process(image))
                result.text
            }
        } catch (e: Exception) {
            android.util.Log.e("OcrUtil", "recognizeTextFromUri failed", e)
            null
        }
    }

    /**
     * 识别Bitmap中的文字，返回识别结果字符串。
     * @param context Context
     * @param bitmap Bitmap
     * @return 识别到的文本，失败返回null
     */
    suspend fun recognizeTextFromBitmap(context: Context, bitmap: Bitmap, rotation: Int = 0): String? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val image = InputImage.fromBitmap(bitmap, rotation)
                    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val result = Tasks.await(recognizer.process(image))
                result.text
            }
        } catch (e: Exception) {
            android.util.Log.e("OcrUtil", "recognizeTextFromBitmap failed", e)
            null
        }
    }
}
