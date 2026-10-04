package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Matrice QR (true = module sombre) générée localement avec ZXing core ; vide si le texte est trop long. */
fun qrMatrix(text: String): List<BooleanArray> = runCatching {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    List(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
}.getOrDefault(emptyList())

/** QR code sombre sur fond blanc (zone calme incluse), à scanner avec un téléphone. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Box(modifier.size(size).background(Color.White).padding(size / 12)) {
        Canvas(Modifier.size(size - size / 6)) {
            val n = matrix.size
            if (n == 0) return@Canvas
            val cell = this.size.width / n
            for (y in 0 until n) for (x in 0 until n) if (matrix[y][x])
                drawRect(Color(0xFF0A0A0C), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
