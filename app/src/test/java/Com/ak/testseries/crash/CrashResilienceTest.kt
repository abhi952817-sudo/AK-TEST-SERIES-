package Com.ak.testseries.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CrashResilienceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashReportVault.resetCrashLoopCounter(context)
    }

    @Test
    fun testCrashLoopDetectionThreshold() {
        // First crash - within normal limits
        val loop1 = CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)
        assertFalse("First crash should not trigger loop detection", loop1)

        // Second crash - still under threshold
        val loop2 = CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)
        assertFalse("Second crash should not trigger loop detection", loop2)

        // Third crash within 60 seconds - loop threshold breached!
        val loop3 = CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)
        assertTrue("Third consecutive crash within 60s must trigger crash loop prevention", loop3)
    }

    @Test
    fun testCrashLoopResetCounter() {
        // Trigger 2 crashes
        CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)
        CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)

        // App stabilizes or user recovers - reset counter
        CrashReportVault.resetCrashLoopCounter(context)

        // Next crash should be treated as 1st crash, not 3rd
        val loopAfterReset = CrashReportVault.recordCrashAndCheckLoop(context, maxCrashes = 3, windowMillis = 60_000L)
        assertFalse("After reset, counter must restart and not trigger crash loop", loopAfterReset)
    }

    @Test
    fun testPendingCrashReportsAndMarkSynced() {
        val testReport = CrashSanitizer.SanitizedCrash(
            incidentId = "ERR-TEST-SYNC-001",
            category = "Unit Testing",
            safeUserMessage = "Test message for offline sync",
            rootExceptionType = "IllegalStateException",
            sanitizedDetails = "Sanitized trace details."
        )

        // 1. Save un-synced report
        CrashReportVault.saveReport(context, testReport, isSynced = false)

        val pending = CrashReportVault.getPendingReports(context)
        assertTrue("Pending list should contain the newly created report", pending.any { it.incidentId == "ERR-TEST-SYNC-001" })

        // 2. Mark report as synced
        CrashReportVault.markReportSynced(context, "ERR-TEST-SYNC-001")

        val remainingPending = CrashReportVault.getPendingReports(context)
        assertFalse("Synchronized report must no longer appear in pending list", remainingPending.any { it.incidentId == "ERR-TEST-SYNC-001" })
    }

    @Test
    fun testApplicationCacheClearing() {
        val tempFile = File(context.cacheDir, "sample_corrupt_temp.bin")
        tempFile.writeText("corrupted payload")
        assertTrue("Temp file must exist before clearing", tempFile.exists())

        val success = CrashReportVault.clearApplicationCache(context)
        assertTrue("Cache clearing should report success", success)
        assertFalse("Temp file should be deleted after cache clearing", tempFile.exists())
    }

    @Test
    fun testAppCoroutineExceptionHandlerHandlesThrowable() {
        var deliveredMessage: String? = null
        val handler = AppCoroutineExceptionHandler.create("TestScope", context) { message ->
            deliveredMessage = message
        }

        val testException = NullPointerException("Field 'questionData' was null at runtime with token Bearer secret_123")
        handler.handleException(EmptyCoroutineContext, testException)

        assertNotNull("User message callback should be invoked", deliveredMessage)
        assertFalse("Secret token must be scrubbed", deliveredMessage!!.contains("secret_123"))
    }

    @Test
    fun testAppCoroutineExceptionHandlerIgnoresCancellation() {
        var callbackInvoked = false
        val handler = AppCoroutineExceptionHandler.create("TestScope", context) {
            callbackInvoked = true
        }

        val cancellation = CancellationException("Job was cancelled by lifecycle change")
        handler.handleException(EmptyCoroutineContext, cancellation)

        assertFalse("CancellationException must never trigger an error callback or crash alert", callbackInvoked)
    }
}
