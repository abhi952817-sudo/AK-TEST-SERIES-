package Com.ak.testseries.data.backup.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import Com.ak.testseries.data.backup.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unit and integration tests for Google Drive Authentication & Storage Core (Phase 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class GoogleDriveServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testGoogleDriveRemoteBackupParsing() {
        val fileJson = JSONObject().apply {
            put("id", "drive_file_123")
            put("name", "ak_backup_user_abc_1690000000000.aktsbak")
            put("size", 204800L)
            put("createdTime", "2026-10-28T05:30:00.000Z")
            put("modifiedTime", "2026-10-28T05:30:00.000Z")
            put("appProperties", JSONObject().apply {
                put("ownerUid", "user_abc")
                put("schemaVersion", "19")
                put("appVersionName", "2.1")
                put("checksumSha256", "abc123sha256")
                put("completeness", "COMPLETE_USER_DATA")
            })
            put("description", JSONObject().apply {
                put("ownerUid", "user_abc")
                put("schemaVersion", 19)
                put("stats", JSONObject().apply {
                    put("privateTestsCount", 5)
                    put("questionsCount", 50)
                    put("attemptsCount", 12)
                    put("diagramsCount", 3)
                })
            }.toString())
        }

        val remoteBackup = GoogleDriveRemoteBackup.fromDriveFileJson(fileJson)
        assertNotNull(remoteBackup)
        assertEquals("drive_file_123", remoteBackup!!.fileId)
        assertEquals("ak_backup_user_abc_1690000000000.aktsbak", remoteBackup.name)
        assertEquals("user_abc", remoteBackup.ownerUid)
        assertEquals(19, remoteBackup.schemaVersion)
        assertEquals("2.1", remoteBackup.appVersionName)
        assertEquals("abc123sha256", remoteBackup.checksumSha256)
        assertEquals(BackupCompleteness.COMPLETE_USER_DATA, remoteBackup.completeness)
        assertEquals(204800L, remoteBackup.sizeBytes)
        assertNotNull(remoteBackup.stats)
        assertEquals(5, remoteBackup.stats!!.privateTestsCount)
        assertEquals(12, remoteBackup.stats!!.attemptsCount)
        assertEquals(3, remoteBackup.stats!!.diagramsCount)
        assertTrue(remoteBackup.statsSummary.contains("5 Tests"))
        assertTrue(remoteBackup.statsSummary.contains("12 Attempts"))
    }

    @Test
    fun testPreferencesSaveAndRetrieve() {
        GoogleDriveAuthManager.saveDriveFolderId(context, "folder_xyz_789")
        assertEquals("folder_xyz_789", GoogleDriveAuthManager.getDriveFolderId(context))

        GoogleDriveAuthManager.saveLastSyncTimestamp(context, 1700000000000L, "3 Tests • 5 Attempts")
        assertEquals(1700000000000L, GoogleDriveAuthManager.getLastSyncTimestamp(context))
        assertEquals("3 Tests • 5 Attempts", GoogleDriveAuthManager.getLastSyncStats(context))

        GoogleDriveAuthManager.setAutoBackupFrequency(context, "WEEKLY")
        assertEquals("WEEKLY", GoogleDriveAuthManager.getAutoBackupFrequency(context))

        GoogleDriveAuthManager.setWifiOnlyRequired(context, false)
        assertFalse(GoogleDriveAuthManager.isWifiOnlyRequired(context))

        GoogleDriveAuthManager.setChargingOnlyRequired(context, true)
        assertTrue(GoogleDriveAuthManager.isChargingOnlyRequired(context))
    }

    @Test
    fun testFakeDriveApiClientListFiltersByActiveUserUid() = runBlocking {
        val fakeClient = FakeDriveApiClient()
        val folderId = fakeClient.ensureBackupFolder("dummy_token").getOrThrow()

        // Upload a backup for user_1
        val manifest1 = createTestManifest(uid = "user_1")
        val file1 = createTestArchiveFile("user_1")
        fakeClient.uploadBackupFile("dummy_token", folderId, file1, manifest1).getOrThrow()

        // Upload a backup for user_2 (different user)
        val manifest2 = createTestManifest(uid = "user_2")
        val file2 = createTestArchiveFile("user_2")
        fakeClient.uploadBackupFile("dummy_token", folderId, file2, manifest2).getOrThrow()

        // Listing for user_1 must strictly return only user_1's backup
        val user1Backups = fakeClient.listBackups("dummy_token", folderId, "user_1").getOrThrow()
        assertEquals(1, user1Backups.size)
        assertEquals("user_1", user1Backups[0].ownerUid)

        // Listing for user_2 must strictly return only user_2's backup
        val user2Backups = fakeClient.listBackups("dummy_token", folderId, "user_2").getOrThrow()
        assertEquals(1, user2Backups.size)
        assertEquals("user_2", user2Backups[0].ownerUid)

        // Listing for user_3 (no backups) must return empty list
        val user3Backups = fakeClient.listBackups("dummy_token", folderId, "user_3").getOrThrow()
        assertTrue(user3Backups.isEmpty())
    }

    @Test
    fun testDownloadAndVerifyIntegrity() = runBlocking {
        val fakeClient = FakeDriveApiClient()
        val folderId = fakeClient.ensureBackupFolder("dummy_token").getOrThrow()

        val manifest = createTestManifest(uid = "user_verified")
        val sourceFile = createTestArchiveFile("user_verified")
        val uploaded = fakeClient.uploadBackupFile("dummy_token", folderId, sourceFile, manifest).getOrThrow()

        val downloadDest = File(context.cacheDir, "test_download.aktsbak")
        val downloaded = fakeClient.downloadBackupFile("dummy_token", uploaded.fileId, downloadDest).getOrThrow()

        assertTrue(downloaded.exists())
        assertEquals(sourceFile.length(), downloaded.length())
        assertEquals(sourceFile.readBytes().toList(), downloaded.readBytes().toList())
    }

    private fun createTestManifest(uid: String): BackupManifest {
        return BackupArchiveEngine.createAccountOwnedManifest(
            uid = uid,
            email = "$uid@example.com",
            displayName = "Test User $uid",
            appVersionCode = 1,
            appVersionName = "1.0",
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        ).getOrThrow()
    }

    private fun createTestArchiveFile(uid: String): File {
        val tempFile = File(context.cacheDir, "test_archive_$uid.aktsbak")
        val manifest = createTestManifest(uid)
        val entries = mapOf(
            "data/tests.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/questions.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/attempts.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/bookmarks.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/progress.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/syllabus.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/profile.json" to JSONObject().put("uid", uid).toString().toByteArray(Charsets.UTF_8)
        )
        val manifestWithStats = manifest.copy(
            stats = BackupStats(
                privateTestsCount = 0,
                questionsCount = 0,
                attemptsCount = 0,
                inProgressCount = 0,
                bookmarkedCount = 0,
                diagramsCount = 0
            )
        )
        tempFile.outputStream().use { fos ->
            BackupArchiveEngine.buildArchive(fos, manifestWithStats, entries).getOrThrow()
        }
        return tempFile
    }

    /**
     * In-memory fake Drive client simulating Google Drive v3 REST operations.
     */
    private class FakeDriveApiClient : DriveApiClient {
        private val folders = mutableSetOf<String>()
        private val files = mutableMapOf<String, Pair<GoogleDriveRemoteBackup, ByteArray>>()
        private var idCounter = 1

        override suspend fun ensureBackupFolder(accessToken: String): Result<String> {
            val folderId = "folder_ak_backup"
            folders.add(folderId)
            return Result.success(folderId)
        }

        override suspend fun uploadBackupFile(
            accessToken: String,
            folderId: String,
            file: File,
            manifest: BackupManifest
        ): Result<GoogleDriveRemoteBackup> {
            val fileId = "file_${idCounter++}"
            val remote = GoogleDriveRemoteBackup(
                fileId = fileId,
                name = file.name,
                sizeBytes = file.length(),
                createdTimeMillis = System.currentTimeMillis(),
                modifiedTimeMillis = System.currentTimeMillis(),
                ownerUid = manifest.owner.uid,
                schemaVersion = manifest.databaseSchemaVersion,
                appVersionName = manifest.appVersionName,
                checksumSha256 = manifest.checksums[BackupArchiveEngine.MANIFEST_ENTRY_NAME],
                stats = manifest.stats,
                completeness = manifest.completeness,
                driveFolderId = folderId
            )
            files[fileId] = Pair(remote, file.readBytes())
            return Result.success(remote)
        }

        override suspend fun listBackups(
            accessToken: String,
            folderId: String,
            targetUid: String
        ): Result<List<GoogleDriveRemoteBackup>> {
            val list = files.values
                .map { it.first }
                .filter { it.driveFolderId == folderId && it.ownerUid == targetUid }
                .sortedByDescending { it.createdTimeMillis }
            return Result.success(list)
        }

        override suspend fun downloadBackupFile(
            accessToken: String,
            fileId: String,
            destinationFile: File
        ): Result<File> {
            val entry = files[fileId] ?: return Result.failure(NoSuchElementException("File not found: $fileId"))
            destinationFile.parentFile?.mkdirs()
            destinationFile.writeBytes(entry.second)
            return Result.success(destinationFile)
        }

        override suspend fun deleteBackupFile(
            accessToken: String,
            fileId: String
        ): Result<Boolean> {
            files.remove(fileId)
            return Result.success(true)
        }
    }
}
