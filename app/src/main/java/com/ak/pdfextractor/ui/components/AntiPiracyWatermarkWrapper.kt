package com.ak.pdfextractor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
@Composable
fun AntiPiracyWatermarkWrapper(
    phoneNumber: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main Screen Content (Quiz UI ya PDF Viewer)
        content()

        // Dynamic Watermark Overlay (Non-blocking for touches)
        val textMeasurer = rememberTextMeasurer()
        val displayPhone = phoneNumber.trim().ifEmpty { "+91 98765 43210" }
        val watermarkText = "ID: $displayPhone"

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val textLayoutResult = textMeasurer.measure(
                text = watermarkText,
                style = TextStyle(
                    fontSize = 15.sp,
                    color = Color.Gray.copy(alpha = 0.15f) // Halka Semi-transparent
                )
            )

            val textWidth = textLayoutResult.size.width
            val textHeight = textLayoutResult.size.height

            val gapX = textWidth + 120f
            val gapY = textHeight + 140f

            // Diagonal Grid Drawing
            rotate(degrees = -32f) {
                var y = -size.height
                while (y < size.height * 2) {
                    var x = -size.width
                    while (x < size.width * 2) {
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(x, y)
                        )
                        x += gapX
                    }
                    y += gapY
                }
            }
        }
    }
}
