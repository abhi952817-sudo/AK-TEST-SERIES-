package Com.ak.testseries.crash

import android.content.Context
import android.util.Log
import Com.ak.testseries.NEETApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Global and ViewModel-level CoroutineExceptionHandler that intercepts uncaught exceptions
 * occurring on background Coroutine Dispatchers, sanitizes stack traces, records non-fatals,
 * triggers offline telemetry sync, and emits non-intrusive UI snackbar notifications.
 */
object AppCoroutineExceptionHandler {

    private const val TAG = "CoroutineCrashHandler"

    // Global event bus for non-fatal background coroutine errors to notify UI via Snackbar
    private val _userAlertEvents = MutableSharedFlow<UserCrashAlert>(extraBufferCapacity = 10)
    val userAlertEvents: SharedFlow<UserCrashAlert> = _userAlertEvents.asSharedFlow()

    data class UserCrashAlert(
        val incidentId: String,
        val message: String,
        val category: String,
        val canRetry: Boolean = true
    )

    /**
     * Factory function creating a CoroutineExceptionHandler bound to a specific component tag.
     *
     * @param tag Identifier for the calling ViewModel, Repository, or Worker (e.g. "ExamViewModel")
     * @param context Application context for disk storage and WorkManager sync
     * @param onUserMessage Optional custom callback to deliver sanitized error text directly to local state
     */
    fun create(
        tag: String,
        appContext: Context? = null,
        onUserMessage: ((String) -> Unit)? = null
    ): CoroutineExceptionHandler {
        return object : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
            override fun handleException(context: CoroutineContext, exception: Throwable) {
                // Cooperative cancellation must not be intercepted as a failure
                if (exception is CancellationException) {
                    return
                }

                val sanitized = try {
                    CrashSanitizer.sanitize(exception)
                } catch (_: Throwable) {
                    CrashSanitizer.SanitizedCrash(
                        incidentId = "ERR-BG-${System.currentTimeMillis() % 1000000}",
                        category = "Background Stability",
                        safeUserMessage = "A background operation could not be completed. Please try again.",
                        rootExceptionType = exception.javaClass.simpleName,
                        sanitizedDetails = "Sanitized trace."
                    )
                }

                Log.w(TAG, "[$tag] Handled background coroutine exception [${sanitized.incidentId}]: ${sanitized.rootExceptionType}")

                // 1. Record non-fatal event to Crashlytics session trail
                try {
                    NEETApplication.recordNonFatal(exception, "[$tag] Incident: ${sanitized.incidentId}")
                } catch (_: Throwable) {}

                // 2. Persist to CrashReportVault and trigger WorkManager sync if context is present
                appContext?.let { ctx ->
                    try {
                        CrashReportVault.saveReport(ctx.applicationContext, sanitized)
                        CrashSyncWorker.scheduleSync(ctx.applicationContext)
                    } catch (_: Throwable) {}
                }

                // 3. User feedback: invoke custom callback or broadcast to global snackbar flow
                val safeMessage = sanitized.safeUserMessage.ifBlank {
                    "Unable to complete operation. Please try again."
                }

                if (onUserMessage != null) {
                    onUserMessage(safeMessage)
                } else {
                    _userAlertEvents.tryEmit(
                        UserCrashAlert(
                            incidentId = sanitized.incidentId,
                            message = safeMessage,
                            category = sanitized.category
                        )
                    )
                }
            }
        }
    }
}
