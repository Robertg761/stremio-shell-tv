package com.stremioshell.host.tv.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Render [content] as a black-on-white QR bitmap of [sizePx] square. Fills an
 * IntArray and uploads it with a single setPixels call rather than hundreds of
 * thousands of per-pixel writes; call off the main thread.
 */
fun encodeQrBitmap(content: String, sizePx: Int): Bitmap {
  val hints = mapOf(
    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    EncodeHintType.MARGIN to 1,
  )
  val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
  val pixels = IntArray(sizePx * sizePx)
  for (y in 0 until sizePx) {
    val row = y * sizePx
    for (x in 0 until sizePx) {
      pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
    }
  }
  return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
    setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
  }
}
