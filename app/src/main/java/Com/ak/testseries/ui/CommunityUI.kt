package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.net.Uri
import android.graphics.Bitmap
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import com.google.firebase.firestore.FirebaseFirestore

enum class CommunityRoute {
    FEED, CREATE_POST, POST_DETAIL
}

enum class CommunityTab {
    FEED, BOOKMARKS, MY_POSTS
}

data class CommunityPost(
    val id: String = UUID.randomUUID().toString(),
    val authorName: String = "",
    val authorSubtitle: String = "",
    val isOfficial: Boolean = false,
    val isMonitor: Boolean = false,
    val timeAgo: String = "Just now",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val upvotes: Int = 0,
    val comments: Int = 0,
    val views: String = "0",
    val subject: String = "",
    val targetExam: String = "NEET",
    val imageUrl: String? = null,
    val userReaction: String? = null,
    val commentsList: List<CommunityComment> = emptyList(),
    val isBookmarked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val postType: String = "DISCUSSION",
    val testId: String? = null,
    val creatorId: String? = null,
    val testTitle: String? = null,
    val questionCount: Int? = null,
    val durationMinutes: Int? = null,
    val chapter: String? = null,
    val difficulty: String? = null,
    val classLevel: String? = null,
    val totalMarks: Double? = null,
    val topic: String? = null,
    val quizOptions: List<String> = emptyList(),
    val creatorRole: String = "STUDENT",
    // Transient values: never serialize the answer key or explanation into a public post.
    val quizCorrectOptionIndex: Int? = null,
    val quizExplanation: String? = null,
    val isHidden: Boolean = false
)

data class CommunityComment(
    val id: String = UUID.randomUUID().toString(),
    val authorName: String = "",
    val timeAgo: String = "",
    val content: String = "",
    val upvotes: Int = 0,
    val isCorrectAnswer: Boolean = false,
    val userReaction: String? = null,
    val replies: List<CommunityComment> = emptyList(),
    val creatorId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

fun commentToMap(comment: CommunityComment): Map<String, Any?> {
    return hashMapOf(
        "id" to comment.id,
        "authorName" to comment.authorName,
        "timeAgo" to comment.timeAgo,
        "content" to comment.content,
        "upvotes" to comment.upvotes,
        "isCorrectAnswer" to comment.isCorrectAnswer,
        "userReaction" to comment.userReaction,
        "creatorId" to comment.creatorId,
        "timestamp" to comment.timestamp
    )
}

fun communityPostToMap(post: CommunityPost): Map<String, Any?> {
    val data = hashMapOf<String, Any?>(
        "id" to post.id,
        "authorName" to post.authorName,
        "authorSubtitle" to post.authorSubtitle,
        "isOfficial" to false,
        "isMonitor" to false,
        "timeAgo" to post.timeAgo,
        "title" to post.title,
        "content" to post.content,
        "isPinned" to false,
        "upvotes" to 0,
        "comments" to 0,
        "views" to "0",
        "subject" to post.subject,
        "targetExam" to post.targetExam,
        "imageUrl" to post.imageUrl,
        "isBookmarked" to false,
        "timestamp" to post.timestamp,
        "postType" to post.postType,
        "testId" to post.testId,
        "creatorId" to post.creatorId,
        "testTitle" to post.testTitle,
        "questionCount" to post.questionCount,
        "durationMinutes" to post.durationMinutes,
        "chapter" to post.chapter,
        "difficulty" to post.difficulty,
        "classLevel" to post.classLevel,
        "totalMarks" to post.totalMarks,
        "topic" to post.topic,
        "userReaction" to null,
        "isHidden" to false
    )
    if (post.postType == "MCQ") {
        data["quizOptions"] = post.quizOptions
        data["creatorRole"] = post.creatorRole
    }
    // quizCorrectOptionIndex and quizExplanation are written only to the private answer-key document.
    return data
}

object CommunityShareManager {
    fun shouldAutoShareNewTests(
        autoShareEnabled: Boolean,
        isLiveTest: Boolean,
        uploaderId: String,
        currentUid: String?,
        isPublic: Boolean = true,
        countsTowardRanking: Boolean = true
    ): Boolean {
        if (!isPublic || !countsTowardRanking) return false
        if (!autoShareEnabled) return false
        if (isLiveTest) return false
        return isEligibleToShare(uploaderId, currentUid)
    }

    fun isEligibleToShare(uploaderId: String, currentUid: String?): Boolean {
        if (currentUid.isNullOrBlank()) return false
        return uploaderId == currentUid
    }

    fun isDuplicateShare(
        existingPosts: List<CommunityPost>,
        testId: String,
        currentUid: String
    ): Boolean {
        return existingPosts.any { post ->
            post.postType == "TEST_SHARE" && post.testId == testId && post.creatorId == currentUid
        }
    }

    fun buildTestSharePost(
        test: Com.ak.testseries.data.TestEntity,
        currentUid: String,
        authorName: String,
        authorSubtitle: String,
        targetExam: String,
        totalMarks: Double? = null,
        questionCount: Int? = null,
        durationMinutes: Int? = null,
        postId: String = UUID.randomUUID().toString()
    ): CommunityPost {
        val qCount = questionCount ?: test.questionsCount
        val durMins = durationMinutes ?: test.durationMinutes
        val shareTitle = "Shared Test: ${test.title}"
        val shareContent = if (test.description.isNotBlank()) {
            test.description
        } else {
            "Check out this test: ${test.title} (${test.subject} - $qCount Questions, $durMins Mins)."
        }

        return CommunityPost(
            id = postId,
            authorName = authorName,
            authorSubtitle = authorSubtitle,
            title = shareTitle,
            content = shareContent,
            subject = test.subject,
            targetExam = targetExam,
            timeAgo = "Just now",
            timestamp = System.currentTimeMillis(),
            postType = "TEST_SHARE",
            testId = test.id,
            creatorId = currentUid,
            testTitle = test.title,
            questionCount = qCount,
            durationMinutes = durMins,
            chapter = test.chapter,
            difficulty = test.difficulty,
            classLevel = test.classLevel.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) },
            totalMarks = totalMarks,
            topic = test.topic.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
        )
    }
}

