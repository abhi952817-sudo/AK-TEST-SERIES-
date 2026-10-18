package Com.ak.testseries.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating semi-transparent text overlay displaying the user's phone number
 * diagonally across the quiz screen for anti-piracy / watermark protection.
 *
 * Touch events pass through this overlay without blocking clicks or interactions.
 */
@Composable
fun QuizWatermarkOverlay(
    phoneNumber: String,
    modifier: Modifier = Modifier,
    alpha: Float = 0.20f,
    angleDegrees: Float = -30f,
    fontSize: TextUnit = 14.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalSpacing: Dp = 200.dp,
    verticalSpacing: Dp = 140.dp,
    enableFloatAnimation: Boolean = true
) {
    val cleanPhone = phoneNumber.trim().ifEmpty { "+91 98765 43210" }
    val watermarkText = "AK TEST SERIES  •  $cleanPhone"

    // Infinite gentle subtle floating animation to prevent screen capture masking
    val infiniteTransition = rememberInfiniteTransition(label = "WatermarkFloat")
    val animatedOffsetY by if (enableFloatAnimation) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "WatermarkOffsetY"
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("quiz_watermark_overlay")
            .drawBehind {
                val stepX = horizontalSpacing.toPx()
                val stepY = verticalSpacing.toPx()
                val width = size.width
                val height = size.height

                if (width <= 0 || height <= 0) return@drawBehind

                val nativeColor = android.graphics.Color.argb(
                    (alpha * 255).toInt().coerceIn(0, 255),
                    (textColor.red * 255).toInt().coerceIn(0, 255),
                    (textColor.green * 255).toInt().coerceIn(0, 255),
                    (textColor.blue * 255).toInt().coerceIn(0, 255)
                )

                val textPaint = Paint().apply {
                    color = nativeColor
                    textSize = fontSize.toPx()
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.rotate(angleDegrees, width / 2f, height / 2f)

                    val startX = -width * 1.5f
                    val endX = width * 2.5f
                    val startY = -height * 1.5f
                    val endY = height * 2.5f

                    var row = 0
                    var y = startY + animatedOffsetY
                    while (y < endY) {
                        val rowOffset = if (row % 2 == 0) 0f else stepX / 2f
                        var x = startX + rowOffset
                        while (x < endX) {
                            canvas.nativeCanvas.drawText(watermarkText, x, y, textPaint)
                            x += stepX
                        }
                        y += stepY
                        row++
                    }
                    canvas.nativeCanvas.restore()
                }
            }
    )
}
