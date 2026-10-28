package Com.ak.testseries.data.backup.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Google Drive OAuth authorization for the authenticated user's private Google Account.
 *
 * Strict Rules:
 * - Uses individual user OAuth credentials (scopes: drive.file, drive.appdata).
 * - Never uploads multiple users' data to a single shared master developer account.
 * - Never bundles client secrets or sensitive credentials.
 */
object GoogleDriveAuthManager {

    const val PREFS_NAME = "ak_google_drive_backup_prefs"
    const val KEY_DRIVE_CONNECTED = "drive_connected"
    const val KEY_DRIVE_ACCOUNT_EMAIL = "drive_account_email"
    const val KEY_DRIVE_ACCOUNT_DISPLAY_NAME = "drive_account_display_name"
    const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
    const val KEY_LAST_DRIVE_BACKUP_TIMESTAMP = "last_drive_backup_timestamp"
    const val KEY_LAST_DRIVE_BACKUP_STATS = "last_drive_backup_stats"
    const val KEY_LINKED_FIREBASE_UID = "linked_firebase_uid"
    const val KEY_AUTO_BACKUP_FREQUENCY = "auto_backup_frequency" // OFF, DAILY, WEEKLY
    const val KEY_AUTO_BACKUP_WIFI_ONLY = "auto_backup_wifi_only"
    const val KEY_AUTO_BACKUP_CHARGING_ONLY = "auto_backup_charging_only"
    const val KEY_AUTO_RESTORE_ON_NEW_DEVICE = "auto_restore_on_new_device"

    val SCOPE_DRIVE_FILE = Scope("https://www.googleapis.com/auth/drive.file")
    val SCOPE_DRIVE_APPDATA = Scope("https://www.googleapis.com/auth/drive.appdata")

