package Com.ak.testseries.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Lifecycle states for PDF Storage and Processing Sessions.
 */
enum class PdfSessionStatus {
    PENDING,
    COPYING,
    READY,
    PARSING,
    EXTRACTED,
    VALIDATING,
    COMPLETED,
    FAILED,
    PAUSED
}

/**
 * Persistent Room Entity for PDF Processing Sessions.
 * Tracks storage location, integrity hash, page progress, and status across app restarts and process kills.
 */
@Entity(
    tableName = "pdf_sessions",
    indices = [
        Index(value = ["fileHash"]),
        Index(value = ["status"])
    ]
)
data class PdfSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val pageCount: Int,
    val processedPages: Int,
    val lastProcessedPage: Int,
    val status: String, // String representation of PdfSessionStatus
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val extractedTextPath: String? = null,
    val examTarget: String? = null
)

@Dao
interface PdfSessionDao {
    @Query("SELECT * FROM pdf_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<PdfSessionEntity>>

    @Query("SELECT * FROM pdf_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessionsList(): List<PdfSessionEntity>

    @Query("SELECT * FROM pdf_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): PdfSessionEntity?

    @Query("SELECT * FROM pdf_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun getSessionFlowById(sessionId: String): Flow<PdfSessionEntity?>

    @Query("SELECT * FROM pdf_sessions WHERE fileHash = :fileHash ORDER BY createdAt DESC LIMIT 1")
    suspend fun getSessionByHash(fileHash: String): PdfSessionEntity?

    @Query("SELECT * FROM pdf_sessions WHERE status IN ('PENDING', 'COPYING', 'READY', 'PARSING', 'PAUSED') ORDER BY updatedAt DESC")
    fun getActiveSessions(): Flow<List<PdfSessionEntity>>

    @Query("SELECT * FROM pdf_sessions WHERE status IN ('PENDING', 'COPYING', 'READY', 'PARSING', 'PAUSED') ORDER BY updatedAt DESC")
    suspend fun getActiveSessionsList(): List<PdfSessionEntity>

    @Query("SELECT * FROM pdf_sessions WHERE status IN ('PARSING', 'PAUSED') ORDER BY updatedAt DESC")
    suspend fun getResumableSessions(): List<PdfSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PdfSessionEntity)

    @Update
    suspend fun updateSession(session: PdfSessionEntity)

    @Query("UPDATE pdf_sessions SET status = :status, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_sessions SET processedPages = :processedPages, lastProcessedPage = :lastProcessedPage, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateProgress(sessionId: String, processedPages: Int, lastProcessedPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_sessions SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateError(sessionId: String, errorMessage: String?, status: String = "FAILED", updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_sessions SET extractedTextPath = :extractedTextPath, status = :status, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateExtractedPath(sessionId: String, extractedTextPath: String, status: String = "EXTRACTED", updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM pdf_sessions")
    suspend fun clearAllSessions()
}
