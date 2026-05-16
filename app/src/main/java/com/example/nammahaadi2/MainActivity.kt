package com.example.nammahaadi2

import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.google.android.gms.location.*
import com.google.maps.android.PolyUtil
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.maps.android.compose.*

private fun seedDummyData(database: FirebaseDatabase) {
    val leaderboardRef = database.getReference("leaderboard")
    val alertsRef = database.getReference("location_alerts")
    
    val dummyUsers = listOf(
        Triple("Ramesh_K", 1240, 45),
        Triple("Suresh_M", 985, 32),
        Triple("Ganesh_B", 720, 28),
        Triple("Lakshmi_P", 595, 19),
        Triple("Anand_S", 460, 15),
        Triple("Vijay_R", 330, 12),
        Triple("Kavitha_L", 215, 8),
        Triple("Priya_D", 180, 22),
        Triple("Sunil_G", 150, 5)
    )

    dummyUsers.forEach { (name, score, warnings) ->
        val ref = leaderboardRef.child("dummy_$name")
        ref.child("score").setValue(score)
        ref.child("name").setValue(name)
        ref.child("warnings").setValue(warnings)
    }
    
    // Seed some system-level warnings for the community
    val systemWarnings = listOf(
        Triple(LatLng(12.9816, 77.5846), PathStatus.FLOODED, "Bridge under water"),
        Triple(LatLng(12.9616, 77.6046), PathStatus.MUDDY, "Narrow forest path slippery"),
        Triple(LatLng(12.9916, 77.5746), PathStatus.FLOODED, "Main road blocked by tree")
    )
    
    systemWarnings.forEachIndexed { index, (pos, status, desc) ->
        alertsRef.child("sys_$index").setValue(mapOf(
            "lat" to pos.latitude,
            "lng" to pos.longitude,
            "status" to status.name,
            "description" to desc
        ))
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase with the explicit database URL to avoid configuration errors
        database = FirebaseDatabase.getInstance("https://nammahaadi-dd116-default-rtdb.firebaseio.com/")
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val statusRef = database.getReference("app_status")
        statusRef.setValue("Online: Namma Haadi")
        
        seedDummyData(database)

        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
            
            var currentScreen by remember { mutableStateOf("splash") }
            var isUserLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            var userScore by remember { mutableStateOf(0) }

            // Global listener for score
            LaunchedEffect(isUserLoggedIn) {
                if (isUserLoggedIn) {
                    val userId = auth.currentUser?.uid ?: "anonymous"
                    database.getReference("leaderboard/$userId/score").addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            userScore = snapshot.getValue(Int::class.java) ?: 0
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                if (currentScreen in listOf("dashboard", "map", "leaderboard_full", "profile", "settings")) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier.width(300.dp),
                                drawerContainerColor = MaterialTheme.colorScheme.surface,
                                drawerTonalElevation = 4.dp
                            ) {
                                DrawerHeader(auth.currentUser?.email ?: "User")
                                Spacer(modifier = Modifier.height(12.dp))
                                NavigationDrawerItem(
                                    label = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                                    selected = currentScreen == "dashboard",
                                    onClick = { 
                                        currentScreen = "dashboard"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Map & Navigation", fontWeight = FontWeight.Bold) },
                                    selected = currentScreen == "map",
                                    onClick = { 
                                        currentScreen = "map"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.Place, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Leaderboard", fontWeight = FontWeight.Bold) },
                                    selected = currentScreen == "leaderboard_full",
                                    onClick = { 
                                        currentScreen = "leaderboard_full"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.Star, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("My Profile", fontWeight = FontWeight.Bold) },
                                    selected = currentScreen == "profile",
                                    onClick = { 
                                        currentScreen = "profile"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.Person, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Settings", fontWeight = FontWeight.Bold) },
                                    selected = currentScreen == "settings",
                                    onClick = { 
                                        currentScreen = "settings"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                NavigationDrawerItem(
                                    label = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                                    selected = false,
                                    onClick = { 
                                        auth.signOut()
                                        isUserLoggedIn = false
                                        currentScreen = "welcome"
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            when (currentScreen) {
                                "dashboard" -> DashboardScreen(
                                    database = database,
                                    auth = auth,
                                    isDarkMode = isDarkMode,
                                    onToggleTheme = { isDarkMode = !isDarkMode },
                                    onGoToMap = { currentScreen = "map" },
                                    onViewLeaderboard = { currentScreen = "leaderboard_full" },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                "map" -> NammaHaadiHomepage(
                                    database, auth, fusedLocationClient,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onBack = { currentScreen = "dashboard" }
                                )
                                "leaderboard_full" -> FullLeaderboardScreen(
                                    database, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onBack = { currentScreen = "dashboard" }
                                )
                                "profile" -> ProfileScreen(auth, userScore) { scope.launch { drawerState.open() } }
                                "settings" -> SettingsScreen(isDarkMode, { isDarkMode = it }) { scope.launch { drawerState.open() } }
                            }
                        }
                    }
                } else {
                    // Logic for screens outside the drawer (Welcome, Login, etc.)
                    when (currentScreen) {
                        "splash" -> SplashScreen { 
                            currentScreen = if (isUserLoggedIn) "dashboard" else "welcome" 
                        }
                        "welcome" -> WelcomeScreen { currentScreen = "login" }
                        "login" -> LoginPage(auth) { 
                            isUserLoggedIn = true
                            currentScreen = "dashboard" 
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerHeader(email: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp)
    ) {
        Column {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(email.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(email, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Community Explorer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun ProfileScreen(auth: FirebaseAuth, score: Int, onOpenDrawer: () -> Unit) {
    val email = auth.currentUser?.email ?: "User"
    val uid = auth.currentUser?.uid ?: "N/A"
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.primary)
            }
            Text("My Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(email.take(1).uppercase(), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(email, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("User ID", uid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "ID Copied to Clipboard", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text("ID: ${uid.take(10)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Edit, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileStatRow("Total Points", "$score")
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow("Shortcuts Shared", "${score / 10}")
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileStatRow("Status Updates", "${score % 10}")
            }
        }
    }
}

@Composable
fun ProfileStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(isDarkMode: Boolean, onToggleTheme: (Boolean) -> Unit, onOpenDrawer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.primary)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", fontWeight = FontWeight.Medium)
                    Switch(checked = isDarkMode, onCheckedChange = onToggleTheme)
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Notifications", fontWeight = FontWeight.Medium)
                Text("Receive alerts for nearby road updates", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Namma Haadi",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 32.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    database: FirebaseDatabase,
    auth: FirebaseAuth,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onGoToMap: () -> Unit,
    onViewLeaderboard: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var userScore by remember { mutableStateOf(0) }
    val userEmail = auth.currentUser?.email ?: "Explorer"
    
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: "anonymous"
        database.getReference("leaderboard/$userId/score").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userScore = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.CheckCircle else Icons.Default.Check, 
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text("Namaskara,", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Text(userEmail.split("@")[0], fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.height(32.dp))

            // Points Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Score", color = Color.White.copy(alpha = 0.8f))
                        Text("$userScore Points", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBC02D), modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Quick Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Open Map",
                    icon = Icons.Default.Place,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onGoToMap
                )
                DashboardActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Leaderboard",
                    icon = Icons.Default.List,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onViewLeaderboard
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Community Impact Section
            Text("Your Impact", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Shortcuts Mapped: ${userScore / 10}", fontWeight = FontWeight.Medium)
                    Text("Road Status Updates: ${userScore % 10}", fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onGoToMap,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Find a Shortcut", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DashboardActionCard(modifier: Modifier, title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLeaderboardScreen(database: FirebaseDatabase, onOpenDrawer: () -> Unit, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Top Pathfinders", "Safety Heroes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Hall of Fame", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                LeaderboardScreen(database, sortByWarnings = selectedTab == 1)
            }
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surface
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Surface(
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Namma Haadi",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Navigating Rural Karnataka Together",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                FeatureRow("📍", "Village Shortcuts", "Access hidden paths mapped by locals.")
                Spacer(Modifier.height(16.dp))
                FeatureRow("🌤️", "Real-time Conditions", "Check if roads are dry, muddy or flooded.")
                Spacer(Modifier.height(16.dp))
                FeatureRow("🎖️", "Community Hero", "Earn points for every update you provide.")
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text("Get Started", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            
            TextButton(onClick = onGetStarted) {
                Text("Already have an account? Sign In", color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureRow(emoji: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoginPage(auth: FirebaseAuth, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = if (isSignUp) "Create Account" else "Welcome Back",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Text(
                text = if (isSignUp) "Join the Namma Haadi community" else "Sign in to continue mapping",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(
                            onClick = {
                                val cleanEmail = email.trim()
                                val cleanPassword = password.trim()
                                
                                if (cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) {
                                    isLoading = true
                                    if (isSignUp) {
                                        auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                                            .addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) onLoginSuccess()
                                                else Toast.makeText(context, task.exception?.message ?: "Sign up failed", Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                                            .addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) onLoginSuccess()
                                                else Toast.makeText(context, "Invalid credentials", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                } else {
                                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isSignUp) "Register" else "Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { isSignUp = !isSignUp }) {
                        Text(
                            text = if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

enum class PathStatus(val label: String, val color: Color, val icon: String) {
    DRY("Dry", Color(0xFF2E7D32), "✅"),
    MUDDY("Muddy", Color(0xFFFBC02D), "🚜"),
    FLOODED("Flooded", Color.Red, "🌊")
}

data class RuralPath(
    val points: List<LatLng>,
    val status: PathStatus,
    val id: String
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NammaHaadiHomepage(
    database: FirebaseDatabase, 
    auth: FirebaseAuth, 
    fusedLocationClient: FusedLocationProviderClient,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentStatus by remember { mutableStateOf(PathStatus.DRY) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }
    
    // Route planning state
    var startLocation by remember { mutableStateOf<LatLng?>(null) }
    var destLocation by remember { mutableStateOf<LatLng?>(null) }
    var roadPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf("none") } // "start", "dest", "none"
    
    // Tracing state
    var isTracing by remember { mutableStateOf(false) }
    var tracedPoints by remember { mutableStateOf(mutableListOf<LatLng>()) }
    var savedPaths by remember { mutableStateOf(listOf<RuralPath>()) }
    var locationAlerts by remember { mutableStateOf(listOf<Triple<LatLng, PathStatus, String>>()) }

    var locationPermissionGranted by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                      permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        locationPermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "Location permission required for tracing", Toast.LENGTH_SHORT).show()
        }
    }

    var userScore by remember { mutableStateOf(0) }
    
    // Sync status, paths and user score from Firebase
    DisposableEffect(Unit) {
        val userId = auth.currentUser?.uid ?: "anonymous"
        val scoreRef = database.getReference("leaderboard/$userId/score")
        val statusRef = database.getReference("village_path_status")
        val pathsRef = database.getReference("rural_paths")
        val alertsRef = database.getReference("location_alerts")

        val scoreListener = scoreRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userScore = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val statusListener = statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusName = snapshot.getValue(String::class.java) ?: "DRY"
                currentStatus = try { PathStatus.valueOf(statusName) } catch (e: Exception) { PathStatus.DRY }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val pathsListener = pathsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newPaths = mutableListOf<RuralPath>()
                snapshot.children.forEach { pathSnap ->
                    val points = mutableListOf<LatLng>()
                    pathSnap.child("points").children.forEach { pt ->
                        val lat = pt.child("lat").getValue(Double::class.java) ?: 0.0
                        val lng = pt.child("lng").getValue(Double::class.java) ?: 0.0
                        points.add(LatLng(lat, lng))
                    }
                    val statusName = pathSnap.child("status").getValue(String::class.java) ?: "DRY"
                    val status = try { PathStatus.valueOf(statusName) } catch (e: Exception) { PathStatus.DRY }
                    if (points.isNotEmpty()) newPaths.add(RuralPath(points, status, pathSnap.key ?: ""))
                }
                savedPaths = newPaths
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val alertsListener = alertsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newAlerts = mutableListOf<Triple<LatLng, PathStatus, String>>()
                snapshot.children.forEach { alertSnap ->
                    val lat = alertSnap.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = alertSnap.child("lng").getValue(Double::class.java) ?: 0.0
                    val statusName = alertSnap.child("status").getValue(String::class.java) ?: "DRY"
                    val status = try { PathStatus.valueOf(statusName) } catch (e: Exception) { PathStatus.DRY }
                    newAlerts.add(Triple(LatLng(lat, lng), status, alertSnap.key ?: ""))
                }
                locationAlerts = newAlerts
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Request location permission
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        onDispose {
            scoreRef.removeEventListener(scoreListener)
            statusRef.removeEventListener(statusListener)
            pathsRef.removeEventListener(pathsListener)
            alertsRef.removeEventListener(alertsListener)
        }
    }

    // Location updates for tracing
    DisposableEffect(isTracing) {
        var callback: LocationCallback? = null
        if (isTracing) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        val newPoint = LatLng(it.latitude, it.longitude)
                        tracedPoints = (tracedPoints + newPoint).toMutableList()
                    }
                }
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            } else {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                isTracing = false
            }
        }

        onDispose {
            callback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.9716, 77.5946), 15f)
    }

    // Move camera to user location on start or when permission is granted
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(location.latitude, location.longitude), 15f
                        )
                    } else {
                        // If lastLocation is null, request a single update
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { freshLoc ->
                                freshLoc?.let {
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                        LatLng(it.latitude, it.longitude), 15f
                                    )
                                }
                            }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("Location", "Permission denied even after grant")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Namma Haadi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("$userScore Points", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLeaderboard = true }) {
                        Icon(Icons.Default.Star, "Leaderboard", tint = Color(0xFFFBC02D), modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (isTracing) {
                    ExtendedFloatingActionButton(
                        onClick = { 
                            isTracing = false
                            tracedPoints = mutableListOf()
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                        icon = { Icon(Icons.Default.Close, null) },
                        text = { Text("Cancel") }
                    )
                }
                
                FloatingActionButton(
                    onClick = { 
                        if (isTracing) {
                            // Save path to Firebase
                            if (tracedPoints.isNotEmpty()) {
                                val pathId = database.getReference("rural_paths").push().key ?: ""
                                val pointsMap = tracedPoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }
                                database.getReference("rural_paths/$pathId/points").setValue(pointsMap)
                                database.getReference("rural_paths/$pathId/creator").setValue(auth.currentUser?.uid)
                                database.getReference("rural_paths/$pathId/status").setValue(currentStatus.name)
                                
                                // Reward points
                                val userId = auth.currentUser?.uid ?: "anonymous"
                                database.getReference("leaderboard/$userId/score").runTransaction(object : Transaction.Handler {
                                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                                        val currentScore = mutableData.getValue(Int::class.java) ?: 0
                                        mutableData.value = currentScore + 10
                                        return Transaction.success(mutableData)
                                    }
                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                                })
                                
                                Toast.makeText(context, "Shortcut Saved! +10 pts", Toast.LENGTH_SHORT).show()
                            }
                            isTracing = false
                            tracedPoints = mutableListOf()
                        } else {
                            tracedPoints = mutableListOf()
                            isTracing = true
                        }
                    },
                    containerColor = if (isTracing) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isTracing) Icons.Default.Check else Icons.Default.Add, null)
                        if (isTracing) {
                            Spacer(Modifier.width(8.dp))
                            Text("Finish Path")
                        }
                    }
                }
                
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet = true },
                    icon = { Icon(Icons.Default.Edit, null) },
                    text = { Text("Update My Location") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = locationPermissionGranted
                ),
                onMapClick = { latLng ->
                    if (selectionMode == "start") {
                        startLocation = latLng
                        selectionMode = "none"
                    } else if (selectionMode == "dest") {
                        destLocation = latLng
                        selectionMode = "none"
                    }
                },
                uiSettings = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false)
            ) {
                // Show Start and Destination Markers
                startLocation?.let {
                    Marker(
                        state = MarkerState(it),
                        title = "Start Point",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
                destLocation?.let {
                    Marker(
                        state = MarkerState(it),
                        title = "Destination",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
                    )
                }

                // Show calculated shortest road path
                if (roadPoints.isNotEmpty()) {
                    Polyline(
                        points = roadPoints,
                        color = Color(0xFF1976D2),
                        width = 12f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap()
                    )
                } else if (startLocation != null && destLocation != null) {
                    // Draw fallback direct line if road route isn't calculated yet
                    Polyline(
                        points = listOf(startLocation!!, destLocation!!),
                        color = Color(0xFF673AB7).copy(alpha = 0.5f),
                        width = 8f,
                        pattern = listOf(Dash(20f), Gap(10f))
                    )
                }

                // Show saved paths with their specific colors and distinct flooded style
                savedPaths.forEach { path ->
                    Polyline(
                        points = path.points,
                        color = path.status.color,
                        width = 16f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 10f,
                        pattern = if (path.status == PathStatus.FLOODED) {
                            listOf(Dash(40f), Gap(20f)) 
                        } else null
                    )
                }

                // Show location-specific alerts (Muddy/Flooded spots)
                locationAlerts.forEach { (latLng, status, id) ->
                    Marker(
                        state = MarkerState(latLng),
                        title = "${status.label} Spot",
                        snippet = "Community Alert",
                        zIndex = 20f,
                        icon = BitmapDescriptorFactory.defaultMarker(
                            when(status) {
                                PathStatus.DRY -> BitmapDescriptorFactory.HUE_GREEN
                                PathStatus.MUDDY -> BitmapDescriptorFactory.HUE_YELLOW
                                else -> BitmapDescriptorFactory.HUE_RED
                            }
                        )
                    )
                }

                // Show currently tracing path with high visibility
                if (tracedPoints.isNotEmpty()) {
                    Polyline(
                        points = tracedPoints,
                        color = Color(0xFF2196F3), // Vibrant Blue for tracing
                        width = 20f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 30f
                    )
                }
            }

            // Route Selection UI
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Plan Shortcut Route", fontWeight = FontWeight.Bold)
                        if (startLocation != null || destLocation != null) {
                            TextButton(onClick = {
                                startLocation = null
                                destLocation = null
                                roadPoints = emptyList()
                                selectionMode = "none"
                            }) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectionMode = "start" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectionMode == "start") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectionMode == "start") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (startLocation == null) "Set Start" else "Start Set", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { selectionMode = "dest" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectionMode == "dest") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectionMode == "dest") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (destLocation == null) "Set Goal" else "Goal Set", fontSize = 12.sp)
                        }
                    }
                    if (selectionMode != "none") {
                        Text(
                            "Tap on the map to set ${if (selectionMode == "start") "starting point" else "destination"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (startLocation != null && destLocation != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isCalculatingRoute = true
                                kotlinx.coroutines.MainScope().launch {
                                    val route = fetchShortestRoad(startLocation!!, destLocation!!)
                                    if (route.isNotEmpty()) {
                                        roadPoints = route
                                        Toast.makeText(context, "Shortest road found!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Road data unavailable here.", Toast.LENGTH_SHORT).show()
                                    }
                                    isCalculatingRoute = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCalculatingRoute,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            if (isCalculatingRoute) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Calculating...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Find Shortest Road Path", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Map Type Toggle Button
            Surface(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .clickable { mapType = if (mapType == MapType.HYBRID) MapType.TERRAIN else MapType.HYBRID },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Change Map Type",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (isTracing) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Recording Path...", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                var description by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Update Road Condition", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Help neighbors by reporting the current status.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("What's happening? (e.g. Floods, Raining)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional description...") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    PathStatus.values().forEach { status ->
                        Button(
                            onClick = {
                                // 1. Update Global Status
                                database.getReference("village_path_status").setValue(status.name)
                                
                                // 2. Drop a local marker at current location
                                val userLoc = cameraPositionState.position.target 
                                val alertId = database.getReference("location_alerts").push().key ?: ""
                                database.getReference("location_alerts/$alertId").setValue(mapOf(
                                    "lat" to userLoc.latitude,
                                    "lng" to userLoc.longitude,
                                    "status" to status.name,
                                    "description" to description.trim(),
                                    "reporter" to (auth.currentUser?.email ?: "Community")
                                ))

                                val userId = auth.currentUser?.uid ?: "anonymous"
                                database.getReference("leaderboard/$userId").runTransaction(object : Transaction.Handler {
                                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                                        val score = mutableData.child("score").getValue(Int::class.java) ?: 0
                                        val warnings = mutableData.child("warnings").getValue(Int::class.java) ?: 0
                                        
                                        mutableData.child("score").value = score + 5 // +5 for alerts now!
                                        mutableData.child("warnings").value = warnings + 1
                                        mutableData.child("name").value = auth.currentUser?.email?.split("@")?.get(0) ?: "Explorer"
                                        
                                        return Transaction.success(mutableData)
                                    }
                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                                })
                                showBottomSheet = false
                                Toast.makeText(context, "Community alert shared! +5 pts", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = status.color),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(status.icon, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Text("Mark as ${status.label}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }

        if (showLeaderboard) {
            ModalBottomSheet(
                onDismissRequest = { showLeaderboard = false },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                LeaderboardScreen(database)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(database: FirebaseDatabase, sortByWarnings: Boolean = false) {
    var contributors by remember { mutableStateOf(listOf<ContributorData>()) }
    
    LaunchedEffect(sortByWarnings) {
        val ref = database.getReference("leaderboard")
        val query = if (sortByWarnings) ref.orderByChild("warnings") else ref.orderByChild("score")
        
        query.limitToLast(20).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ContributorData>()
                snapshot.children.forEach {
                    val id = it.key ?: ""
                    val name = it.child("name").getValue(String::class.java) ?: "Explorer ${id.take(4)}"
                    val score = it.child("score").getValue(Int::class.java) ?: 0
                    val warnings = it.child("warnings").getValue(Int::class.java) ?: 0
                    list.add(ContributorData(id, name, score, warnings))
                }
                contributors = if (sortByWarnings) {
                    list.sortedByDescending { it.warnings }
                } else {
                    list.sortedByDescending { it.score }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
        val title = if (sortByWarnings) "Top Safety Reporters 🛡️" else "Community Legends 🏆"
        val subtitle = if (sortByWarnings) "Most hazards reported this week." else "Top pathfinders and map builders."
        
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(24.dp))
        
        LazyColumn {
            itemsIndexed(contributors) { index, data ->
                LeaderboardItem(index, data, highlightWarnings = sortByWarnings)
            }
        }
    }
}

data class ContributorData(val id: String, val name: String, val score: Int, val warnings: Int)

@Composable
fun LeaderboardItem(index: Int, data: ContributorData, highlightWarnings: Boolean = false) {
    val medal = when(index) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        else -> "${index + 1}."
    }
    
    val bgColor = if (index < 3) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(medal, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(data.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place, null, 
                            modifier = Modifier.size(12.dp), 
                            tint = if (!highlightWarnings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Text("${data.score / 10} Paths", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Warning, null, 
                            modifier = Modifier.size(12.dp), 
                            tint = if (highlightWarnings) Color.Red else Color.Red.copy(alpha = 0.5f)
                        )
                        Text("${data.warnings} Alerts", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (highlightWarnings) "${data.warnings}" else "${data.score}", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = if (highlightWarnings) Color.Red else MaterialTheme.colorScheme.primary, 
                    fontSize = 18.sp
                )
                Text(if (highlightWarnings) "alerts" else "pts", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

suspend fun fetchShortestRoad(start: LatLng, dest: LatLng): List<LatLng> {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://router.project-osrm.org/route/v1/driving/" +
                    "${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}" +
                    "?overview=full&geometries=polyline"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val routes = json.getJSONArray("routes")
            
            if (routes.length() > 0) {
                val geometry = routes.getJSONObject(0).getString("geometry")
                return@withContext PolyUtil.decode(geometry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }
}
