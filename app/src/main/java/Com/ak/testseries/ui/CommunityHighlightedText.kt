package Com.ak.testseries.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue

data class CommunityTextHighlight(
    val start: Int,
    val end: Int
)

fun selectedCommunityTextHighlight(value: TextFieldValue): CommunityTextHighlight? {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    return if (end > start) CommunityTextHighlight(start, end) else null
}

fun parseCommunityTextHighlights(raw: Any?, content: String): List<CommunityTextHighlight> {
    val entries = raw as? List<*> ?: return emptyList()
    val parsed = entries.mapNotNull { item ->
        val fields = item as? Map<*, *> ?: return@mapNotNull null
        val start = (fields["start"] as? Number)?.toInt() ?: return@mapNotNull null
        val end = (fields["end"] as? Number)?.toInt() ?: return@mapNotNull null
        if (start < 0 || end <= start || end > content.length) null
        else CommunityTextHighlight(start, end)
    }.sortedBy { it.start }.take(20)

    val safe = mutableListOf<CommunityTextHighlight>()
    var previousEnd = -1
    parsed.forEach { span ->
        if (span.start >= previousEnd) {
            safe += span
            previousEnd = span.end
        }
    }
    return safe
}

@Composable
fun CommunityHighlightedText(
    text: String,
    highlights: List<CommunityTextHighlight>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE
) {
    val highlightBackground = MaterialTheme.colorScheme.tertiaryContainer
    val annotated = buildAnnotatedString {
        var cursor = 0
        highlights.sortedBy { it.start }.take(20).forEach { span ->
            val start = span.start.coerceIn(cursor, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            if (end > start) {
                withStyle(SpanStyle(background = highlightBackground, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(start, end))
                }
            }
            cursor = end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    Text(text = annotated, modifier = modifier, fontSize = fontSize, color = color, maxLines = maxLines)
}
