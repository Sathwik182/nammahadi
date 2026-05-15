package com.example.nammahaadi2

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
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.maps.android.compose.*

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

        setContent {
            var currentScreen by remember { mutableStateOf(if (auth.currentUser == null) "welcome" else "home") }

            MaterialTheme {
                when (currentScreen) {
                    "welcome" -> WelcomeScreen { currentScreen = "login" }
                    "login" -> LoginPage(auth) { currentScreen = "home" }
                    "home" -> NammaHaadiHomepage(database, auth, fusedLocationClient) { currentScreen = "login" }
                }
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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Namma Haadi",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Community Shortcut Guide",
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val cleanEmail = email.trim()
                    val cleanPassword = password.trim()
                    
                    if (cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) {
                        isLoading = true
                        auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    isLoading = false
                                    onLoginSuccess()
                                } else {
                                    val signInError = task.exception?.message ?: "Unknown error"
                                    Log.w("Auth", "SignIn failed: $signInError. Attempting registration...")

                                    // If login fails, try to register
                                    auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                                        .addOnCompleteListener { regTask ->
                                            isLoading = false
                                            if (regTask.isSuccessful) {
                                                onLoginSuccess()
                                            } else {
                                                val errorMsg = regTask.exception?.message ?: "Unknown error"
                                                val errorCode = (regTask.exception as? FirebaseAuthException)?.errorCode ?: "No code"
                                                Log.e("AuthError", "Code: $errorCode, Message: $errorMsg")
                                                Toast.makeText(context, "Auth failed: $errorMsg (Code: $errorCode)", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }
                            }
                    } else {
                        Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login / Sign Up")
            }
        }
    }
}

