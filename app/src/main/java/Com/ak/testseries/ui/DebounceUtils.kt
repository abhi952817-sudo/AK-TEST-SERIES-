package Com.ak.testseries.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Clickable modifier that guards against rapid multi-clicks (debounce).
 * Prevents accidental duplicate submissions, uploads, or state toggles.
 */
fun Modifier.clickableDebounced(
    debounceIntervalMs: Long = 600L,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var lastClickTime = remember { 0L }

    this.clickable(
        interactionSource = interactionSource,
        indication = ripple(),
        onClick = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceIntervalMs) {
                lastClickTime = currentTime
                onClick()
            }
        }
    )
}
