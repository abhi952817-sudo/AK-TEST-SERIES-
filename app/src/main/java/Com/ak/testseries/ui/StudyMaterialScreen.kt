package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.viewmodel.NEETViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMaterialScreen(
    onNavigateBack: () -> Unit,
    viewModel: NEETViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val targetExam = userProfile.targetExam.ifEmpty { "NEET UG" }
    val examConfig = remember(targetExam) {
        Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
    }
    val examLabel = examConfig?.shortName ?: targetExam
    val dynamicSubjects = remember(targetExam) {
        val list = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam)
        if (list.isNotEmpty()) list else listOf("Paper 1", "General Studies", "Aptitude")
    }

    val folderColors = listOf(
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF009688),
        Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981)
    )

    val materials = emptyList<Pair<String, String>>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$examLabel Study Notes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search $examLabel notes, formulas, PYQs...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                singleLine = true
            )

            // Dynamic Subject Folders for Target Exam
            Text(
                text = "Subject Folders ($examLabel)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dynamicSubjects.size) { index ->
                    val subjectName = dynamicSubjects[index]
                    val color = folderColors[index % folderColors.size]
                    MaterialFolderBox(subjectName, color)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of PDFs
            Text(
                "$examLabel Revision Materials",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(materials.size) { index ->
                    val item = materials[index]
                    MaterialCard(title = item.first, subtitle = item.second)
                }
            }
        }
    }
}

@Composable
fun MaterialFolderBox(name: String, color: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(90.dp)
            .clickable {
                android.widget.Toast.makeText(context, "Opening $name Notes...", android.widget.Toast.LENGTH_SHORT).show()
            },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
fun MaterialCard(title: String, subtitle: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            android.widget.Toast.makeText(context, "Downloading $title...", android.widget.Toast.LENGTH_SHORT).show()
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Red.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color.Red)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
