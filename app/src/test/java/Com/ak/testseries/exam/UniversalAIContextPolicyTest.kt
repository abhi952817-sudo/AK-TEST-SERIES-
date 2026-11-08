package Com.ak.testseries.exam

import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAIContextPolicyTest {

    @Test
    fun universalPolicyIsExamAgnosticAndPreventsFabrication() {
        val prompt = UniversalAIContext.buildUniversalSystemInstruction()
        assertTrue(prompt.contains("never assume every examination follows NEET"))
        assertTrue(prompt.contains("Never fill a requested question count with fabricated questions"))
        assertTrue(prompt.contains("Do not reveal private chain-of-thought"))
        assertTrue(prompt.contains("Follow the exact JSON schema"))
    }

    @Test
    fun taskModeIsExplicitInSharedPolicy() {
        val prompt = UniversalAIContext.buildUniversalSystemInstruction(
            UniversalAiTaskMode.SOURCE_EXTRACTION
        )
        assertTrue(prompt.contains("ACTIVE TASK MODE: SOURCE_EXTRACTION"))
    }
}
