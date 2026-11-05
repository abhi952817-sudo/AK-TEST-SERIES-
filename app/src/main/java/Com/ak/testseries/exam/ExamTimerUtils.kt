package Com.ak.testseries.exam

/**
 * Calculates elapsed time from the actual configured test duration.
 * Remaining time is clamped to the valid duration range to avoid negative
 * or over-duration values reaching local records or the server.
 */
internal fun calculateElapsedExamTimeSeconds(
    totalDurationSeconds: Long,
    remainingSeconds: Long
): Long {
    val duration = totalDurationSeconds.coerceAtLeast(0L)
    return (duration - remainingSeconds).coerceIn(0L, duration)
}
