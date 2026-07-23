package com.nathan.bingoauto

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit's on-device Latin text recogniser and turns a bitmap into the
 * list of integer number-tokens it contains, each with the pixel centre of its
 * bounding box. Runs fully offline (bundled model).
 */
class OcrProcessor {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** OCR one frame. The bitmap must be in the absolute screen pixel space. */
    suspend fun recognize(bitmap: Bitmap): List<NumberToken> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val tokens = ArrayList<NumberToken>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val digits = DIGITS.find(element.text) ?: continue
                    val value = digits.value.toIntOrNull() ?: continue
                    val box = element.boundingBox ?: continue
                    tokens.add(
                        NumberToken(
                            value = value,
                            cx = box.exactCenterX(),
                            cy = box.exactCenterY(),
                        )
                    )
                }
            }
        }
        return tokens
    }

    fun close() = recognizer.close()

    companion object {
        private val DIGITS = Regex("\\d{1,2}")
    }
}
