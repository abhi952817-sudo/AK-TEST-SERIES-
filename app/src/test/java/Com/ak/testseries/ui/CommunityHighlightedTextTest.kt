package Com.ak.testseries.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityHighlightedTextTest {
    @Test
    fun selectedRangeSupportsForwardAndReverseSelection() {
        val forward = selectedCommunityTextHighlight(
            TextFieldValue("A highlighted doubt", selection = TextRange(2, 8))
        )
        val reverse = selectedCommunityTextHighlight(
            TextFieldValue("A highlighted doubt", selection = TextRange(8, 2))
        )

        assertEquals(CommunityTextHighlight(2, 8), forward)
        assertEquals(forward, reverse)
    }

    @Test
    fun emptySelectionDoesNotCreateHighlight() {
        val span = selectedCommunityTextHighlight(
            TextFieldValue("No selected text", selection = TextRange(4, 4))
        )
        assertNull(span)
    }

    @Test
    fun parserRejectsOutOfRangeAndOverlappingSpans() {
        val parsed = parseCommunityTextHighlights(
            listOf(
                mapOf("start" to 0, "end" to 4),
                mapOf("start" to 3, "end" to 8),
                mapOf("start" to 10, "end" to 30),
                mapOf("start" to -1, "end" to 2)
            ),
            "0123456789"
        )

        assertEquals(listOf(CommunityTextHighlight(0, 4)), parsed)
    }

    @Test
    fun parserCapsHighlightCountAtTwenty() {
        val spans = (0 until 25).map { index ->
            mapOf("start" to (index * 2), "end" to (index * 2 + 1))
        }
        val parsed = parseCommunityTextHighlights(spans, "x".repeat(60))

        assertEquals(20, parsed.size)
    }
}
