package Com.ak.testseries.exam

import org.junit.Assert.*
import org.junit.Test

class OfflineMarkdownQuestionParserTest {

    @Test
    fun testParseStandardEnglishQuestionsWithAnswerKey() {
        val markdown = """
            1. What is the SI unit of electric potential?
            (A) Ampere
            (B) Volt
            (C) Ohm
            (D) Watt

            2. The capacitance of a capacitor depends on:
            (A) Geometry of the plates
            (B) Type of metal
            (C) Battery connected
            (D) None of these

            Answer Key:
            1. (B)
            2. (A)
        """.trimIndent()

        val parsed = OfflineMarkdownQuestionParser.parseStructuredQuestions(markdown)
        assertEquals(2, parsed.size)

        assertEquals("What is the SI unit of electric potential?", parsed[0].questionText)
        assertEquals("Ampere", parsed[0].optionA)
        assertEquals("Volt", parsed[0].optionB)
        assertEquals("Ohm", parsed[0].optionC)
        assertEquals("Watt", parsed[0].optionD)
        assertEquals("B", parsed[0].correctAnswer)

        assertEquals("The capacitance of a capacitor depends on:", parsed[1].questionText)
        assertEquals("Geometry of the plates", parsed[1].optionA)
        assertEquals("A", parsed[1].correctAnswer)
    }

    @Test
    fun testParseHindiAndBilingualQuestionsWithHindiDigits() {
        val markdown = """
            प्रश्न १. निम्नलिखित में से कौन सा एक अदिश राशि है?
            (अ) विद्युत धारा
            (ब) विद्युत क्षेत्र
            (स) संवेग
            (द) बल
            उत्तर: (अ)

            प्रश्न २. प्रतिरोध का मात्रक क्या है?
            (1) वोल्ट
            (2) ओम
            (3) फैराड
            (4) हेनरी
            Ans: (B)
        """.trimIndent()

        val parsed = OfflineMarkdownQuestionParser.parseStructuredQuestions(markdown)
        assertEquals(2, parsed.size)

        assertEquals(1, parsed[0].questionNumber)
        assertTrue(parsed[0].questionText.contains("अदिश राशि"))
        assertEquals("विद्युत धारा", parsed[0].optionA)
        assertEquals("विद्युत क्षेत्र", parsed[0].optionB)
        assertEquals("A", parsed[0].correctAnswer)

        assertEquals(2, parsed[1].questionNumber)
        assertTrue(parsed[1].questionText.contains("प्रतिरोध का मात्रक"))
        assertEquals("ओम", parsed[1].optionB)
        assertEquals("B", parsed[1].correctAnswer)
    }

    @Test
    fun testParseToQuestionEntities() {
        val text = """
            Q1. Which of the following is an amphoteric oxide?
            (A) CO2
            (B) Al2O3
            (C) Na2O
            (D) SO2
            Ans: B

            Q2. Which element has the highest electronegativity?
            (A) Oxygen
            (B) Nitrogen
            (C) Fluorine
            (D) Chlorine
            Ans: C
        """.trimIndent()

        val entities = OfflineMarkdownQuestionParser.parseTextToQuestionEntities(
            text = text,
            testId = "test_123",
            subject = "Chemistry",
            examName = "NEET UG"
        )

        assertEquals(2, entities.size)
        assertEquals("test_123_q1", entities[0].id)
        assertEquals("Al2O3", entities[0].optionB)
        assertEquals("B", entities[0].correctAnswer)
        assertEquals("Chemistry", entities[0].subject)

        assertEquals("test_123_q2", entities[1].id)
        assertEquals("Fluorine", entities[1].optionC)
        assertEquals("C", entities[1].correctAnswer)
    }

    @Test
    fun testParseMarkdownToUniversalQuestionModels() {
        val text = """
            1. The Indian Constitution was adopted on:
            (A) 26 January 1950
            (B) 26 November 1949
            (C) 15 August 1947
            (D) 30 January 1948
            Ans: B
        """.trimIndent()

        val examConfig = UniversalExamRegistry.findExamByNameOrShortName("UPSC CSE")!!
        val models = OfflineMarkdownQuestionParser.parseMarkdownToQuestions(
            markdown = text,
            examConfig = examConfig,
            subjectId = "Indian Polity"
        )

        assertEquals(1, models.size)
        assertEquals("The Indian Constitution was adopted on:", models[0].questionText)
        assertEquals("B", models[0].correctAnswer)
        assertEquals("Indian Polity", models[0].subjectId)
        assertEquals(ValidationStatus.APPROVED, models[0].validationStatus)
        assertEquals("Extracted PDF Test Paper", models[0].sourceName)
    }

    @Test
    fun missingAnswerKeyDoesNotInventOptionAOrApproveQuestion() {
        val text = """
            1. Which element has atomic number 8?
            (A) Oxygen
            (B) Nitrogen
            (C) Carbon
            (D) Hydrogen
        """.trimIndent()
        val examConfig = UniversalExamRegistry.findExamByNameOrShortName("UPSC CSE")!!
        val models = OfflineMarkdownQuestionParser.parseMarkdownToQuestions(
            markdown = text,
            examConfig = examConfig,
            subjectId = "General Science"
        )
        assertEquals(1, models.size)
        assertEquals("", models[0].correctAnswer)
        assertNull(models[0].sourceAnswer)
        assertEquals(ValidationStatus.NEEDS_REVIEW, models[0].validationStatus)
        assertTrue(models[0].validationReasons.any { it.contains("Independent source answer unavailable") })
    }
}
