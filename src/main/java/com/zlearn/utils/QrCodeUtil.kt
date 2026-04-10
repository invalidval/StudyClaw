package com.zlearn.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.WriterException
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import com.google.zxing.DecodeHintType
import java.util.EnumMap

// 二维码工具类骨架
object QrCodeUtil {
    fun encodeToBitmap(text: String, size: Int = 480): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            // 明确指定使用 UTF-8 进行字符串编码
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            // 某些老旧解码器对 ISO-8859-1 有偏向，强制 UTF-8 有助于中文传输
            val bitMatrix: BitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: WriterException) {
            null
        }
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        // 显式指定解码时使用的字符集为 UTF-8
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
        hints[DecodeHintType.TRY_HARDER] = true
        return try {
            val result: Result = MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}
