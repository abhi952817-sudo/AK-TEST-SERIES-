package com.ak.pdfextractor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility wrapper delegating to Com.ak.testseries.ui.AntiPiracyWatermarkWrapper
 */
@Composable
fun AntiPiracyWatermarkWrapper(
    phoneNumber: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Com.ak.testseries.ui.AntiPiracyWatermarkWrapper(
        phoneNumber = phoneNumber,
        modifier = modifier,
        content = content
    )
}
