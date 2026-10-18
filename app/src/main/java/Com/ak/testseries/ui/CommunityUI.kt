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
    val pdfUrl: String? = null,
    val pdfFileName: String? = null,
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
    val topic: String? = null
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
    val timestamp: Long = System.currentTimeMillis()
)

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
        "imageUrl" to post.imageUrl,
        "pdfUrl" to post.pdfUrl,
        "pdfFileName" to post.pdfFileName,
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
        "topic" to post.topic
    )
}

object CommunityShareManager {
    fun shouldAutoShareNewTests(autoShareEnabled: Boolean, isLiveTest: Boolean, uploaderId: String, currentUid: String?, isPublic: Boolean = true): Boolean {
        if (!isPublic) return false
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

    DisposableEffect(targetExam) {
        val db = FirebaseFirestore.getInstance()
        val listenerRegistration = db.collection("Community_Posts")
            .whereEqualTo("targetExam", targetExam)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val newPosts = snapshot.documents.mapNotNull { doc ->
                        try {
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
                                comments = doc.getLong("comments")?.toInt() ?: 0,
                                views = doc.getString("views") ?: "0",
                                subject = doc.getString("subject") ?: "",
                                targetExam = doc.getString("targetExam") ?: targetExam,
                                imageUrl = doc.getString("imageUrl"),
                                pdfUrl = doc.getString("pdfUrl"),
                                pdfFileName = doc.getString("pdfFileName"),
                                isBookmarked = doc.getBoolean("isBookmarked") ?: false,
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
                                topic = doc.getString("topic")
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    posts.clear()
                    // Deduplicate posts: for TEST_SHARE posts, ensure unique (creatorId, testId) composite identity, and deduplicate by post id
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
                    posts.addAll(deduplicated)
                }
            }
            
        onDispose {
            listenerRegistration.remove()
        }
    }

    val updatePostInFirebase = { updatedPost: CommunityPost ->
        val db = FirebaseFirestore.getInstance()
        db.collection("Community_Posts").document(updatedPost.id).set(communityPostToMap(updatedPost))
    }

    fun deletePostInFirebase(post: CommunityPost) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Community_Posts").document(post.id).delete()
    }

    when (currentRoute) {
        CommunityRoute.FEED -> {
            CommunityFeedScreen(
                isAdmin = isAdmin,
                targetExam = targetExam,
                posts = posts.toList(),
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
                onExit = onExit,
                onOpenTest = onOpenTest
            )
        }
        CommunityRoute.CREATE_POST -> {
            CreatePostScreen(
                targetExam = targetExam,
                onBack = { currentRoute = CommunityRoute.FEED },
                onPostCreated = { newPost ->
                    updatePostInFirebase(newPost)
                    currentRoute = CommunityRoute.FEED
                }
            )
        }
        CommunityRoute.POST_DETAIL -> {
            val post = posts.find { it.id == selectedPostId }
            if (post != null) {
                PostDetailScreen(
                    post = post,
                    isAdmin = isAdmin,
                    onBack = { currentRoute = CommunityRoute.FEED },
                    onUpdatePost = { updatedPost ->
                        updatePostInFirebase(updatedPost)
                    },
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
    onOpenTest: (String) -> Unit = {}
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
                    listOf("All") + Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam) + listOf("General")
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
            }.distinctBy { post ->
                if (post.postType == "TEST_SHARE" && !post.testId.isNullOrBlank() && !post.creatorId.isNullOrBlank()) {
                    "TEST_SHARE_${post.creatorId}_${post.testId}"
                } else {
                    post.id
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        isAdmin = isAdmin,
                        onClick = { onNavigateToDetail(post) },
                        onUpdatePost = onUpdatePost,
                        onDeletePost = onDeletePost,
                        onOpenTest = onOpenTest
                    )
                }
                
                if (filteredPosts.isEmpty()) {
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
    onOpenTest: (String) -> Unit = {}
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
            
            // Content
            Text(post.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            val newReaction = if (post.userReaction == "UP") null else "UP"
                            val diff = if (newReaction == "UP") 1 else if (post.userReaction == "UP") -1 else 0
                            onUpdatePost(post.copy(userReaction = newReaction, upvotes = post.upvotes + diff))
                        },
                        tint = if (post.userReaction == "UP") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.upvotes}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.comments}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Icon(
                    if (post.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark",
                    modifier = Modifier.size(20.dp).clickable {
                        onUpdatePost(post.copy(isBookmarked = !post.isBookmarked))
                    },
                    tint = if (post.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Post",
                        modifier = Modifier.size(20.dp).clickable {
                            onDeletePost(post)
                        },
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
    onPostCreated: (CommunityPost) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("General") }
    
    val subjects = remember(targetExam) {
        Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam) + listOf("General")
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
                            if (title.isNotBlank() && content.isNotBlank()) {
                                onPostCreated(
                                    CommunityPost(
                                        authorName = "Ananya Verma",
                                        authorSubtitle = "Aspirant",
                                        title = title,
                                        content = content,
                                        subject = selectedSubject,
                                        targetExam = targetExam,
                                        timeAgo = "Just now",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text("Post")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 5
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Subject", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
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
    onOpenTest: (String) -> Unit = {}
) {
    var commentText by remember { mutableStateOf("") }

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
                    IconButton(onClick = {
                        onUpdatePost(post.copy(isBookmarked = !post.isBookmarked))
                    }) {
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
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Write a comment...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                val newComment = CommunityComment(
                                    authorName = "Ananya Verma",
                                    timeAgo = "Just now",
                                    content = commentText,
                                    timestamp = System.currentTimeMillis()
                                )
                                onUpdatePost(
                                    post.copy(
                                        comments = post.comments + 1,
                                        commentsList = post.commentsList + newComment
                                    )
                                )
                                commentText = ""
                            }
                        },
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
                    onOpenTest = onOpenTest
                )
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Comments (${post.comments})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            items(post.commentsList) { comment ->
                CommentCard(
                    comment = comment,
                    isAdmin = isAdmin,
                    onDelete = {
                        val newComments = post.commentsList.toMutableList()
                        newComments.remove(comment)
                        onUpdatePost(post.copy(commentsList = newComments, comments = newComments.size))
                    }
                )
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: CommunityComment,
    isAdmin: Boolean = false,
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
            if (isAdmin) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(comment.content, fontSize = 14.sp, modifier = Modifier.padding(start = 40.dp))
    }
}
