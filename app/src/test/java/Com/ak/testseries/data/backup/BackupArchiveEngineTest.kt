package Com.ak.testseries.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class BackupArchiveEngineTest {

    private val validOwner = BackupOwner(
        uid = "test_user_uid_123",
        email = "student@example.com",
        displayName = "Sample Student"
    )

    @Test
    fun testManifest_serializationAndDeserialization() {
        val originalManifest = BackupManifest(
            formatVersion = 1,
            createdAt = 1793148571000L,
            appVersionCode = 2,
            appVersionName = "1.1",
            databaseSchemaVersion = 19,
            scope = BackupScope.ACCOUNT_OWNED,
            completeness = BackupCompleteness.STRUCTURAL_ONLY,
            owner = validOwner,
            stats = BackupStats(
                privateTestsCount = 5,
                questionsCount = 150,
                attemptsCount = 10,
                diagramsCount = 8
            ),
            compatibility = BackupCompatibility(minSupportedAppVersion = 1, minSupportedSchemaVersion = 15),
            checksums = mapOf("data/sample.json" to "abcd1234abcd1234")
        )

        val jsonString = originalManifest.toJsonString()
        assertNotNull(jsonString)
        assertTrue(jsonString.contains(""""formatVersion": 1"""))
        assertTrue(jsonString.contains(""""databaseSchemaVersion": 19"""))
        assertTrue(jsonString.contains(""""uid": "test_user_uid_123""""))

        val deserialized = BackupManifest.fromJsonString(jsonString)
        assertEquals(originalManifest.formatVersion, deserialized.formatVersion)
        assertEquals(originalManifest.databaseSchemaVersion, deserialized.databaseSchemaVersion)
        assertEquals(originalManifest.owner.uid, deserialized.owner.uid)
        assertEquals(originalManifest.owner.email, deserialized.owner.email)
        assertEquals(originalManifest.stats.privateTestsCount, deserialized.stats.privateTestsCount)
        assertEquals(originalManifest.stats.questionsCount, deserialized.stats.questionsCount)
        assertEquals(originalManifest.checksums["data/sample.json"], deserialized.checksums["data/sample.json"])
    }

    @Test
    fun testSha256_correctness() {
        // "Hello, AK Test Series!" SHA-256
        val input = "Hello, AK Test Series!".toByteArray(Charsets.UTF_8)
        val hash = BackupArchiveEngine.computeSha256(input)
        assertNotNull(hash)
        assertEquals(64, hash.length) // Hex length of SHA-256 is 64

        // Deterministic check
        val hash2 = BackupArchiveEngine.computeSha256(input)
        assertEquals(hash, hash2)

        // Known vector: empty byte array SHA-256
        val emptyHash = BackupArchiveEngine.computeSha256(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)
    }

    @Test
    fun testZipSlip_pathTraversalRejection() {
        val dangerousPaths = listOf(
            "../etc/passwd",
            "data/../../shadow",
            "/absolute/path/file.json",
            "\\windows\\path\\file.json",
            "C:/drive/file.json",
            "data/./current.json",
            "data//empty_segment.json",
            "null\u0000byte.json",
            "",
            "   "
        )

        for (path in dangerousPaths) {
            val result = BackupArchiveEngine.sanitizeEntryPath(path)
            assertTrue("Dangerous path must be rejected: '$path'", result.isFailure)
        }

        // Safe relative paths must succeed
        val safePaths = listOf(
            "data/tests.json",
            "data/questions.json",
            "assets/diagrams/test_1/q1.png",
            "profile/avatar.jpg"
        )
        for (path in safePaths) {
            val result = BackupArchiveEngine.sanitizeEntryPath(path)
            assertTrue("Safe path must be accepted: '$path'", result.isSuccess)
        }
    }

    @Test
    fun testBuildArchive_and_ValidateArchive_success() {
        val manifestDraftResult = BackupArchiveEngine.createAccountOwnedManifest(
            uid = "user_456",
            email = "student456@aktest.com",
            displayName = "Student 456",
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        )
        assertTrue(manifestDraftResult.isSuccess)
        val manifestDraft = manifestDraftResult.getOrThrow()

        val sampleEntries = mapOf(
            "data/tests.json" to """[{"id":"test_1","title":"Physics Mock"}]""".toByteArray(Charsets.UTF_8),
            "data/questions.json" to """[{"id":"q_1","testId":"test_1"}]""".toByteArray(Charsets.UTF_8),
            "assets/diagrams/test_1/d1.png" to ByteArray(128) { (it % 256).toByte() }
        )

        val baos = ByteArrayOutputStream()
        val buildResult = BackupArchiveEngine.buildArchive(baos, manifestDraft, sampleEntries)
        assertTrue("Archive creation must succeed", buildResult.isSuccess)

        val finalManifest = buildResult.getOrThrow()
        assertEquals(4, finalManifest.stats.entryCount) // 3 payload + 1 manifest
        assertTrue(finalManifest.checksums.containsKey("data/tests.json"))
        assertTrue(finalManifest.checksums.containsKey("assets/diagrams/test_1/d1.png"))

        // Validate archive
        val bais = ByteArrayInputStream(baos.toByteArray())
        val validationResult = BackupArchiveEngine.validateArchive(bais)
        assertTrue("Archive must be valid", validationResult is BackupValidationResult.Valid)

        val valid = validationResult as BackupValidationResult.Valid
        assertEquals("user_456", valid.manifest.owner.uid)
        assertTrue("Should be marked complete as specified in completeness", valid.isComplete)
        assertEquals(4, valid.totalEntries)
    }

    @Test
    fun testDuplicateZipEntry_rejection() {
        // Construct a corrupt ZIP containing duplicate entries manually
        // 1. Build-time duplicate check
        val manifest = BackupManifest(owner = validOwner)
        val collidingEntries = mapOf(
            "data/tests.json" to "{}".toByteArray(Charsets.UTF_8),
            "data/tests.json " to "{}".toByteArray(Charsets.UTF_8)
        )
        val buildCollisionResult = BackupArchiveEngine.buildArchive(
            ByteArrayOutputStream(),
            manifest,
            collidingEntries
        )
        assertTrue("Duplicate normalized path must fail build", buildCollisionResult.isFailure)
        assertTrue(buildCollisionResult.exceptionOrNull()?.message?.contains("Duplicate entry") == true)

        // 2. Validate-time duplicate check from archive
        val baos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(baos)

        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // First entry
        zipOut.putNextEntry(ZipEntry("data/tests.json"))
        zipOut.write("{}".toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // Duplicate entry with trailing whitespace that normalizes to the exact same canonical path
        zipOut.putNextEntry(ZipEntry("data/tests.json "))
        zipOut.write("{}".toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        zipOut.finish()

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Validation must fail on duplicate entry", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Duplicate entry"))
    }

    @Test
    fun testCorruptArchive_handling() {
        // Random non-zip bytes
        val corruptBytes = "THIS IS NOT A VALID ZIP FILE CONTENT".toByteArray(Charsets.UTF_8)
        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(corruptBytes))
        assertTrue("Validation must fail on non-zip bytes", validation is BackupValidationResult.Invalid)

        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("corrupted") || invalid.reason.contains("Missing mandatory root manifest"))
    }

    @Test
    fun testUnsupportedFutureFormat_rejection() {
        val futureManifest = BackupManifest(
            formatVersion = 999, // Future format
            databaseSchemaVersion = 19,
            owner = validOwner
        )

        val baos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(baos)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(futureManifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        zipOut.finish()

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Future format version must be rejected", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Unsupported future format version"))
    }

    @Test
    fun testUnsupportedFutureSchema_rejection() {
        val futureSchemaManifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 999, // Future schema version beyond 19
            owner = validOwner
        )

        val baos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(baos)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(futureSchemaManifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        zipOut.finish()

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Future schema version must be rejected", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Unsupported future database schema version"))
    }

    @Test
    fun testEntryCount_and_SizeLimitEnforcement() {
        val strictLimits = ArchiveLimits(
            maxEntries = 3,
            maxSingleEntryBytes = 50,
            maxTotalUncompressedBytes = 100
        )

        val manifest = BackupManifest(owner = validOwner)

        // Test single entry exceeding size limit
        val oversizedEntries = mapOf(
            "data/big.bin" to ByteArray(100) // Exceeds 50 bytes
        )
        val buildOversized = BackupArchiveEngine.buildArchive(
            ByteArrayOutputStream(),
            manifest,
            oversizedEntries,
            strictLimits
        )
        assertTrue("Oversized entry must be rejected during build", buildOversized.isFailure)

        // Test entry count exceeding limit
        val tooManyEntries = mapOf(
            "data/1.txt" to ByteArray(5),
            "data/2.txt" to ByteArray(5),
            "data/3.txt" to ByteArray(5),
            "data/4.txt" to ByteArray(5) // 4 entries > maxEntries(3)
        )
        val buildTooMany = BackupArchiveEngine.buildArchive(
            ByteArrayOutputStream(),
            manifest,
            tooManyEntries,
            strictLimits
        )
        assertTrue("Too many entries must be rejected during build", buildTooMany.isFailure)
    }

    @Test
    fun testUnauthenticatedOwner_rejection() {
        // Attempting to create an account-owned manifest with blank UID must fail
        val blankUidResult = BackupArchiveEngine.createAccountOwnedManifest(
            uid = "   ",
            email = "test@example.com"
        )
        assertTrue("Blank UID must fail", blankUidResult.isFailure)
        assertTrue(blankUidResult.exceptionOrNull() is IllegalArgumentException)

        val emptyUidResult = BackupArchiveEngine.createAccountOwnedManifest(
            uid = ""
        )
        assertTrue("Empty UID must fail", emptyUidResult.isFailure)
    }

    @Test
    fun testChecksumTampering_detection() {
        val manifest = BackupManifest(owner = validOwner)
        val payload = mapOf(
            "data/tests.json" to """{"title":"Genuine"}""".toByteArray(Charsets.UTF_8)
        )

        val baos = ByteArrayOutputStream()
        val buildResult = BackupArchiveEngine.buildArchive(baos, manifest, payload)
        assertTrue(buildResult.isSuccess)
        val builtManifest = buildResult.getOrThrow()

        // Construct a tampered archive where the manifest declared hash does not match payload
        val tamperedBaos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(tamperedBaos)

        // Write original manifest
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(builtManifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // Write tampered content
        zipOut.putNextEntry(ZipEntry("data/tests.json"))
        zipOut.write("""{"title":"TAMPERED_MALICIOUS_CONTENT"}""".toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        zipOut.finish()

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(tamperedBaos.toByteArray()))
        assertTrue("Tampered content must be detected", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Checksum mismatch") || invalid.details?.contains("Expected") == true)
    }

    @Test
    fun testUnmanifestedFile_detection() {
        val manifest = BackupManifest(owner = validOwner, checksums = emptyMap())

        val baos = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(baos)

        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        // Write unexpected file not declared in manifest
        zipOut.putNextEntry(ZipEntry("data/injected_secret.txt"))
        zipOut.write("hidden payload".toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        zipOut.finish()

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Unmanifested file must be rejected", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("unmanifested extra file"))
    }
}