    private val OAUTH_SCOPE_STRING = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(SCOPE_DRIVE_FILE, SCOPE_DRIVE_APPDATA)
            .build()
    }

    fun getSignInClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    fun getSignInIntent(context: Context): Intent {
        return getSignInClient(context).signInIntent
    }

    fun handleSignInResult(data: Intent?, context: Context, currentFirebaseUid: String? = null): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val email = account.email ?: ""
                val displayName = account.displayName ?: ""
                val prefs = getPrefs(context)
                val editor = prefs.edit()
                    .putBoolean(KEY_DRIVE_CONNECTED, true)
                    .putString(KEY_DRIVE_ACCOUNT_EMAIL, email)
                    .putString(KEY_DRIVE_ACCOUNT_DISPLAY_NAME, displayName)
                if (!currentFirebaseUid.isNullOrBlank()) {
                    editor.putString(KEY_LINKED_FIREBASE_UID, currentFirebaseUid.trim())
                }
                editor.apply()
                Result.success(account)
            } else {
                Result.failure(IllegalStateException("Google Sign-In returned null account."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun bindDriveAccount(context: Context, firebaseUid: String) {
        val safeUid = firebaseUid.trim()
        if (safeUid.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LINKED_FIREBASE_UID, safeUid).apply()
        }
    }

    fun getLinkedFirebaseUid(context: Context): String? {
        return getPrefs(context).getString(KEY_LINKED_FIREBASE_UID, null)
    }

    /**
     * Verifies that the connected Google Drive account is authorized and bound strictly
     * to the active Firebase UID. Returns failure if accounts mismatch or Drive is not connected.
     */
    fun verifyAccountBinding(context: Context, activeFirebaseUid: String): Result<Unit> {
        val safeUid = activeFirebaseUid.trim()
        if (safeUid.isBlank()) {
            return Result.failure(IllegalStateException("Active authenticated Firebase UID is required."))
        }
        val linkedUid = getLinkedFirebaseUid(context)
        if (!linkedUid.isNullOrBlank() && linkedUid != safeUid) {
            val connectedEmail = getConnectedEmail(context) ?: "connected account"
            return Result.failure(
                SecurityException(
                    "Google Drive account ($connectedEmail) is linked to a different user. Please disconnect and reconnect Google Drive for the current user."
                )
            )
        }
        if (linkedUid.isNullOrBlank()) {
            bindDriveAccount(context, safeUid)
        }
        return Result.success(Unit)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
            account
        } else {
            null
        }
    }

    fun isDriveConnected(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isFlagged = prefs.getBoolean(KEY_DRIVE_CONNECTED, false)
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return isFlagged && account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)
    }

    fun getConnectedEmail(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account?.email != null) return account.email
        return getPrefs(context).getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
    }

    fun getConnectedDisplayName(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account?.displayName != null) return account.displayName
        return getPrefs(context).getString(KEY_DRIVE_ACCOUNT_DISPLAY_NAME, null)
    }

    suspend fun getAccessToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val account = getSignedInAccount(context)
            ?: return@withContext Result.failure(IllegalStateException("No Google Account signed in with Drive permissions."))
        val androidAccount: Account = account.account
            ?: return@withContext Result.failure(IllegalStateException("GoogleSignInAccount does not contain an Android Account."))

        try {
            val token = GoogleAuthUtil.getToken(context, androidAccount, OAUTH_SCOPE_STRING)
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("Failed to obtain OAuth2 access token for Google Drive."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invalidateToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
    }

    suspend fun disconnect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getSignInClient(context)
            client.signOut()
            getPrefs(context).edit()
                .putBoolean(KEY_DRIVE_CONNECTED, false)
                .remove(KEY_DRIVE_ACCOUNT_EMAIL)
                .remove(KEY_DRIVE_ACCOUNT_DISPLAY_NAME)
                .remove(KEY_DRIVE_FOLDER_ID)
                .remove(KEY_LINKED_FIREBASE_UID)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDriveFolderId(context: Context): String? {
        return getPrefs(context).getString(KEY_DRIVE_FOLDER_ID, null)
    }

    fun saveDriveFolderId(context: Context, folderId: String) {
        getPrefs(context).edit().putString(KEY_DRIVE_FOLDER_ID, folderId).apply()
    }

    fun getLastSyncTimestamp(context: Context): Long? {
        val time = getPrefs(context).getLong(KEY_LAST_DRIVE_BACKUP_TIMESTAMP, -1L)
        return if (time > 0) time else null
    }

    fun saveLastSyncTimestamp(context: Context, timestamp: Long, statsSummary: String? = null) {
        val editor = getPrefs(context).edit().putLong(KEY_LAST_DRIVE_BACKUP_TIMESTAMP, timestamp)
        if (statsSummary != null) {
            editor.putString(KEY_LAST_DRIVE_BACKUP_STATS, statsSummary)
        }
        editor.apply()
    }

    fun getLastSyncStats(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_DRIVE_BACKUP_STATS, null)
    }

    fun getAutoBackupFrequency(context: Context): String {
        return getPrefs(context).getString(KEY_AUTO_BACKUP_FREQUENCY, "DAILY") ?: "DAILY"
    }

    fun setAutoBackupFrequency(context: Context, frequency: String) {
        getPrefs(context).edit().putString(KEY_AUTO_BACKUP_FREQUENCY, frequency).apply()
    }

    fun isWifiOnlyRequired(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_BACKUP_WIFI_ONLY, true)
    }

    fun setWifiOnlyRequired(context: Context, wifiOnly: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BACKUP_WIFI_ONLY, wifiOnly).apply()
    }

    fun isChargingOnlyRequired(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_BACKUP_CHARGING_ONLY, false)
    }

    fun setChargingOnlyRequired(context: Context, chargingOnly: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BACKUP_CHARGING_ONLY, chargingOnly).apply()
    }

    fun isAutoRestoreOnNewDeviceEnabled(context: Context, uid: String? = null): Boolean {
        val safeUid = uid?.trim()
        val prefs = getPrefs(context)
        return if (!safeUid.isNullOrBlank() && prefs.contains("${KEY_AUTO_RESTORE_ON_NEW_DEVICE}_$safeUid")) {
            prefs.getBoolean("${KEY_AUTO_RESTORE_ON_NEW_DEVICE}_$safeUid", false)
        } else {
            prefs.getBoolean(KEY_AUTO_RESTORE_ON_NEW_DEVICE, false)
        }
    }

    fun setAutoRestoreOnNewDeviceEnabled(context: Context, enabled: Boolean, uid: String? = null) {
        val safeUid = uid?.trim()
        val editor = getPrefs(context).edit()
        if (!safeUid.isNullOrBlank()) {
            editor.putBoolean("${KEY_AUTO_RESTORE_ON_NEW_DEVICE}_$safeUid", enabled)
        } else {
            editor.putBoolean(KEY_AUTO_RESTORE_ON_NEW_DEVICE, enabled)
        }
        editor.apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
