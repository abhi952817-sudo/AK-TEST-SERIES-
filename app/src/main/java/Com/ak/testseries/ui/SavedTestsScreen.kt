package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.viewmodel.NEETViewModel

enum class SavedFilterType {
    ALL,
    BOOKMARKED,
    OFFLINE_DOWNLOADED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedTestsScreen(viewModel: NEETViewModel) {
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    var activeFilter by remember { mutableStateOf(SavedFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubjectFilter by remember { mutableStateOf("All") }

    // Filter tests that are either bookmarked or downloaded offline
    val allSavedTests = remember(tests) {
        tests.filter { it.isBookmarked || it.isDownloaded }
    }

    val bookmarkedCount = remember(tests) { tests.count { it.isBookmarked } }
    val downloadedCount = remember(tests) { tests.count { it.isDownloaded } }

    val filteredTests = remember(allSavedTests, activeFilter, searchQuery, selectedSubjectFilter) {
        allSavedTests.filter { test ->
            val matchesType = when (activeFilter) {
                SavedFilterType.ALL -> true
                SavedFilterType.BOOKMARKED -> test.isBookmarked
                SavedFilterType.OFFLINE_DOWNLOADED -> test.isDownloaded
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                test.title.contains(searchQuery, ignoreCase = true) ||
                test.subject.contains(searchQuery, ignoreCase = true) ||
                test.chapter.contains(searchQuery, ignoreCase = true) ||
                test.institute.contains(searchQuery, ignoreCase = true)
            }
            val matchesSubject = if (selectedSubjectFilter == "All") true else {
                test.subject.equals(selectedSubjectFilter, ignoreCase = true)
            }
            matchesType && matchesQuery && matchesSubject
        }
    }

    val availableSubjects = remember(allSavedTests) {
        listOf("All") + allSavedTests.map { it.subject }.filter { it.isNotBlank() }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringRes("Saved Tests"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${allSavedTests.size} saved • $downloadedCount ready offline",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.clickable {
                        viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.PRIVATE_VAULT)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Private Vault",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                // Quick Sync / Stats Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline Sync Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, subject, or chapter...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Type Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeFilter == SavedFilterType.ALL,
                onClick = { activeFilter = SavedFilterType.ALL },
                label = { Text("All (${allSavedTests.size})", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
            FilterChip(
                selected = activeFilter == SavedFilterType.BOOKMARKED,
                onClick = { activeFilter = SavedFilterType.BOOKMARKED },
                label = { Text("Bookmarked ($bookmarkedCount)", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
            FilterChip(
                selected = activeFilter == SavedFilterType.OFFLINE_DOWNLOADED,
                onClick = { activeFilter = SavedFilterType.OFFLINE_DOWNLOADED },
                label = { Text("Offline ($downloadedCount)", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
        }

        // Subject Filter Chips (if more than 1 subject)
        if (availableSubjects.size > 2) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(availableSubjects) { subj ->
                    val isSelected = selectedSubjectFilter == subj
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { selectedSubjectFilter = subj }
                    ) {
                        Text(
                            text = subj,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area
        if (filteredTests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activeFilter) {
                                SavedFilterType.OFFLINE_DOWNLOADED -> Icons.Default.CloudDownload
                                SavedFilterType.BOOKMARKED -> Icons.Default.BookmarkBorder
                                SavedFilterType.ALL -> Icons.Default.FolderOpen
                            },
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (activeFilter) {
                            SavedFilterType.OFFLINE_DOWNLOADED -> "No tests downloaded offline yet"
                            SavedFilterType.BOOKMARKED -> "No bookmarked tests found"
                            SavedFilterType.ALL -> if (searchQuery.isNotEmpty()) "No tests matched your search" else "No saved tests yet"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bookmark tests on the Home screen or tap Download on any test card to access it offline anytime.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTests, key = { it.id }) { test ->
                    val userAttemptsCount = remember(attempts, test.id) {
                        attempts.count { it.testId == test.id }
                    }
                    TestCard(
                        test = test,
                        currentUserAttempts = userAttemptsCount,
                        selectedLanguage = selectedLanguage,
                        onStartTest = { viewModel.initiateTest(test) },
                        onViewSyllabus = { viewModel.viewTestSyllabus(test) },
                        onBookmark = { viewModel.toggleTestBookmark(test) },
                        onDownload = { viewModel.downloadTestOffline(test) },
                        currentUserId = viewModel.currentUserId.value,
                        onDeleteTest = { viewModel.promptDeleteTest(test) },
                        onReportIssue = { viewModel.openReportIssueDialog(test) },
                        onShareToCommunity = { viewModel.shareTestToCommunity(test) }
                    )
                }
            }
        }
    }
}
