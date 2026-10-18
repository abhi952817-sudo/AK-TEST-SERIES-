package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.viewmodel.NEETViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: NEETViewModel
) {
    var selectedPlan by remember { mutableStateOf(1) } // 0: Free, 1: Pro, 2: Elite

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium Plans", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Image/Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        contentDescription = "Premium",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFFFFD700)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Upgrade Your Prep!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        "Unlock AI Features & Infinite Tests",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Choose Your Plan",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Plan Cards
            SubscriptionCard(
                title = "Basic (Free)",
                price = "₹0",
                duration = "Forever",
                features = listOf("Basic Mock Tests", "Ads Enabled", "Community Access"),
                isSelected = selectedPlan == 0,
                onSelect = { selectedPlan = 0 },
                color = Color.Gray
            )
            
            SubscriptionCard(
                title = "Pro",
                price = "₹299",
                duration = "/ month",
                features = listOf("Unlimited AI Mock Tests", "No Ads", stringRes("Detailed Analytics"), "Priority Support"),
                isSelected = selectedPlan == 1,
                onSelect = { selectedPlan = 1 },
                color = MaterialTheme.colorScheme.primary,
                isBestValue = true
            )
            
            SubscriptionCard(
                title = "Elite",
                price = "₹2499",
                duration = "/ year",
                features = listOf("All Pro Features", "1-on-1 Mentorship Session", "Printed Study Materials", "Guaranteed Selection Plan"),
                isSelected = selectedPlan == 2,
                onSelect = { selectedPlan = 2 },
                color = Color(0xFF9C27B0)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val context = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = { 
                    android.widget.Toast.makeText(context, "Redirecting to Payment Gateway...", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Proceed to Pay", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SubscriptionCard(
    title: String,
    price: String,
    duration: String,
    features: List<String>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    color: Color,
    isBestValue: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
                if (isBestValue) {
                    Box(
                        modifier = Modifier
                            .background(color, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("BEST VALUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(duration, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(feature, fontSize = 14.sp)
                }
            }
        }
    }
}
