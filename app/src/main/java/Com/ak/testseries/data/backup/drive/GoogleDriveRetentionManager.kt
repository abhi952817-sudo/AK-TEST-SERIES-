package Com.ak.testseries.data.backup.drive

/**
 * Enforces cloud storage retention policies on the user's personal Google Drive
 * to prevent unlimited archive accumulation and quota exhaustion.
 */
object GoogleDriveRetentionManager {

    const val DEFAULT_MAX_BACKUPS_TO_RETAIN = 5

    /**
     * Identifies obsolete backups that exceed the retention limit and prunes them from Drive.
     * Backups are sorted newest first. The [maxToKeep] newest backups are preserved.
     * Any older excess backups are deleted.
     */
    suspend fun pruneExcessBackups(
        driveApiClient: DriveApiClient,
        accessToken: String,
        folderId: String,
        ownerUid: String,
        maxToKeep: Int = DEFAULT_MAX_BACKUPS_TO_RETAIN
    ): PruneResult {
        val listResult = driveApiClient.listBackups(accessToken, folderId, ownerUid)
        if (listResult.isFailure) {
            return PruneResult(
                deletedCount = 0,
                preservedCount = 0,
                errors = listOf(listResult.exceptionOrNull()?.message ?: "Failed to list backups")
            )
        }

        val backups = listResult.getOrThrow()
        if (backups.size <= maxToKeep) {
            return PruneResult(deletedCount = 0, preservedCount = backups.size)
        }

        // Sort newest first by modifiedTimeMillis or createdTimeMillis
        val sorted = backups.sortedByDescending {
            if (it.modifiedTimeMillis > 0) it.modifiedTimeMillis else it.createdTimeMillis
        }
        val toPreserve = sorted.take(maxToKeep)
        val toDelete = sorted.drop(maxToKeep)

        var deleted = 0
        val errors = mutableListOf<String>()

        for (backup in toDelete) {
            val deleteRes = driveApiClient.deleteBackupFile(accessToken, backup.fileId)
            if (deleteRes.isSuccess && deleteRes.getOrThrow()) {
                deleted++
            } else {
                errors.add("Failed to delete ${backup.name} (${backup.fileId}): ${deleteRes.exceptionOrNull()?.message}")
            }
        }

        return PruneResult(
            deletedCount = deleted,
            preservedCount = toPreserve.size,
            errors = errors
        )
    }

    data class PruneResult(
        val deletedCount: Int,
        val preservedCount: Int,
        val errors: List<String> = emptyList()
    )
}
