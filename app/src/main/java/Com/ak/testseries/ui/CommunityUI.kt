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
    val examId: String? = null,
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
    val mcqOptions: List<String> = emptyList()
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
    return hashMapOf(
        "id" to post.id,
        "authorName" to post.authorName,
        "authorSubtitle" to post.authorSubtitle,
        "isOfficial" to post.isOfficial,
        "isMonitor" to post.isMonitor,
        "timeAgo" to post.timeAgo,
        "title" to post.title,
        "content" to post.content,
        "isPinned" to post.isPinned,
        "upvotes" to post.upvotes,
        "comments" to post.comments,
        "views" to post.views,
        "subject" to post.subject,
        "targetExam" to post.targetExam,
        "examId" to (post.examId ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(post.targetExam)?.examId.orEmpty()),
        "imageUrl" to post.imageUrl,
        "isBookmarked" to post.isBookmarked,
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
        "userReaction" to post.userReaction,
        "mcqOptions" to post.mcqOptions,
        "moderationState" to "VISIBLE",
        // Replies live in Community_Posts/{postId}/Comments/{commentId}; never write a shared array.
    )
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
            examId = test.examId ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)?.examId,
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
    var lastVisiblePostDocument by remember(targetExam) { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var canLoadMorePosts by remember(targetExam) { mutableStateOf(true) }
    var isLoadingMorePosts by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var loadMoreAction by remember(targetExam) { mutableStateOf<(() -> Unit)?>(null) }
    val bookmarkedPostIds = remember { mutableStateListOf<String>() }
    val likedPostIds = remember { mutableStateListOf<String>() }
    val blockedUserIds = remember { mutableStateListOf<String>() }

    val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val currentUid = currentAuthUser?.uid

    // Isolate bookmarks and likes per user under Users_Data/{userId}
    DisposableEffect(currentUid) {
        if (currentUid.isNullOrBlank()) {
            bookmarkedPostIds.clear()
            likedPostIds.clear()
            blockedUserIds.clear()
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
        val blockedListener = db.collection("Users_Data").document(currentUid)
            .collection("Blocked_Users")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    blockedUserIds.clear()
                    blockedUserIds.addAll(snapshot.documents.map { it.id })
                }
            }
        onDispose {
            bookmarkListener.remove()
            likedListener.remove()
            blockedListener.remove()
        }
    }

    DisposableEffect(targetExam) {
        posts.clear()
        lastVisiblePostDocument = null
        canLoadMorePosts = true
        isLoadingMorePosts = false
        loadMoreError = null
        val db = FirebaseFirestore.getInstance()

        fun parseDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CommunityPost? {
            return try {
                if (!isAdmin && doc.getString("moderationState") == "HIDDEN") return null
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
                    examId = doc.getString("examId"),
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
                    commentsList = parsedComments,
                    mcqOptions = (doc.get("mcqOptions") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }

        fun updatePostsList(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
            val newPosts = docs.mapNotNull { parseDocument(it) }
            val existingTail = posts.toList()
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
            val firstPage = deduplicated.distinctBy { it.id }
            val firstPageIds = firstPage.mapTo(mutableSetOf()) { it.id }
            posts.clear()
            posts.addAll(firstPage)
            posts.addAll(existingTail.filterNot { it.id in firstPageIds })
        }

        loadMoreAction = {
            val cursor = lastVisiblePostDocument
            if (cursor != null && canLoadMorePosts && !isLoadingMorePosts) {
                isLoadingMorePosts = true
                loadMoreError = null
                var nextPageQuery: com.google.firebase.firestore.Query = db.collection("Community_Posts")
                    .whereEqualTo("targetExam", targetExam)
                if (!isAdmin) {
                    nextPageQuery = nextPageQuery.whereEqualTo("moderationState", "VISIBLE")
                }
                nextPageQuery
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(20)
                    .get()
                    .addOnSuccessListener { page ->
                        val nextPosts = page.documents.mapNotNull { parseDocument(it) }
                        val existingIds = posts.mapTo(mutableSetOf()) { it.id }
                        posts.addAll(nextPosts.filterNot { it.id in existingIds })
                        lastVisiblePostDocument = page.documents.lastOrNull() ?: cursor
                        canLoadMorePosts = page.documents.size == 20
                        isLoadingMorePosts = false
                    }
                    .addOnFailureListener { error ->
                        isLoadingMorePosts = false
                        loadMoreError = error.localizedMessage ?: "More posts could not be loaded."
                    }
            }
        }

        var feedQuery: com.google.firebase.firestore.Query = db.collection("Community_Posts")
            .whereEqualTo("targetExam", targetExam)
        if (!isAdmin) {
            feedQuery = feedQuery.whereEqualTo("moderationState", "VISIBLE")
        }
        val listenerRegistration = feedQuery
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback query if compound index is building or fails
                    var fallbackQuery: com.google.firebase.firestore.Query = db.collection("Community_Posts")
                        .whereEqualTo("targetExam", targetExam)
                    if (!isAdmin) {
                        fallbackQuery = fallbackQuery.whereEqualTo("moderationState", "VISIBLE")
                    }
                    fallbackQuery.limit(20)
                        .get()
                        .addOnSuccessListener { fallbackSnap ->
                            updatePostsList(fallbackSnap.documents)
                            lastVisiblePostDocument = null
                            canLoadMorePosts = false
                        }
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    updatePostsList(snapshot.documents)
                    lastVisiblePostDocument = snapshot.documents.lastOrNull()
                    canLoadMorePosts = snapshot.documents.size == 20
                }
            }
            
        onDispose {
            listenerRegistration.remove()
            loadMoreAction = null
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

    val createPostInFirebase: (CommunityPost) -> Unit = { newPost ->
        val db = FirebaseFirestore.getInstance()
        db.collection("Community_Posts").document(newPost.id).set(communityPostToMap(newPost))
    }

    val toggleBlockUser: (String) -> Unit = { blockedUid ->
        val uid = currentUid
        if (!uid.isNullOrBlank() && blockedUid.isNotBlank() && blockedUid != uid) {
            val ref = FirebaseFirestore.getInstance().collection("Users_Data").document(uid)
                .collection("Blocked_Users").document(blockedUid)
            if (blockedUserIds.contains(blockedUid)) {
                blockedUserIds.remove(blockedUid)
                ref.delete()
            } else {
                blockedUserIds.add(blockedUid)
                ref.set(mapOf("blockedUid" to blockedUid, "createdAt" to System.currentTimeMillis()))
            }
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
        val db = FirebaseFirestore.getInstance()
        db.collection("Community_Posts").document(post.id).delete()
    }

    // Attach user bookmark and like status to posts
    val postsWithUserStatus = posts.map { post ->
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
                blockedUserIds = blockedUserIds.toList(),
                onUnblockUser = toggleBlockUser,
                onLoadMore = { loadMoreAction?.invoke() },
                canLoadMore = canLoadMorePosts,
                isLoadingMore = isLoadingMorePosts,
                loadMoreError = loadMoreError,
                onExit = onExit,
                onOpenTest = onOpenTest
            )
        }
        CommunityRoute.CREATE_POST -> {
            CreatePostScreen(
                targetExam = targetExam,
                onBack = { currentRoute = CommunityRoute.FEED },
                onPostCreated = { newPost ->
                    createPostInFirebase(newPost)
                    currentRoute = CommunityRoute.FEED
                },
                onMcqCreated = { currentRoute = CommunityRoute.FEED }
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
                    onToggleBlockUser = toggleBlockUser,
                    isAuthorBlocked = post.creatorId?.let { blockedUserIds.contains(it) } == true,
                    onDeletePost = { p ->
                        deletePostInFirebase(p)
                        currentRoute = CommunityRoute.FEED
                    },
                    onOpenTest = onOpenTest
                )
            } else {
                currentRoute = CommunityRoute.FEED
            }
        }
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
    onToggleLike: (CommunityPost) -> Unit = {},
    blockedUserIds: List<String> = emptyList(),
    onUnblockUser: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    canLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    loadMoreError: String? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
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
                        if (blockedUserIds.isNotEmpty()) {
                            IconButton(onClick = { showBlockedUsersDialog = true }) {
                                Icon(Icons.Default.PersonOff, contentDescription = "Manage blocked accounts")
                            }
                        }
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
                CommunityTabItem(
                    title = "My Posts",
                    isSelected = currentTab == CommunityTab.MY_POSTS,
                    onClick = { currentTab = CommunityTab.MY_POSTS },
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
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val isBlockedAuthor = !it.creatorId.isNullOrBlank() && blockedUserIds.contains(it.creatorId)
                val matchesTab = when(currentTab) {
                    CommunityTab.FEED -> !isBlockedAuthor
                    CommunityTab.BOOKMARKS -> it.isBookmarked && !isBlockedAuthor
                    CommunityTab.MY_POSTS -> !currentUid.isNullOrBlank() && it.creatorId == currentUid
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
                if (loadMoreError != null) {
                    item { Text(loadMoreError.orEmpty(), color = MaterialTheme.colorScheme.error) }
                }
                if (canLoadMore) {
                    item {
                        Button(onClick = onLoadMore, enabled = !isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                            if (isLoadingMore) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            else Text("Load more posts")
                        }
                    }
                }
            }
        }
    }
    if (showBlockedUsersDialog) {
        CommunityBlockedUsersDialog(
            blockedUserIds = blockedUserIds,
            onUnblock = onUnblockUser,
            onDismiss = { showBlockedUsersDialog = false }
        )
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
            if (post.postType == "MCQ") {
                CommunityMcqAttemptCard(post)
            }

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
    onBack: () -> Unit,
    onPostCreated: (CommunityPost) -> Unit,
    onMcqCreated: () -> Unit = {}
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var selectedPostType by remember { mutableStateOf("DISCUSSION") }
    var optionCount by remember { mutableStateOf(4) }
    var optionTexts by remember { mutableStateOf(listOf("", "", "", "", "")) }
    var correctOptionIndex by remember { mutableStateOf(0) }
    var creatingMcq by remember { mutableStateOf(false) }
    var mcqRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val authorName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val creatorUid = authUser?.uid
    val examId = remember(targetExam) {
        Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)?.examId
    }
    val subjects = remember(targetExam) {
        Com.ak.testseries.exam.UniversalExamRegistry.getRegisteredSubjectsForExamName(targetExam)
    }
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
        val cleanContent = content.trim()
        if (examId.isNullOrBlank() || selectedSubject !in subjects) return
        if (selectedPostType == "MCQ" && cleanContent.isBlank()) return
        if (selectedPostType != "MCQ" && cleanContent.isBlank() && imageUrl.isNullOrBlank()) return
        if (selectedPostType == "MCQ") {
            val options = optionTexts.take(optionCount).map { it.trim() }
            if (options.any { it.isBlank() } || options.distinctBy { it.lowercase() }.size != optionCount ||
                correctOptionIndex !in options.indices
            ) {
                uploadError = "Enter $optionCount different options and select one correct option."
                return
            }
            creatingMcq = true
            val payload = hashMapOf<String, Any>(
                "title" to title.trim().ifBlank { cleanContent.take(120) },
                "question" to cleanContent,
                "subject" to selectedSubject,
                "targetExam" to targetExam,
                "options" to options,
                "correctOptionIndex" to correctOptionIndex,
                "requestId" to mcqRequestId,
                "authorName" to authorName
            )
            if (!imageUrl.isNullOrBlank()) payload["imageUrl"] = imageUrl
            com.google.firebase.functions.FirebaseFunctions.getInstance()
                .getHttpsCallable("createCommunityMcq")
                .call(payload)
                .addOnSuccessListener {
                    creatingMcq = false
                    mcqRequestId = UUID.randomUUID().toString()
                    onMcqCreated()
                }
                .addOnFailureListener { error ->
                    creatingMcq = false
                    uploadError = error.localizedMessage ?: "MCQ could not be published."
                }
            return
        }
        onPostCreated(
            CommunityPost(
                authorName = authorName,
                authorSubtitle = "$targetExam Aspirant",
                creatorId = uid,
                title = title.trim().ifBlank { cleanContent.take(80) },
                content = cleanContent,
                subject = selectedSubject,
                targetExam = targetExam,
                examId = examId,
                imageUrl = imageUrl,
                postType = selectedPostType,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val uid = creatorUid
                            if (uid != null && selectedSubject in subjects && !isUploading && !creatingMcq &&
                                (if (selectedPostType == "MCQ") content.isNotBlank() else content.isNotBlank() || selectedImageUri != null) &&
                                (selectedPostType != "MCQ" || optionTexts.take(optionCount).all { it.isNotBlank() })
                            ) {
                                val uri = selectedImageUri
                                if (uri == null) {
                                    publish(null)
                                } else {
                                    val mimeType = context.contentResolver.getType(uri) ?: if (uri.scheme == "file") "image/jpeg" else ""
                                    val size = try {
                                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                                    } catch (_: Exception) { null }
                                    if (mimeType !in setOf("image/jpeg", "image/png", "image/webp") || (size != null && size > 5L * 1024L * 1024L)) {
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
                        enabled = creatorUid != null && selectedSubject in subjects &&
                            (if (selectedPostType == "MCQ") content.isNotBlank() else content.isNotBlank() || selectedImageUri != null) &&
                            (selectedPostType != "MCQ" || optionTexts.take(optionCount).all { it.isNotBlank() }) &&
                            !isUploading && !creatingMcq
                    ) {
                        Text(if (isUploading || creatingMcq) "Publishing…" else "Post")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (creatorUid == null) {
                Text("Please sign in to post.", color = MaterialTheme.colorScheme.error)
            }
            Text("Post type", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DISCUSSION" to "Discussion", "DOUBT" to "Doubt", "MCQ" to "MCQ").forEach { (type, label) ->
                    FilterChip(selected = selectedPostType == type, onClick = { selectedPostType = type }, label = { Text(label) })
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
                onValueChange = { title = it.take(120) },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it.take(5000) },
                label = { Text("Write your post") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 4
            )
            if (selectedPostType == "MCQ") {
                Text("One question • one correct option", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 5).forEach { count ->
                        FilterChip(selected = optionCount == count, onClick = { optionCount = count }, label = { Text("$count options") })
                    }
                }
                for (index in 0 until optionCount) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = correctOptionIndex == index, onClick = { correctOptionIndex = index })
                        OutlinedTextField(
                            value = optionTexts[index],
                            onValueChange = { value -> optionTexts = optionTexts.toMutableList().also { it[index] = value.take(500) } },
                            label = { Text("Option ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                Text("The correct answer stays hidden until a student submits an attempt.", style = MaterialTheme.typography.bodySmall)
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
                OutlinedButton(
                    onClick = { cameraLauncher.launch(null) },
                    enabled = selectedImageUri == null && !isUploading
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = selectedImageUri == null && !isUploading
                ) {
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
    onToggleBlockUser: (String) -> Unit = {},
    isAuthorBlocked: Boolean = false
) {
    var commentText by remember { mutableStateOf("") }
    var showReportDialog by remember(post.id) { mutableStateOf(false) }
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
                    val viewerUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (viewerUid != null && !post.creatorId.isNullOrBlank() && viewerUid != post.creatorId) {
                        IconButton(onClick = { onToggleBlockUser(post.creatorId.orEmpty()) }) {
                            Icon(
                                if (isAuthorBlocked) Icons.Default.PersonAdd else Icons.Default.Block,
                                contentDescription = if (isAuthorBlocked) "Unblock author" else "Block author"
                            )
                        }
                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Report post or author")
                        }
                    }
                    IconButton(onClick = { onToggleBookmark(post) }) {
                        Icon(
                            if (post.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
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
    if (showReportDialog) {
        CommunityReportDialog(
            post = post,
            onDismiss = { showReportDialog = false },
            onSubmitted = { showReportDialog = false }
        )
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
