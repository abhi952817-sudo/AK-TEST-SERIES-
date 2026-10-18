package Com.ak.testseries.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CustomSaveIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CustomSaveIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 2f)
            curveTo(4.8444444f, 2f, 4f, 2.9666667f, 4f, 4f)
            lineTo(4f, 22.039062f)
            lineTo(12f, 19.066406f)
            lineTo(20f, 22.039062f)
            lineTo(20f, 20.599609f)
            lineTo(20f, 4f)
            curveTo(20f, 3.4777778f, 19.808671f, 2.9453899f, 19.431641f, 2.5683594f)
            curveTo(19.05461f, 2.1913289f, 18.522222f, 2f, 18f, 2f)
            lineTo(6f, 2f)
            close()
            moveTo(6f, 4f)
            lineTo(18f, 4f)
            lineTo(18f, 19.162109f)
            lineTo(12f, 16.933594f)
            lineTo(6f, 19.162109f)
            lineTo(6f, 4f)
            close()
        }
    }.build()

val CustomSaveIconFilled: ImageVector
    get() = ImageVector.Builder(
        name = "CustomSaveIconFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 2f)
            curveTo(4.8444444f, 2f, 4f, 2.9666667f, 4f, 4f)
            lineTo(4f, 22.039062f)
            lineTo(12f, 19.066406f)
            lineTo(20f, 22.039062f)
            lineTo(20f, 20.599609f)
            lineTo(20f, 4f)
            curveTo(20f, 3.4777778f, 19.808671f, 2.9453899f, 19.431641f, 2.5683594f)
            curveTo(19.05461f, 2.1913289f, 18.522222f, 2f, 18f, 2f)
            lineTo(6f, 2f)
            close()
        }
    }.build()