enum class PathStatus(val label: String, val color: Color, val icon: String) {
    DRY("Dry", Color(0xFF2E7D32), "✅"),
    MUDDY("Muddy", Color(0xFFFBC02D), "🚜"),
    FLOODED("Flooded", Color.Red, "🌊")
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NammaHaadiHomepage(
    database: FirebaseDatabase, 
    auth: FirebaseAuth, 
    fusedLocationClient: FusedLocationProviderClient,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentStatus by remember { mutableStateOf(PathStatus.DRY) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }
    
    // Tracing state
    var isTracing by remember { mutableStateOf(false) }
    var tracedPoints by remember { mutableStateOf(mutableListOf<LatLng>()) }
    var savedPaths by remember { mutableStateOf(listOf<Triple<List<LatLng>, PathStatus, String>>()) }
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
    LaunchedEffect(Unit) {
        // Request location permission on start
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        val userId = auth.currentUser?.uid ?: "anonymous"
        database.getReference("leaderboard/$userId/score")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userScore = snapshot.getValue(Int::class.java) ?: 0
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        val pathStatusRef = database.getReference("village_path_status")
        pathStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusName = snapshot.getValue(String::class.java) ?: "DRY"
                currentStatus = try { PathStatus.valueOf(statusName) } catch (e: Exception) { PathStatus.DRY }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val pathsRef = database.getReference("rural_paths")
        pathsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newPaths = mutableListOf<Triple<List<LatLng>, PathStatus, String>>()
                snapshot.children.forEach { pathSnap ->
                    val points = mutableListOf<LatLng>()
                    pathSnap.child("points").children.forEach { pt ->
                        val lat = pt.child("lat").getValue(Double::class.java) ?: 0.0
                        val lng = pt.child("lng").getValue(Double::class.java) ?: 0.0
                        points.add(LatLng(lat, lng))
                    }
                    val statusName = pathSnap.child("status").getValue(String::class.java) ?: "DRY"
                    val status = try { PathStatus.valueOf(statusName) } catch (e: Exception) { PathStatus.DRY }
                    val id = pathSnap.key ?: ""
                    if (points.isNotEmpty()) newPaths.add(Triple(points, status, id))
                }
                savedPaths = newPaths
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        val alertsRef = database.getReference("location_alerts")
        alertsRef.addValueEventListener(object : ValueEventListener {
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
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(auth.currentUser?.email?.take(1)?.uppercase() ?: "U", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
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
                    IconButton(onClick = { 
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
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
                uiSettings = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false)
            ) {
                // Show saved paths with their specific colors
                savedPaths.forEach { (points, status, id) ->
                    Polyline(
                        points = points,
                        color = status.color,
                        width = 14f,
                        jointType = JointType.ROUND,
                        pattern = if (status == PathStatus.FLOODED) listOf(Dash(30f), Gap(20f)) else null
                    )
                }

                // Show location-specific alerts (Muddy/Flooded spots)
                locationAlerts.forEach { (latLng, status, id) ->
                    Marker(
                        state = MarkerState(latLng),
                        title = "${status.label} Spot",
                        snippet = "Reported by community",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            when(status) {
                                PathStatus.DRY -> BitmapDescriptorFactory.HUE_GREEN
                                PathStatus.MUDDY -> BitmapDescriptorFactory.HUE_YELLOW
                                else -> BitmapDescriptorFactory.HUE_RED
                            }
                        )
                    )
                }

                // Show currently tracing path
                if (tracedPoints.isNotEmpty()) {
                    Polyline(
                        points = tracedPoints,
                        color = MaterialTheme.colorScheme.primary,
                        width = 16f,
                        jointType = JointType.ROUND
                    )
                }
            }

            // Map Type Toggle Button
            SmallFloatingActionButton(
                onClick = { 
                    mapType = if (mapType == MapType.HYBRID) MapType.TERRAIN else MapType.HYBRID 
                },
                modifier = Modifier
                    .padding(top = 80.dp, end = 16.dp)
                    .align(Alignment.TopEnd),
                containerColor = Color.White.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (mapType == MapType.HYBRID) Icons.Default.Menu else Icons.Default.Share, // Menu as "layers" icon fallback
                    contentDescription = "Change Map Type"
                )
            }
            
            if (isTracing) {
                Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text("📍 Recording Path...", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("Update Road Condition", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Help neighbors by reporting the current status.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    PathStatus.values().forEach { status ->
                        Button(
                            onClick = {
                                // 1. Update Global Status
                                database.getReference("village_path_status").setValue(status.name)
                                
                                // 2. Drop a local marker at current location
                                val userLoc = cameraPositionState.position.target // Use center of map as current loc
                                val alertId = database.getReference("location_alerts").push().key ?: ""
                                database.getReference("location_alerts/$alertId").setValue(mapOf(
                                    "lat" to userLoc.latitude,
                                    "lng" to userLoc.longitude,
                                    "status" to status.name
                                ))

                                val userId = auth.currentUser?.uid ?: "anonymous"
                                database.getReference("leaderboard/$userId/score").runTransaction(object : Transaction.Handler {
                                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                                        val currentScore = mutableData.getValue(Int::class.java) ?: 0
                                        mutableData.value = currentScore + 1
                                        return Transaction.success(mutableData)
                                    }
                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                                })
                                showBottomSheet = false
                                Toast.makeText(context, "Location updated for everyone!", Toast.LENGTH_SHORT).show()
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
fun LeaderboardScreen(database: FirebaseDatabase) {
    var contributors by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    
    LaunchedEffect(Unit) {
        database.getReference("leaderboard").orderByChild("score").limitToLast(10)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Pair<String, Int>>()
                    snapshot.children.forEach {
                        val name = it.key?.take(8) ?: "User"
                        val score = it.child("score").getValue(Int::class.java) ?: 0
                        list.add(name to score)
                    }
                    contributors = list.sortedByDescending { it.second }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.8f)) {
        Text("Top Contributors 🏆", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text("Our local heroes mapping the way.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(24.dp))
        
        LazyColumn {
            itemsIndexed(contributors) { index, (name, score) ->
                LeaderboardItem(index, name, score)
            }
        }
    }
}

@Composable
fun LeaderboardItem(index: Int, name: String, score: Int) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(medal, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Haadi Explorer $name", fontWeight = FontWeight.Bold)
                    Text(if (score > 50) "Path Legend" else "Contributor", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text("$score pts", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
