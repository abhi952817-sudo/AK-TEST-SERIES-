package Com.ak.testseries

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level smoke test. Requires the production app's google-services.json
 * to be supplied through the secure CI/local build process.
 *
 * FirebaseFirestore.getInstance(app) intentionally selects the (default)
 * Firestore database; this test validates client SDK initialization, not
 * authorization or live reads/writes against production data.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseRuntimeInitializationTest {

    @Test
    fun firebaseSdkServicesInitializeFromAndroidClientConfig() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Com.ak.testseries", context.packageName)

        val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
        assertNotNull(app.options.projectId)
        assertFalse(app.options.projectId.isNullOrBlank())
        assertNotNull(app.options.applicationId)
        assertFalse(app.options.applicationId.isNullOrBlank())

        // These instances use the default app/database/bucket configuration.
        assertNotNull(FirebaseAuth.getInstance(app))
        assertNotNull(FirebaseFirestore.getInstance(app))
        assertNotNull(FirebaseStorage.getInstance(app))
    }
}
