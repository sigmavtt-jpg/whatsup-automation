package com.whatsup.automation.presentation.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * مكوّن عرض رمز QR حقيقي قابل للمسح بكاميرا واتساب الرسمية مباشرة.
 * يحول نص الـ payload الوارد من خوادم واتساب عبر Baileys إلى مصفوفة نقطية حقيقية.
 */
@Composable
fun RealQrCodeView(
    payload: String?,
    size: Dp = 220.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(payload) {
        if (!payload.isNullOrBlank()) {
            try {
                val hints = mapOf(EncodeHintType.MARGIN to 1)
                val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512, hints)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                bmp
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "رمز QR الحقيقي للاقتران",
                modifier = Modifier.size(size - 16.dp)
            )
        } else {
            CircularProgressIndicator(
                color = androidx.compose.ui.graphics.Color(0xFF25D366),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
