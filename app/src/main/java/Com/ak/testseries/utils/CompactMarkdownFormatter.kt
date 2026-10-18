package Com.ak.testseries.utils

/**
 * Formats raw offline-extracted PDF text into highly compact Markdown.
 * Drops noisy boilerplate, page headers, footers, duplicate whitespace,
 * and formats questions and options into compact Markdown structures to
 * significantly minimize API token payload.
 */
object CompactMarkdownFormatter {

    fun formatToCompactMarkdown(rawText: String): String {
        if (rawText.isBlank()) return ""
        val lines = rawText.lines()
        val cleanedLines = mutableListOf<String>()

        val skipPattern = Regex("""(?i)^\s*(page\s+\d+(\s+of\s+\d+)?|test\s+series|all\s+rights\s+reserved|confidential|neet\s+ug\s+\d{4}|watermark|mock\s+test\s+\d+)\s*$""")
        val questionStartPattern = Regex("""(?i)^\s*(?:Q\.?\s*(\d+)[\.:\)]|Question\s*(\d+)[\.:\)]|प्रश्न\s*(\d+)[\.:\)])\s*(.*)""")
        val optionPattern = Regex("""(?i)^\s*([\(]?[A-Da-d1-4][\)\.:])\s*(.*)""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (skipPattern.matches(trimmed)) continue

            // Normalize question lines to markdown bold format
            val qMatch = questionStartPattern.find(trimmed)
            if (qMatch != null) {
                val num = qMatch.groupValues[1].ifEmpty { qMatch.groupValues[2].ifEmpty { qMatch.groupValues[3] } }
                val content = qMatch.groupValues[4]
                cleanedLines.add("\n### Q$num. $content")
                continue
            }

            // Normalize options to compact markdown list
            val optMatch = optionPattern.find(trimmed)
            if (optMatch != null) {
                val optLabel = optMatch.groupValues[1].replace("(", "").replace(")", "").replace(".", "").replace(":", "").uppercase()
                val optContent = optMatch.groupValues[2]
                cleanedLines.add("- **($optLabel)** $optContent")
                continue
            }

            // Normal text line
            cleanedLines.add(trimmed)
        }

        // Collapse multiple blank lines and excessive spaces to save tokens
        return cleanedLines.joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }
}
