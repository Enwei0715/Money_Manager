package com.enwei.moneymanager.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 在手機上辨識截圖／照片裡的文字（ML Kit，中英文，離線、不上傳）。 */
object ScreenOcr {
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    suspend fun read(context: Context, uri: Uri): List<String> = read(InputImage.fromFilePath(context, uri))

    suspend fun read(bitmap: Bitmap): List<String> = read(InputImage.fromBitmap(bitmap, 0))

    /** 依畫面位置由上到下、由左到右排好的文字行。 */
    private suspend fun read(image: InputImage): List<String> = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
                    .map { it.text.trim() }.filter { it.isNotEmpty() }
                cont.resume(lines)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
