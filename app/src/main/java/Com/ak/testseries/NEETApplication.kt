package Com.ak.testseries

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class NEETApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase and configure Crashlytics stability monitoring
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.setCrashlyticsCollectionEnabled(true)
                crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
                crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
                crashlytics.log("NEETApplication: Crashlytics initialized successfully")
            } catch (e: Throwable) {
                Log.w("NEETApplication", "Crashlytics setup skipped: ${e.message}")
            }
        } catch (e: Throwable) {
            Log.e("NEETApplication", "Failed to initialize Firebase", e)
        }

        // 2. Uncaught exception handler: log to Crashlytics before showing fallback recovery screen
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (e: Throwable) {
                Log.e("NEETApplication", "Error recording fatal crash to Crashlytics", e)
            }

            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("error", Log.getStackTraceString(throwable))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    companion object {
        /**
         * Gathers and reports non-fatal exceptions to Firebase Crashlytics across devices.
         */
        fun recordNonFatal(throwable: Throwable, message: String? = null) {
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                if (!message.isNullOrBlank()) {
                    crashlytics.log(message)
                }
                crashlytics.recordException(throwable)
            } catch (e: Throwable) {
                Log.e("NEETApplication", "Failed to report non-fatal to Crashlytics: ${message ?: throwable.message}", throwable)
            }
        }

        /**
         * Logs diagnostic breadcrumb messages into the Crashlytics session trail.
         */
        fun log(message: String) {
            try {
                FirebaseCrashlytics.getInstance().log(message)
            } catch (e: Throwable) {
                Log.d("NEETApplication", message)
            }
        }

        /**
         * Sets custom key-value metadata to assist in bug triage and device stability tracking.
         */
        fun setCustomKey(key: String, value: String) {
            try {
                FirebaseCrashlytics.getInstance().setCustomKey(key, value)
            } catch (e: Throwable) {
                Log.d("NEETApplication", "Failed to set custom key $key: $value")
            }
        }
    }
}
