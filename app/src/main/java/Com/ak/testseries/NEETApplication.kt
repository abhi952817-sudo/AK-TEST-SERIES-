package Com.ak.testseries

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import Com.ak.testseries.crash.CrashReportVault
import Com.ak.testseries.crash.CrashSanitizer

class NEETApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase and configure Crashlytics stability monitoring
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Throwable) {
            Log.w("NEETApplication", "Default FirebaseApp initialization skipped: ${e.message}")
        }

        // Programmatic fallback if resource-based initialization was not triggered
        if (FirebaseApp.getApps(this).isEmpty()) {
            try {
                val projectId = "ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612"
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:836952428988:android:aktestseries")
                    .setApiKey("AIzaSyMockKeyForInitializationAKTestSeries")
                    .setProjectId(projectId)
                    .setStorageBucket("$projectId.appspot.com")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("NEETApplication", "FirebaseApp initialized with fallback options")
            } catch (e: Throwable) {
                Log.w("NEETApplication", "Fallback programmatic Firebase initialization skipped: ${e.message}")
            }
        }

        try {
            getSafeCrashlytics()?.apply {
                setCrashlyticsCollectionEnabled(true)
                setCustomKey("app_version", BuildConfig.VERSION_NAME)
                setCustomKey("build_type", BuildConfig.BUILD_TYPE)
                log("NEETApplication: Crashlytics initialized successfully")
            }
        } catch (e: Throwable) {
            Log.w("NEETApplication", "Crashlytics setup skipped: ${e.message}")
        }

        // 2. Synchronize any previously saved offline crash reports when connectivity permits
        try {
            Com.ak.testseries.crash.CrashSyncWorker.scheduleSync(this)
        } catch (_: Throwable) {}

        // 3. Uncaught exception handler: sanitize, detect crash loop, and record before safe recovery screen
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sanitized = try {
                CrashSanitizer.sanitize(throwable)
            } catch (_: Throwable) {
                CrashSanitizer.SanitizedCrash(
                    incidentId = "ERR-FATAL-${System.currentTimeMillis() % 1000000}",
                    category = "Application Stability",
                    safeUserMessage = "An unexpected error occurred. The application was safely restarted.",
                    rootExceptionType = throwable.javaClass.simpleName,
                    sanitizedDetails = "Error sanitized to prevent sensitive information leakage."
                )
            }

            // Check if application is stuck in a repetitive startup crash loop (>3 crashes in 60s)
            val isCrashLoop = CrashReportVault.recordCrashAndCheckLoop(this)

            // Securely persist sanitized report into app-private storage
            CrashReportVault.saveReport(this, sanitized)

            // Schedule background telemetry sync
            try {
                Com.ak.testseries.crash.CrashSyncWorker.scheduleSync(this)
            } catch (_: Throwable) {}

            try {
                getSafeCrashlytics()?.apply {
                    setCustomKey("incident_id", sanitized.incidentId)
                    setCustomKey("error_category", sanitized.category)
                    setCustomKey("is_crash_loop", isCrashLoop)
                    recordException(throwable)
                }
            } catch (e: Throwable) {
                Log.w("NEETApplication", "Crashlytics exception recording skipped: ${e.message}")
            }

            // Launch CrashActivity with ONLY safe, non-sensitive incident metadata
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_INCIDENT_ID, sanitized.incidentId)
                putExtra(CrashActivity.EXTRA_CATEGORY, sanitized.category)
                putExtra(CrashActivity.EXTRA_SAFE_MESSAGE, sanitized.safeUserMessage)
                putExtra(CrashActivity.EXTRA_TIMESTAMP, sanitized.timestamp)
                putExtra(CrashActivity.EXTRA_IS_CRASH_LOOP, isCrashLoop)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    companion object {
        /**
         * Safely obtains FirebaseCrashlytics instance if FirebaseApp is active and initialized.
         * Returns null rather than throwing IllegalStateException if Firebase is unavailable.
         */
        fun getSafeCrashlytics(): FirebaseCrashlytics? {
            return try {
                val app = FirebaseApp.getInstance()
                if (FirebaseApp.getApps(app.applicationContext).isNotEmpty()) {
                    FirebaseCrashlytics.getInstance()
                } else null
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Gathers and reports non-fatal exceptions to Firebase Crashlytics across devices,
         * ensuring any custom messages are scrubbed of sensitive PII or credentials.
         */
        fun recordNonFatal(throwable: Throwable, message: String? = null) {
            try {
                val crashlytics = getSafeCrashlytics() ?: return
                val sanitizedMsg = CrashSanitizer.sanitizeText(message)
                if (sanitizedMsg.isNotBlank()) {
                    crashlytics.log(sanitizedMsg)
                }
                crashlytics.recordException(throwable)
            } catch (_: Throwable) {
                val safeMsg = CrashSanitizer.sanitizeText(message ?: throwable.message)
                Log.w("NEETApplication", "Non-fatal report skipped: $safeMsg")
            }
        }

        /**
         * Logs diagnostic breadcrumb messages into the Crashlytics session trail after redaction.
         */
        fun log(message: String) {
            try {
                val sanitized = CrashSanitizer.sanitizeText(message)
                getSafeCrashlytics()?.log(sanitized)
            } catch (_: Throwable) {
                Log.d("NEETApplication", CrashSanitizer.sanitizeText(message))
            }
        }

        /**
         * Sets custom key-value metadata to assist in bug triage and device stability tracking,
         * redacting any potential secrets or sensitive values.
         */
        fun setCustomKey(key: String, value: String) {
            try {
                val sanitizedVal = CrashSanitizer.sanitizeText(value)
                getSafeCrashlytics()?.setCustomKey(key, sanitizedVal)
            } catch (_: Throwable) {
                // Ignore silently
            }
        }
    }
}
