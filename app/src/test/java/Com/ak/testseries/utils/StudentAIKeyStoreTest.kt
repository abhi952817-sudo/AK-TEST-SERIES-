package Com.ak.testseries.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StudentAIKeyStoreTest {

    @Test
    fun preferredProviderAndModelAreScopedToTheAuthenticatedUid() {
        val context = RuntimeEnvironment.getApplication() as Application
        StudentAIKeyStore.savePreferredModel(
            context,
            "student-account-a",
            StudentAIKeyStore.Provider.OPENAI,
            "gpt-model-test"
        )

        assertEquals(
            StudentAIKeyStore.Provider.OPENAI,
            StudentAIKeyStore.preferredProvider(context, "student-account-a")
        )
        assertEquals(
            "gpt-model-test",
            StudentAIKeyStore.preferredModel(context, "student-account-a", StudentAIKeyStore.Provider.OPENAI)
        )
        assertNull(StudentAIKeyStore.preferredProvider(context, "student-account-b"))
        assertNull(StudentAIKeyStore.preferredModel(context, "student-account-b", StudentAIKeyStore.Provider.OPENAI))

        StudentAIKeyStore.clearAll(context, "student-account-a")
        assertNull(StudentAIKeyStore.preferredProvider(context, "student-account-a"))
        assertNull(StudentAIKeyStore.preferredModel(context, "student-account-a", StudentAIKeyStore.Provider.OPENAI))
    }

    @Test
    fun clearingOneProviderDoesNotClearAnotherProvidersModel() {
        val context = RuntimeEnvironment.getApplication() as Application
        StudentAIKeyStore.savePreferredModel(
            context,
            "student-account",
            StudentAIKeyStore.Provider.GEMINI,
            "gemini-model-test"
        )
        StudentAIKeyStore.savePreferredModel(
            context,
            "student-account",
            StudentAIKeyStore.Provider.GROQ,
            "groq-model-test"
        )

        StudentAIKeyStore.clear(context, "student-account", StudentAIKeyStore.Provider.GEMINI)

        assertEquals(
            "groq-model-test",
            StudentAIKeyStore.preferredModel(context, "student-account", StudentAIKeyStore.Provider.GROQ)
        )
    }
}
