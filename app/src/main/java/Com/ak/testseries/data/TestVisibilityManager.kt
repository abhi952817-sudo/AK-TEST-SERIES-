package Com.ak.testseries.data

/**
 * Manages visibility rules for tests across the app (Home, Search, Test Lists).
 *
 * Rules:
 * - Public Test (isPublic = true): Can appear in normal public Home/Search/Test lists.
 * - My Private Test (isPublic = false, uploaderId == currentUserUid): Visible only to its owner.
 * - Another User's Private Test (isPublic = false, uploaderId != currentUserUid): Hidden from normal lists.
 * - Admin user (isAdmin = true): Can view all tests.
 */
object TestVisibilityManager {

    /**
     * Evaluates whether a test should be visible to the specified user.
     *
     * @param test The test entity to check
     * @param currentUid The currently authenticated Firebase UID (or null/empty if logged out)
     * @param isAdmin True if the user has verified admin privileges
     * @return true if visible in standard lists and search results, false otherwise
     */
    fun isTestVisible(test: TestEntity, currentUid: String?, isAdmin: Boolean = false): Boolean {
        if (isAdmin) return true
        val safeUid = currentUid?.trim().orEmpty()
        val safeUploader = test.uploaderId.trim()
        val isOwner = safeUid.isNotEmpty() && safeUploader.isNotEmpty() && safeUid == safeUploader

        // Tests under review or draft are only visible to their creator and administrators
        if (test.status == "Under Review" || test.status == "Draft") {
            return isOwner
        }

        // Only published public tests are visible to other students in public lists
        if (test.isPublic && test.status == "Published") return true
        return isOwner
    }
}