@Composable
fun CommunityScreen(
    isAdmin: Boolean = false,
    targetExam: String = "NEET",
    onExit: () -> Unit = {},
    onOpenTest: (String) -> Unit = {}
) {
    var currentRoute by remember { mutableStateOf(CommunityRoute.FEED) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    
    val posts = remember { mutableStateListOf<CommunityPost>() }
    val bookmarkedPostIds = remember { mutableStateListOf<String>() }
    val likedPostIds = remember { mutableStateListOf<String>() }
    val blockedUserIds = remember { mutableStateListOf<String>() }
    var reportTarget by remember { mutableStateOf<CommunityReportTarget?>(null) }

    val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val currentUid = currentAuthUser?.uid
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(currentUid) {
        if (currentUid.isNullOrBlank()) {
            blockedUserIds.clear()
            return@DisposableEffect onDispose {}
        }
        val blockListener = FirebaseFirestore.getInstance()
            .collection("Users_Data").document(currentUid)
            .collection("Blocked_Users")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    blockedUserIds.clear()
                    blockedUserIds.addAll(snapshot.documents.map { it.id })
                }
            }
        onDispose { blockListener.remove() }
    }

    // Isolate bookmarks and likes per user under Users_Data/{userId}
    DisposableEffect(currentUid) {
        if (currentUid.isNullOrBlank()) {
            bookmarkedPostIds.clear()
            likedPostIds.clear()
            return@DisposableEffect onDispose {}
        }
        val db = FirebaseFirestore.getInstance()
        val bookmarkListener = db.collection("Users_Data").document(currentUid)
            .collection("Bookmarked_Posts")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    bookmarkedPostIds.clear()
                    bookmarkedPostIds.addAll(snapshot.documents.map { it.id })
                }
            }
        val likedListener = db.collection("Users_Data").document(currentUid)
            .collection("Liked_Posts")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    likedPostIds.clear()
                    likedPostIds.addAll(snapshot.documents.map { it.id })
                }
            }
        onDispose {
            bookmarkListener.remove()
            likedListener.remove()
        }
    }

    DisposableEffect(targetExam, isAdmin) {
        val db = FirebaseFirestore.getInstance()

        fun parseDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CommunityPost? {
            return try {
                val rawComments = doc.get("commentsList") as? List<Map<String, Any?>> ?: emptyList()
                val parsedComments = rawComments.mapNotNull { cMap ->
                    try {
                        CommunityComment(
                            id = cMap["id"] as? String ?: UUID.randomUUID().toString(),
                            authorName = cMap["authorName"] as? String ?: "Student",
                            timeAgo = cMap["timeAgo"] as? String ?: "Just now",
                            content = cMap["content"] as? String ?: "",
                            upvotes = (cMap["upvotes"] as? Number)?.toInt() ?: 0,
                            isCorrectAnswer = cMap["isCorrectAnswer"] as? Boolean ?: false,
                            userReaction = cMap["userReaction"] as? String,
                            timestamp = (cMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                CommunityPost(
                    id = doc.id,
                    authorName = doc.getString("authorName") ?: "Unknown",
                    authorSubtitle = doc.getString("authorSubtitle") ?: "",
                    isOfficial = doc.getBoolean("isOfficial") ?: false,
                    isMonitor = doc.getBoolean("isMonitor") ?: false,
                    timeAgo = doc.getString("timeAgo") ?: "Just now",
                    title = doc.getString("title") ?: "",
                    content = doc.getString("content") ?: "",
                    isPinned = doc.getBoolean("isPinned") ?: false,
                    upvotes = doc.getLong("upvotes")?.toInt() ?: 0,
                    comments = (doc.getLong("comments")?.toInt() ?: parsedComments.size),
                    views = doc.getString("views") ?: "0",
                    subject = doc.getString("subject") ?: "",
                    targetExam = doc.getString("targetExam") ?: targetExam,
                    imageUrl = doc.getString("imageUrl"),
                    isBookmarked = false,
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    postType = doc.getString("postType") ?: "DISCUSSION",
                    testId = doc.getString("testId"),
                    creatorId = doc.getString("creatorId"),
                    testTitle = doc.getString("testTitle"),
                    questionCount = doc.getLong("questionCount")?.toInt(),
                    durationMinutes = doc.getLong("durationMinutes")?.toInt(),
                    chapter = doc.getString("chapter"),
                    difficulty = doc.getString("difficulty"),
                    classLevel = doc.getString("classLevel"),
                    totalMarks = doc.getDouble("totalMarks") ?: doc.getLong("totalMarks")?.toDouble(),
                    topic = doc.getString("topic"),
                    userReaction = doc.getString("userReaction"),
                    quizOptions = (doc.get("quizOptions") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    isHidden = doc.getBoolean("isHidden") ?: false,
                    commentsList = parsedComments
                )
            } catch (e: Exception) {
                null
            }
        }

        fun updatePostsList(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
            val newPosts = docs.mapNotNull { parseDocument(it) }
            posts.clear()
            val seenShareKeys = mutableSetOf<String>()
            val deduplicated = mutableListOf<CommunityPost>()
            val sortedList = newPosts.sortedWith(compareByDescending<CommunityPost> { it.isPinned }.thenByDescending { it.timestamp })
            for (post in sortedList) {
                if (post.postType == "TEST_SHARE" && !post.testId.isNullOrBlank() && !post.creatorId.isNullOrBlank()) {
                    val compositeKey = "${post.creatorId}_${post.testId}"
                    if (seenShareKeys.add(compositeKey)) {
                        deduplicated.add(post)
                    }
                } else {
                    deduplicated.add(post)
                }
            }
            posts.addAll(deduplicated.distinctBy { it.id })
        }

        var feedQuery: com.google.firebase.firestore.Query = db.collection("Community_Posts")
            .whereEqualTo("targetExam", targetExam)
        if (!isAdmin) {
            feedQuery = feedQuery.whereEqualTo("isHidden", false)
        }
        val listenerRegistration = feedQuery
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Keep the visibility boundary in the fallback query as well.
                    var fallbackQuery: com.google.firebase.firestore.Query = db.collection("Community_Posts")
                        .whereEqualTo("targetExam", targetExam)
                    if (!isAdmin) fallbackQuery = fallbackQuery.whereEqualTo("isHidden", false)
                    fallbackQuery.limit(20).get()
                        .addOnSuccessListener { fallbackSnap ->
                            updatePostsList(fallbackSnap.documents)
                        }
                    return@addSnapshotListener
                }
                if (snapshot != null) updatePostsList(snapshot.documents)
            }
            
        onDispose {
            listenerRegistration.remove()
        }
    }

    val toggleBookmark: (CommunityPost) -> Unit = { post ->
        val uid = currentUid
        if (!uid.isNullOrBlank()) {
            val db = FirebaseFirestore.getInstance()
            val bookmarkDoc = db.collection("Users_Data").document(uid).collection("Bookmarked_Posts").document(post.id)
            if (bookmarkedPostIds.contains(post.id)) {
                bookmarkedPostIds.remove(post.id)
                bookmarkDoc.delete()
            } else {
                bookmarkedPostIds.add(post.id)
                bookmarkDoc.set(mapOf("postId" to post.id, "timestamp" to System.currentTimeMillis()))
            }
        }
    }

    val toggleLikePost: (CommunityPost) -> Unit = { post ->
        val uid = currentUid
        if (!uid.isNullOrBlank()) {
            val db = FirebaseFirestore.getInstance()
            val likeDoc = db.collection("Users_Data").document(uid).collection("Liked_Posts").document(post.id)
            val isLiked = likedPostIds.contains(post.id)
            if (isLiked) {
                likedPostIds.remove(post.id)
                likeDoc.delete()
            } else {
                likedPostIds.add(post.id)
                likeDoc.set(mapOf("postId" to post.id, "timestamp" to System.currentTimeMillis()))
            }
        }
    }

    val addCommentToPost: (CommunityPost, CommunityComment) -> Unit = { targetPost, newComment ->
        val uid = currentUid
        if (!uid.isNullOrBlank() && newComment.content.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("Community_Posts").document(targetPost.id)
                .collection("Comments").document(newComment.id)
                .set(commentToMap(newComment.copy(creatorId = uid)))
        }
    }

    val createPostInFirebase: (CommunityPost, (Boolean) -> Unit) -> Unit = { newPost, onComplete ->
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("Community_Posts").document(newPost.id)
        val batch = db.batch()
        batch.set(postRef, communityPostToMap(newPost))
        var validPayload = true
        if (newPost.postType == "MCQ") {
            val answerIndex = newPost.quizCorrectOptionIndex
            if (answerIndex != null && answerIndex in newPost.quizOptions.indices) {
                val answerRef = db.collection("Community_MCQ_AnswerKeys").document(newPost.id)
                batch.set(answerRef, mapOf(
                    "creatorId" to newPost.creatorId,
                    "correctOptionIndex" to answerIndex,
                    "explanation" to (newPost.quizExplanation ?: ""),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            } else {
                validPayload = false
            }
        }
        if (validPayload) {
            batch.commit()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { error ->
                    android.widget.Toast.makeText(context, error.localizedMessage ?: "Post could not be published.", android.widget.Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
        } else {
            onComplete(false)
        }
    }

    val updatePostInFirebase: (CommunityPost) -> Unit = { updatedPost ->
        val db = FirebaseFirestore.getInstance()
        if (currentUid == updatedPost.creatorId || isAdmin) {
            db.collection("Community_Posts").document(updatedPost.id).update(
                mapOf(
                    "title" to updatedPost.title,
                    "content" to updatedPost.content,
                    "subject" to updatedPost.subject
                )
            )
        }
    }

    fun deletePostInFirebase(post: CommunityPost) {
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("deleteCommunityPost")
            .call(mapOf("postId" to post.id))
            .addOnSuccessListener {
                posts.removeAll { it.id == post.id }
            }
            .addOnFailureListener { error ->
                android.widget.Toast.makeText(
                    context,
                    error.localizedMessage ?: "Post could not be deleted.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
    }

    // Attach user bookmark and like status to posts
    val postsWithUserStatus = posts.filter { post ->
        (isAdmin || !post.isHidden) && (isAdmin || post.creatorId !in blockedUserIds)
    }.map { post ->
        post.copy(
            isBookmarked = bookmarkedPostIds.contains(post.id),
            userReaction = if (likedPostIds.contains(post.id)) "UP" else null
        )
    }

    when (currentRoute) {
        CommunityRoute.FEED -> {
            CommunityFeedScreen(
                isAdmin = isAdmin,
                targetExam = targetExam,
                posts = postsWithUserStatus,
                onNavigateToCreate = { currentRoute = CommunityRoute.CREATE_POST },
                onNavigateToDetail = { post ->
                    selectedPostId = post.id
                    currentRoute = CommunityRoute.POST_DETAIL
                },
                onUpdatePost = { updatedPost ->
                    updatePostInFirebase(updatedPost)
                },
                onDeletePost = { post ->
                    deletePostInFirebase(post)
                },
                onToggleBookmark = toggleBookmark,
                onToggleLike = toggleLikePost,
                onExit = onExit,
                onOpenTest = onOpenTest
            )
        }
        CommunityRoute.CREATE_POST -> {
            CreatePostScreen(
                targetExam = targetExam,
                isAdmin = isAdmin,
                onBack = { currentRoute = CommunityRoute.FEED },
                onPostCreated = { newPost, onComplete ->
                    createPostInFirebase(newPost) { success ->
                        if (success) currentRoute = CommunityRoute.FEED
                        onComplete(success)
                    }
                }
            )
        }
        CommunityRoute.POST_DETAIL -> {
            val post = postsWithUserStatus.find { it.id == selectedPostId }
            if (post != null) {
                PostDetailScreen(
                    post = post,
                    isAdmin = isAdmin,
                    onBack = { currentRoute = CommunityRoute.FEED },
                    onUpdatePost = { updatedPost ->
                        updatePostInFirebase(updatedPost)
                    },
                    onToggleBookmark = toggleBookmark,
                    onToggleLike = toggleLikePost,
                    onAddComment = addCommentToPost,
                    onDeletePost = { p ->
                        deletePostInFirebase(p)
                        currentRoute = CommunityRoute.FEED
                    },
                    onReportPost = { p ->
                        reportTarget = CommunityReportTarget(postId = p.id, reportedUserId = p.creatorId)
                    },
                    onReportUser = { p ->
                        reportTarget = CommunityReportTarget(reportedUserId = p.creatorId)
                    },
                    onBlockAuthor = { p ->
                        val uid = currentUid
                        val authorUid = p.creatorId
                        if (!uid.isNullOrBlank() && !authorUid.isNullOrBlank() && uid != authorUid) {
                            FirebaseFirestore.getInstance().collection("Users_Data").document(uid)
                                .collection("Blocked_Users").document(authorUid)
                                .set(mapOf("blockedUid" to authorUid, "createdAt" to System.currentTimeMillis()))
                                .addOnFailureListener { error ->
                                    android.widget.Toast.makeText(context, error.localizedMessage ?: "Block action failed.", android.widget.Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onOpenTest = onOpenTest
                )
            } else {
                currentRoute = CommunityRoute.FEED
            }
        }
    }

    reportTarget?.let { target ->
        CommunityReportDialog(
            target = target,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, evidenceUri ->
                val uid = currentUid
                if (uid.isNullOrBlank()) {
                    reportTarget = null
                } else {
                    val db = FirebaseFirestore.getInstance()
                    val reportRef = db.collection("Community_Reports").document()
                    val reportId = reportRef.id
                    fun saveReport(evidencePath: String?) {
                        val payload = hashMapOf<String, Any?>(
                            "reporterId" to uid,
                            "reason" to reason,
                            "status" to "OPEN",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        target.postId?.let { payload["postId"] = it }
                        target.reportedUserId?.let { payload["reportedUserId"] = it }
                        evidencePath?.let { payload["evidenceImagePath"] = it }
                        reportRef.set(payload)
                            .addOnSuccessListener {
                                reportTarget = null
                                android.widget.Toast.makeText(
                                    context,
                                    "Report submitted",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener {
                                android.widget.Toast.makeText(
                                    context,
                                    it.localizedMessage ?: "Report could not be submitted",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    if (evidenceUri == null) {
                        saveReport(null)
                    } else {
                        val mime = context.contentResolver.getType(evidenceUri) ?: ""
                        val size = try {
                            context.contentResolver.openAssetFileDescriptor(evidenceUri, "r")?.use { it.length }
                        } catch (_: Exception) { null }
                        if (mime !in setOf("image/jpeg", "image/png", "image/webp") ||
                            (size != null && size >= 5L * 1024L * 1024L)) {
                            android.widget.Toast.makeText(context, "Only JPEG, PNG or WebP evidence up to 5 MB is allowed", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            val path = "community_report_evidence/$uid/$reportId"
                            val fileRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(path)
                            val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mime).build()
                            fileRef.putFile(evidenceUri, metadata)
                                .addOnSuccessListener { saveReport(path) }
                                .addOnFailureListener { error ->
                                    android.widget.Toast.makeText(context, error.localizedMessage ?: "Evidence upload failed", android.widget.Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeedScreen(
    isAdmin: Boolean,
    targetExam: String,
    posts: List<CommunityPost>,
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (CommunityPost) -> Unit,
    onUpdatePost: (CommunityPost) -> Unit,
    onDeletePost: (CommunityPost) -> Unit,
    onExit: () -> Unit,
    onOpenTest: (String) -> Unit = {},
    onToggleBookmark: (CommunityPost) -> Unit = {},
    onToggleLike: (CommunityPost) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(CommunityTab.FEED) }
    var showExitDialog by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf("All") }
    
    val isHindi = false

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(if (isHindi) "बाहर निकलें?" else "Exit Community?", fontWeight = FontWeight.Bold) },
            text = { Text(if (isHindi) "क्या आप कम्युनिटी सेक्शन से बाहर जाना चाहते हैं?" else "Are you sure you want to exit the community section?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExit()
                }) {
                    Text(if (isHindi) "हाँ" else "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(if (isHindi) "नहीं" else "No")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search community...", fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(25.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                             isSearchActive = false
                             searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("$targetExam Community", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = { showExitDialog = true }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Post")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F5F5))) {
            // Tabs
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                CommunityTabItem(
                    title = "Feed",
                    isSelected = currentTab == CommunityTab.FEED,
                    onClick = { currentTab = CommunityTab.FEED },
                    modifier = Modifier.weight(1f)
                )
                CommunityTabItem(
                    title = "Bookmarks",
                    isSelected = currentTab == CommunityTab.BOOKMARKS,
                    onClick = { currentTab = CommunityTab.BOOKMARKS },
                    modifier = Modifier.weight(1f)
                )
            }

            // Subject Filter
            if (currentTab == CommunityTab.FEED) {
                val subjects = remember(targetExam) {
                    listOf("All") + Com.ak.testseries.exam.UniversalExamRegistry.getRegisteredSubjectsForExamName(targetExam)
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subjects.size) { index ->
                        val subject = subjects[index]
                        FilterChip(
                            selected = selectedSubject == subject,
                            onClick = { selectedSubject = subject },
                            label = { Text(subject) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Feed
            val filteredPosts = posts.filter {
                val matchesTab = when(currentTab) {
                    CommunityTab.FEED -> true
                    CommunityTab.BOOKMARKS -> it.isBookmarked
                    else -> true
                }
                val matchesSubject = if (currentTab == CommunityTab.FEED && selectedSubject != "All") {
                    it.subject == selectedSubject
                } else true
                
                val matchesSearch = if (searchQuery.isNotBlank()) {
                    it.title.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true)
                } else true
                
                matchesTab && matchesSubject && matchesSearch
            }
            
            // Deduplicate safely by unique post id
            val finalPosts = filteredPosts.distinctBy { it.id }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(finalPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        isAdmin = isAdmin,
                        onClick = { onNavigateToDetail(post) },
                        onUpdatePost = onUpdatePost,
                        onDeletePost = onDeletePost,
                        onOpenTest = onOpenTest,
                        onToggleBookmark = onToggleBookmark,
                        onToggleLike = onToggleLike
                    )
                }
                
                if (finalPosts.isEmpty()) {
                    item {
                        Text(
                            text = "No posts found.",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityTabItem(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
fun PostCard(
    post: CommunityPost,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onUpdatePost: (CommunityPost) -> Unit,
    onDeletePost: (CommunityPost) -> Unit = {},
    onOpenTest: (String) -> Unit = {},
    onToggleBookmark: (CommunityPost) -> Unit = {},
    onToggleLike: (CommunityPost) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(post.authorName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (post.isOfficial) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = "Official", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(post.authorSubtitle, fontSize = 12.sp, color = Color.Gray)
                }
                Text(post.timeAgo, fontSize = 12.sp, color = Color.Gray)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            post.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Community post image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Content
            if (post.title.isNotBlank()) Text(post.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            // If TEST_SHARE, show Test Details and Action
            if (post.postType == "TEST_SHARE") {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = post.testTitle ?: post.title.ifBlank { "Shared Test" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!post.targetExam.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = post.targetExam,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (!post.difficulty.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = post.difficulty,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val chapterTopic = when {
                            !post.chapter.isNullOrBlank() && !post.chapter.equals("All", ignoreCase = true) && !post.topic.isNullOrBlank() && !post.topic.equals("All", ignoreCase = true) -> "${post.chapter} • ${post.topic}"
                            !post.chapter.isNullOrBlank() && !post.chapter.equals("All", ignoreCase = true) -> post.chapter
                            !post.topic.isNullOrBlank() && !post.topic.equals("All", ignoreCase = true) -> post.topic
                            else -> null
                        }

                        val marksText = post.totalMarks?.takeIf { it > 0.0 }?.let {
                            val formatted = if (it == it.toLong().toDouble()) it.toLong().toString() else String.format(java.util.Locale.US, "%.1f", it)
                            "$formatted Marks"
                        }

                        val line1 = listOfNotNull(
                            post.subject.takeIf { it.isNotBlank() },
                            post.classLevel?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) },
                            chapterTopic
                        ).joinToString(" • ")

                        val line2 = listOfNotNull(
                            post.questionCount?.takeIf { it > 0 }?.let { "$it Qs" },
                            post.durationMinutes?.takeIf { it > 0 }?.let { "$it mins" },
                            marksText
                        ).joinToString(" • ")

                        if (line1.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = line1,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (line2.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = line2,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val hasValidTestId = !post.testId.isNullOrBlank()
                        if (hasValidTestId) {
                            Button(
                                onClick = {
                                    val tId = post.testId
                                    if (!tId.isNullOrBlank()) {
                                        onOpenTest(tId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Attempt Test",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Attempt Test", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Test unavailable",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (post.userReaction == "UP") Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Upvote",
                        modifier = Modifier.size(20.dp).clickable {
                            onToggleLike(post)
                        },
                        tint = if (post.userReaction == "UP") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replies", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Icon(
                    if (post.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark",
                    modifier = Modifier.size(20.dp).clickable {
                        onToggleBookmark(post)
                    },
                    tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (isAdmin || currentUid == post.creatorId) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Post",
                        modifier = Modifier.size(20.dp).clickable { onDeletePost(post) },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    targetExam: String,
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onPostCreated: (CommunityPost, (Boolean) -> Unit) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedPostType by remember { mutableStateOf("DISCUSSION") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var correctOptionIndex by remember { mutableStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    val optionValues = remember { mutableStateListOf("", "", "", "") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val authorName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val creatorUid = authUser?.uid
    val subjects = remember(targetExam) {
        Com.ak.testseries.exam.UniversalExamRegistry.getRegisteredSubjectsForExamName(targetExam)
    }
    val isMcq = selectedPostType == "MCQ"
    val isDoubt = selectedPostType == "DOUBT"
    val cleanOptions = optionValues.map { it.trim() }
    val validMcq = !isMcq || (
        title.trim().isNotEmpty() &&
        cleanOptions.size in 4..5 &&
        cleanOptions.all { it.isNotEmpty() && it.length <= 500 } &&
        cleanOptions.map { it.lowercase() }.distinct().size == cleanOptions.size &&
        correctOptionIndex in cleanOptions.indices
    )
    val hasPublishableBody = when (selectedPostType) {
        "MCQ", "DOUBT" -> title.trim().isNotEmpty()
        else -> content.trim().isNotEmpty() || selectedImageUri != null
    }
    val canPublish = creatorUid != null && selectedSubject in subjects &&
        hasPublishableBody && validMcq && !isUploading && !isPublishing

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        uploadError = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val imageFile = java.io.File(context.cacheDir, "community_capture_${UUID.randomUUID()}.jpg")
                FileOutputStream(imageFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                }
                selectedImageUri = Uri.fromFile(imageFile)
                uploadError = null
            } catch (error: Exception) {
                uploadError = error.localizedMessage ?: "Camera image could not be saved."
            }
        }
    }

    fun publish(imageUrl: String?) {
        val uid = creatorUid ?: return
        if (selectedSubject !in subjects || !hasPublishableBody || !validMcq) return
        val post = CommunityPost(
            authorName = authorName,
            authorSubtitle = "$targetExam Aspirant",
            creatorId = uid,
            creatorRole = if (isAdmin) "ADMIN" else "STUDENT",
            title = title.trim().ifBlank { content.trim().take(80) },
            content = content.trim(),
            subject = selectedSubject,
            targetExam = targetExam,
            imageUrl = imageUrl,
            postType = selectedPostType,
            quizOptions = if (isMcq) cleanOptions else emptyList(),
            quizCorrectOptionIndex = if (isMcq) correctOptionIndex else null,
            quizExplanation = if (isMcq) explanation.trim() else null,
            timestamp = System.currentTimeMillis()
        )
        isPublishing = true
        onPostCreated(post) { success ->
            isPublishing = false
            if (success) onBack()
            else uploadError = "Post could not be published. Please check your connection and try again."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isMcq) "Create MCQ" else if (isDoubt) "Ask a Doubt" else "Create Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (canPublish) {
                                val uid = creatorUid ?: return@Button
                                val uri = selectedImageUri
                                if (uri == null) {
                                    publish(null)
                                } else {
                                    val mimeType = context.contentResolver.getType(uri)
                                        ?: if (uri.scheme == "file") "image/jpeg" else ""
                                    val size = try {
                                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                                    } catch (_: Exception) { null }
                                    if (mimeType !in setOf("image/jpeg", "image/png", "image/webp") ||
                                        (size != null && size >= 5L * 1024L * 1024L)) {
                                        uploadError = "Only JPEG, PNG or WebP images up to 5 MB are allowed."
                                    } else {
                                        isUploading = true
                                        val fileRef = FirebaseStorage.getInstance().reference
                                            .child("community_images/$uid/${UUID.randomUUID()}")
                                        val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
                                        fileRef.putFile(uri, metadata)
                                            .addOnSuccessListener {
                                                fileRef.downloadUrl
                                                    .addOnSuccessListener { downloadUri ->
                                                        isUploading = false
                                                        publish(downloadUri.toString())
                                                    }
                                                    .addOnFailureListener { error ->
                                                        isUploading = false
                                                        uploadError = error.localizedMessage ?: "Image URL could not be loaded."
                                                    }
                                            }
                                            .addOnFailureListener { error ->
                                                isUploading = false
                                                uploadError = error.localizedMessage ?: "Image upload failed. Please retry."
                                            }
                                    }
                                }
                            }
                        },
                        enabled = canPublish
                    ) {
                        Text(when {
                            isUploading -> "Uploading…"
                            isPublishing -> "Publishing…"
                            else -> "Post"
                        })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (creatorUid == null) {
                Text("Please sign in to post.", color = MaterialTheme.colorScheme.error)
            }
            Text("Post type", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DISCUSSION" to "Discussion", "DOUBT" to "Doubt", "MCQ" to "MCQ").forEach { (type, label) ->
                    FilterChip(
                        selected = selectedPostType == type,
                        onClick = {
                            selectedPostType = type
                            uploadError = null
                        },
                        label = { Text(label) }
                    )
                }
            }
            Text("Subject (required)", fontWeight = FontWeight.Bold)
            if (subjects.isEmpty()) {
                Text("No registered subjects are available for this exam. Posting is disabled.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjects.size) { index ->
                        val subject = subjects[index]
                        FilterChip(
                            selected = selectedSubject == subject,
                            onClick = { selectedSubject = subject },
                            label = { Text(subject) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(if (isMcq || isDoubt) 2000 else 120) },
                label = { Text(if (isMcq) "Question (required)" else if (isDoubt) "Doubt question (required)" else "Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (isMcq || isDoubt) 2 else 1
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it.take(5000) },
                label = { Text(if (isMcq) "Extra context (optional)" else if (isDoubt) "Details (optional)" else "Write your post") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            if (isMcq) {
                Text("Options (choose 4 or 5)", fontWeight = FontWeight.Bold)
                optionValues.forEachIndexed { index, option ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { optionValues[index] = it.take(500) },
                            label = { Text("Option " + ('A' + index)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = correctOptionIndex == index,
                                onClick = { correctOptionIndex = index }
                            )
                            Text("Mark as correct answer", fontSize = 12.sp)
                        }
                    }
                }
                if (optionValues.size == 4) {
                    TextButton(onClick = { optionValues.add("") }) { Text("Add fifth option") }
                } else {
                    TextButton(onClick = {
                        optionValues.removeAt(optionValues.lastIndex)
                        if (correctOptionIndex >= optionValues.size) correctOptionIndex = 0
                    }) { Text("Remove fifth option") }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it.take(2000) },
                    label = { Text("Explanation (optional, shown after attempt)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text("The correct answer is stored privately and revealed only after a student's first attempt.", fontSize = 11.sp)
            }
            selectedImageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected image preview",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp))
                )
                TextButton(onClick = { selectedImageUri = null }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Remove image")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { cameraLauncher.launch(null) }, enabled = selectedImageUri == null && !isUploading) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }, enabled = selectedImageUri == null && !isUploading) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery")
                }
            }
            uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: CommunityPost,
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onUpdatePost: (CommunityPost) -> Unit,
    onDeletePost: (CommunityPost) -> Unit = {},
    onOpenTest: (String) -> Unit = {},
    onToggleBookmark: (CommunityPost) -> Unit = {},
    onToggleLike: (CommunityPost) -> Unit = {},
    onAddComment: (CommunityPost, CommunityComment) -> Unit = { _, _ -> },
    onReportPost: (CommunityPost) -> Unit = {},
    onReportUser: (CommunityPost) -> Unit = {},
    onBlockAuthor: (CommunityPost) -> Unit = {}
) {
    var commentText by remember { mutableStateOf("") }
    var showActionsMenu by remember { mutableStateOf(false) }
    var comments by remember(post.id) { mutableStateOf(post.commentsList.distinctBy { it.id }) }
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    DisposableEffect(post.id) {
        val registration = FirebaseFirestore.getInstance()
            .collection("Community_Posts").document(post.id)
            .collection("Comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val storedReplies = snapshot.documents.mapNotNull { doc ->
                        val text = doc.getString("content") ?: return@mapNotNull null
                        CommunityComment(
                            id = doc.id,
                            authorName = doc.getString("authorName") ?: "Student",
                            timeAgo = doc.getString("timeAgo") ?: "",
                            content = text,
                            creatorId = doc.getString("creatorId"),
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                    comments = (post.commentsList + storedReplies).distinctBy { it.id }
                        .sortedBy { it.timestamp }
                }
            }
        onDispose { registration.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleBookmark(post) }) {
                        Icon(
                            if (post.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Community actions")
                        }
                        DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Report post") },
                                onClick = { showActionsMenu = false; onReportPost(post) }
                            )
                            DropdownMenuItem(
                                text = { Text("Report author") },
                                onClick = { showActionsMenu = false; onReportUser(post) }
                            )
                            if (currentUid != null && currentUid != post.creatorId) {
                                DropdownMenuItem(
                                    text = { Text("Block author") },
                                    onClick = { showActionsMenu = false; onBlockAuthor(post) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Write a reply...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        enabled = currentUid != null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val uid = currentUid
                            if (!uid.isNullOrBlank() && commentText.isNotBlank()) {
                                val auth = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                onAddComment(
                                    post,
                                    CommunityComment(
                                        authorName = auth?.displayName?.takeIf { it.isNotBlank() } ?: "Student",
                                        timeAgo = "Just now",
                                        content = commentText.trim(),
                                        creatorId = uid,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                commentText = ""
                            }
                        },
                        enabled = currentUid != null && commentText.isNotBlank(),
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                PostCard(
                    post = post,
                    isAdmin = isAdmin,
                    onClick = {},
                    onUpdatePost = onUpdatePost,
                    onDeletePost = onDeletePost,
                    onOpenTest = onOpenTest,
                    onToggleBookmark = onToggleBookmark,
                    onToggleLike = onToggleLike
                )
                if (post.postType == "MCQ") {
                    CommunityMcqAttemptCard(post)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Replies (${comments.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(comments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    canDelete = isAdmin || comment.creatorId == currentUid,
                    onDelete = {
                        FirebaseFirestore.getInstance()
                            .collection("Community_Posts").document(post.id)
                            .collection("Comments").document(comment.id).delete()
                    }
                )
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: CommunityComment,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(comment.authorName.take(1).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(comment.timeAgo, fontSize = 11.sp, color = Color.Gray)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(comment.content, fontSize = 14.sp, modifier = Modifier.padding(start = 40.dp))
    }
}
