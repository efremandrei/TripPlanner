package com.tripplanner.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.tripplanner.app.data.auth.AuthRepository
import com.tripplanner.app.data.auth.AuthProvider
import com.tripplanner.app.data.auth.AuthSession
import com.tripplanner.app.data.backup.TripBackupRepository
import com.tripplanner.app.data.google.GooglePlaceDetails
import com.tripplanner.app.data.google.GooglePlacesRepository
import com.tripplanner.app.data.TripRepository
import com.tripplanner.app.data.local.entity.PoolItemEntity
import com.tripplanner.app.data.local.entity.TripEntity
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectAttributeInputKind
import com.tripplanner.app.model.TripObjectDraft
import com.tripplanner.app.model.TripObjectType
import com.tripplanner.app.ui.map.TripMapView
import com.tripplanner.app.util.GoogleMapsIntents
import com.tripplanner.app.util.isOnline
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TripPlannerApp()
        }
    }
}

private enum class Screen {
    MainMenu,
    TripManagement,
    UseExistingTrip,
    PlanNewTrip
}

private enum class AppSkin(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

private enum class AuthMode {
    LocalLogin,
    CreateAccount
}

@Composable
private fun TripPlannerApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.MainMenu) }
    var appSkin by rememberSaveable { mutableStateOf(AppSkin.System) }
    var editingTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val authRepository = remember(context) { AuthRepository(context.applicationContext) }
    val database = (context.applicationContext as TripPlannerApplication).database
    val startupBackupRepository = remember(database) {
        TripBackupRepository(
            context = context.applicationContext,
            database = database
        )
    }
    var authSession by remember { mutableStateOf(authRepository.currentSession()) }

    fun returnToMainPage() {
        editingTripId = null
        screen = Screen.MainMenu
    }

    fun signOut() {
        authRepository.signOut()
        authSession = null
        returnToMainPage()
    }

    BackHandler {
        if (authSession == null || screen == Screen.MainMenu) {
            showExitConfirmation = true
        } else {
            returnToMainPage()
        }
    }

    LaunchedEffect(startupBackupRepository) {
        if (BuildConfig.PREPOPULATE_MOCK_DB) {
            runCatching {
                startupBackupRepository.populateMockDatabaseIfEmpty()
            }
        }
    }

    TripPlannerTheme(appSkin = appSkin) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val session = authSession
            if (session == null) {
                AuthScreen(
                    appSkin = appSkin,
                    onAppSkinChange = { appSkin = it },
                    onGoogleSignIn = {
                        "Google sign-in needs OAuth client configuration"
                    },
                    onLocalSignIn = { accountName, password ->
                        authRepository.signInLocal(
                            accountName = accountName,
                            password = password
                        ).also { result ->
                            result.session?.let { authSession = it }
                        }.errorMessage
                    },
                    onCreateAccount = { accountName, password ->
                        authRepository.createLocalAccount(
                            accountName = accountName,
                            password = password
                        ).also { result ->
                            result.session?.let { authSession = it }
                        }.errorMessage
                    }
                )
            } else {
                when (screen) {
                    Screen.MainMenu -> MainMenuScreen(
                        authSession = session,
                        appSkin = appSkin,
                        onAppSkinChange = { appSkin = it },
                        onSignOut = ::signOut,
                        onManageTrips = { screen = Screen.TripManagement },
                        onUseExistingTrip = { screen = Screen.UseExistingTrip }
                    )

                    Screen.TripManagement -> TripManagementScreen(
                        authSession = session,
                        appSkin = appSkin,
                        onAppSkinChange = { appSkin = it },
                        onSignOut = ::signOut,
                        onBack = { screen = Screen.MainMenu },
                        onNewTrip = {
                            editingTripId = null
                            screen = Screen.PlanNewTrip
                        },
                        onEditTrip = { tripId ->
                            editingTripId = tripId
                            screen = Screen.PlanNewTrip
                        }
                    )

                    Screen.UseExistingTrip -> UseExistingTripScreen(
                        authSession = session,
                        appSkin = appSkin,
                        onAppSkinChange = { appSkin = it },
                        onSignOut = ::signOut,
                        onBack = { screen = Screen.MainMenu }
                    )

                    Screen.PlanNewTrip -> PlanNewTripScreen(
                        appSkin = appSkin,
                        onAppSkinChange = { appSkin = it },
                        editingTripId = editingTripId,
                        onTripSaved = { tripId -> editingTripId = tripId },
                        onBack = { returnToMainPage() }
                    )
                }
            }
            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = { Text("Exit Trip Planner?") },
                    text = { Text("Do you want to close the application?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitConfirmation = false
                                activity?.finish()
                            }
                        ) {
                            Text("Exit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Stay")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    authSession: AuthSession,
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onSignOut: () -> Unit,
    onManageTrips: () -> Unit,
    onUseExistingTrip: () -> Unit
) {
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    val backupRepository = remember(database) {
        TripBackupRepository(
            context = context.applicationContext,
            database = database
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var databaseStatus by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeaderBar(
                displayName = authSession.displayName,
                onSignOut = onSignOut
            )
            TripHeroPanel(
                title = "Trip Planner",
                subtitle = "Build a clear itinerary, bookings list, and map plan",
                status = "Local first"
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            Text(
                text = "Choose how to continue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HomeActionCard(
                title = "Add new/edit existing trip",
                subtitle = "Create a new trip or update a saved local trip",
                accent = MaterialTheme.colorScheme.primary,
                onClick = onManageTrips
            )
            HomeActionCard(
                title = "Use existing trip",
                subtitle = "Open a saved plan for itinerary, bookings, and maps",
                accent = Color(0xFF4F7CAC),
                onClick = onUseExistingTrip
            )
            DatabaseToolsPanel(
                status = databaseStatus,
                exportDirectory = backupRepository.exportDirectoryPath(),
                onPopulateMockDb = {
                    coroutineScope.launch {
                        databaseStatus = "Populating mock database"
                        databaseStatus = runCatching {
                            backupRepository.populateMockDatabase().message
                        }.getOrElse { error ->
                            error.message ?: "Mock database could not be populated"
                        }
                    }
                },
                onClearMockedData = {
                    coroutineScope.launch {
                        databaseStatus = "Clearing mocked data"
                        databaseStatus = runCatching {
                            backupRepository.clearMockedData().message
                        }.getOrElse { error ->
                            error.message ?: "Mocked data could not be cleared"
                        }
                    }
                },
                onExportWholeDb = {
                    coroutineScope.launch {
                        databaseStatus = "Exporting whole database"
                        databaseStatus = runCatching {
                            backupRepository.exportWholeDatabase().message
                        }.getOrElse { error ->
                            error.message ?: "Whole database could not be exported"
                        }
                    }
                },
                onImportWholeDb = {
                    coroutineScope.launch {
                        databaseStatus = "Importing latest whole database export"
                        databaseStatus = runCatching {
                            backupRepository.importLatestWholeDatabase().message
                        }.getOrElse { error ->
                            error.message ?: "Whole database could not be imported"
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthScreen(
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onGoogleSignIn: () -> String?,
    onLocalSignIn: (String, String) -> String?,
    onCreateAccount: (String, String) -> String?
) {
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LocalLogin) }
    var accountName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var authStatus by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeaderBar()
            TripHeroPanel(
                title = "Trip Planner",
                subtitle = "Choose an account before opening your trips",
                status = "Account"
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            HomeActionCard(
                title = "Log in with Google account",
                subtitle = "OAuth setup required before live Google sign-in",
                accent = Color(0xFF4F7CAC),
                onClick = {
                    authStatus = onGoogleSignIn() ?: "Google account signed in"
                }
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = authMode == AuthMode.LocalLogin,
                    onClick = {
                        authMode = AuthMode.LocalLogin
                        authStatus = null
                    },
                    label = { Text("Local account") },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = authMode == AuthMode.CreateAccount,
                    onClick = {
                        authMode = AuthMode.CreateAccount
                        authStatus = null
                    },
                    label = { Text("Create account") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (authMode == AuthMode.LocalLogin) {
                            "Local account"
                        } else {
                            "Create account"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        onClick = {
                            authStatus = if (authMode == AuthMode.LocalLogin) {
                                onLocalSignIn(accountName, password)
                            } else {
                                onCreateAccount(accountName, password)
                            } ?: if (authMode == AuthMode.LocalLogin) {
                                "Signed in"
                            } else {
                                "Account created"
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (authMode == AuthMode.LocalLogin) "Log in" else "Create account")
                    }
                }
            }
            authStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DatabaseToolsPanel(
    status: String?,
    exportDirectory: String,
    onPopulateMockDb: () -> Unit,
    onClearMockedData: () -> Unit,
    onExportWholeDb: () -> Unit,
    onImportWholeDb: () -> Unit
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear mocked data?") },
            text = { Text("This removes local trips, pools, map presets, and cached place details. Local accounts and exported files stay untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearMockedData()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Database tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                onClick = onPopulateMockDb,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Populate mock DB")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = { showClearConfirmation = true },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear mocked data")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onExportWholeDb,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Export whole DB")
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onImportWholeDb,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Import latest whole DB")
            }
            Text(
                text = "Files: $exportDirectory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.let { currentStatus ->
                Text(
                    text = currentStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripManagementScreen(
    authSession: AuthSession,
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onNewTrip: () -> Unit,
    onEditTrip: (Long) -> Unit
) {
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    val trips by database.tripDao().observeActiveTrips().collectAsState(initial = emptyList())
    val backupRepository = remember(database) {
        TripBackupRepository(
            context = context.applicationContext,
            database = database
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var tripBackupStatus by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add new/edit existing trip") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeaderBar(
                displayName = authSession.displayName,
                onSignOut = onSignOut
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            HomeActionCard(
                title = "+ New Trip",
                subtitle = "Start a new local trip plan",
                accent = MaterialTheme.colorScheme.primary,
                onClick = onNewTrip
            )
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    coroutineScope.launch {
                        tripBackupStatus = "Importing latest trip export"
                        tripBackupStatus = runCatching {
                            backupRepository.importLatestTripExport().message
                        }.getOrElse { error ->
                            error.message ?: "Trip export could not be imported"
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Import latest trip export")
            }
            tripBackupStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Existing trips",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (trips.isEmpty()) {
                EmptyTripsCard(text = "No existing trips yet")
            } else {
                trips.forEach { trip ->
                    SavedTripCard(
                        trip = trip,
                        actionLabel = "Edit",
                        secondaryActionLabel = "Export",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { onEditTrip(trip.id) },
                        onSecondaryClick = {
                            coroutineScope.launch {
                                tripBackupStatus = "Exporting ${trip.title}"
                                tripBackupStatus = runCatching {
                                    backupRepository.exportTrip(trip.id).message
                                }.getOrElse { error ->
                                    error.message ?: "Trip could not be exported"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UseExistingTripScreen(
    authSession: AuthSession,
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    val tripRepository = remember(database) { TripRepository(database) }
    val trips by database.tripDao().observeActiveTrips().collectAsState(initial = emptyList())
    var selectedTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var useStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTripObjects = remember { mutableStateListOf<TripObjectDraft>() }
    val selectedTrip = trips.firstOrNull { it.id == selectedTripId }

    LaunchedEffect(selectedTripId, tripRepository) {
        val tripId = selectedTripId
        if (tripId == null) {
            selectedTripObjects.clear()
            useStatus = null
            return@LaunchedEffect
        }

        useStatus = "Loading trip"
        runCatching {
            tripRepository.getEditableTrip(tripId)
        }.onSuccess { editableTrip ->
            selectedTripObjects.clear()
            selectedTripObjects.addAll(editableTrip?.objects.orEmpty())
            useStatus = if (editableTrip == null) {
                "Trip #$tripId was not found"
            } else {
                "${editableTrip.objects.size} trip items loaded"
            }
        }.onFailure { error ->
            selectedTripObjects.clear()
            useStatus = error.message ?: "Trip could not be loaded"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Use existing trip") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeaderBar(
                displayName = authSession.displayName,
                onSignOut = onSignOut
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            if (selectedTrip != null) {
                TripHeroPanel(
                    title = selectedTrip.title,
                    subtitle = tripDateRange(selectedTrip),
                    status = "Selected"
                )
                TripMapView(tripObjects = selectedTripObjects)
                BookingsPanel(tripObjects = selectedTripObjects)
                PrivateMapPresetPanel(
                    tripObjects = selectedTripObjects,
                    isSavedTrip = true,
                    onOpenGoogleMaps = {
                        val mapIntent = GoogleMapsIntents.openMapIntent(selectedTripObjects)
                        if (mapIntent == null) {
                            useStatus = "Add coordinates before opening Google Maps"
                        } else {
                            try {
                                context.startActivity(mapIntent)
                            } catch (_: ActivityNotFoundException) {
                                useStatus = "Google Maps app is not available"
                            }
                        }
                    }
                )
                if (selectedTripObjects.isNotEmpty()) {
                    TripItemsPanel(tripObjects = selectedTripObjects)
                }
                useStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "Existing trips",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (trips.isEmpty()) {
                EmptyTripsCard(text = "No saved trips to use yet")
            } else {
                trips.forEach { trip ->
                    SavedTripCard(
                        trip = trip,
                        actionLabel = if (trip.id == selectedTripId) "Using" else "Use",
                        accent = Color(0xFF4F7CAC),
                        onClick = { selectedTripId = trip.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedTripCard(
    trip: TripEntity,
    actionLabel: String,
    secondaryActionLabel: String? = null,
    accent: Color,
    onClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trip.title.firstOrNull()?.uppercase() ?: "T",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = trip.title.ifBlank { "Untitled trip" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tripDateRange(trip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(actionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTripsCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemPoolPanel(
    trips: List<TripEntity>,
    selectedTripPoolId: Long?,
    selectedTypeFilter: TripObjectType?,
    poolItems: List<PoolItemEntity>,
    totalItemCount: Int,
    currentTripObjectIds: Set<Long>,
    onSelectTripPool: (Long?) -> Unit,
    onSelectTypeFilter: (TripObjectType?) -> Unit,
    onUseItem: (PoolItemEntity) -> Unit
) {
    val selectedTrip = trips.firstOrNull { it.id == selectedTripPoolId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Item pool",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (selectedTrip == null) {
                    "General pool"
                } else {
                    "Trip pool: ${selectedTrip.title}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTripPoolId == null,
                    onClick = { onSelectTripPool(null) },
                    label = { Text("General") },
                    shape = RoundedCornerShape(8.dp)
                )
                trips.forEach { trip ->
                    FilterChip(
                        selected = selectedTripPoolId == trip.id,
                        onClick = { onSelectTripPool(trip.id) },
                        label = { Text(trip.title) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { onSelectTypeFilter(null) },
                    label = { Text("All") },
                    shape = RoundedCornerShape(8.dp)
                )
                TripObjectType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = { onSelectTypeFilter(type) },
                        label = { Text(type.displayName) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            if (poolItems.isEmpty()) {
                Text(
                    text = if (totalItemCount == 0) {
                        "No items in this pool yet"
                    } else {
                        "No items match this type"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                poolItems.forEach { poolItem ->
                    PoolItemRow(
                        poolItem = poolItem,
                        isAlreadyInTrip = poolItem.id in currentTripObjectIds,
                        onUseItem = { onUseItem(poolItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PoolItemRow(
    poolItem: PoolItemEntity,
    isAlreadyInTrip: Boolean,
    onUseItem: () -> Unit
) {
    val accent = accentForTripObjectType(poolItem.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = poolItem.type.displayName.first().uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = poolItem.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = poolItem.type.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onUseItem,
            enabled = !isAlreadyInTrip
        ) {
            Text(if (isAlreadyInTrip) "In trip" else "Use")
        }
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.first().uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryMenuButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryMenuButton(label: String) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = {},
        enabled = false,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AppHeaderBar(
    modifier: Modifier = Modifier,
    displayName: String = "Guest",
    onSignOut: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TP",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = "Trip Planner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0C4B4)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "G",
                    color = Color(0xFF4A2E24),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        onSignOut?.let { signOut ->
            TextButton(onClick = signOut) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun TripHeroPanel(
    title: String,
    subtitle: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        TripHeroVisual(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xAA06231F)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE6F4EF)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFD7FFE7))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF176B5B)
            )
        }
    }
}

@Composable
private fun TripHeroVisual(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFB07C),
                    Color(0xFF86C7D4),
                    Color(0xFF176B5B)
                )
            )
        )
        drawCircle(
            color = Color(0xFFFFE6A3),
            radius = size.minDimension * 0.11f,
            center = Offset(size.width * 0.78f, size.height * 0.27f)
        )

        val hill = Path().apply {
            moveTo(0f, size.height * 0.74f)
            cubicTo(
                size.width * 0.25f,
                size.height * 0.58f,
                size.width * 0.43f,
                size.height * 0.78f,
                size.width * 0.68f,
                size.height * 0.61f
            )
            cubicTo(
                size.width * 0.86f,
                size.height * 0.49f,
                size.width,
                size.height * 0.66f,
                size.width,
                size.height * 0.66f
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = hill, color = Color(0xFF0E4F43))

        val templeRoof = Path().apply {
            moveTo(size.width * 0.13f, size.height * 0.59f)
            lineTo(size.width * 0.35f, size.height * 0.38f)
            lineTo(size.width * 0.58f, size.height * 0.59f)
            lineTo(size.width * 0.52f, size.height * 0.63f)
            lineTo(size.width * 0.35f, size.height * 0.49f)
            lineTo(size.width * 0.19f, size.height * 0.63f)
            close()
        }
        drawPath(path = templeRoof, color = Color(0xFFE36D3C))
        drawRect(
            color = Color(0xFFFFD092),
            topLeft = Offset(size.width * 0.24f, size.height * 0.62f),
            size = Size(size.width * 0.22f, size.height * 0.16f)
        )
        drawLine(
            color = Color(0xFF4F7CAC),
            start = Offset(size.width * 0.12f, size.height * 0.84f),
            end = Offset(size.width * 0.88f, size.height * 0.54f),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        listOf(
            Offset(size.width * 0.20f, size.height * 0.81f),
            Offset(size.width * 0.53f, size.height * 0.68f),
            Offset(size.width * 0.82f, size.height * 0.56f)
        ).forEachIndexed { index, offset ->
            drawCircle(
                color = if (index == 1) Color(0xFFFF8A3D) else Color(0xFF00A6A6),
                radius = 11f,
                center = offset
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = offset
            )
        }
    }
}

@Composable
private fun TripSummaryStrip(
    firstTitle: String,
    firstSubtitle: String,
    secondTitle: String,
    secondSubtitle: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetric(
                modifier = Modifier.weight(1f),
                title = firstTitle,
                subtitle = firstSubtitle
            )
            SummaryMetric(
                modifier = Modifier.weight(1f),
                title = secondTitle,
                subtitle = secondSubtitle
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFD7FFE7))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF176B5B),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardPreviewPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Your itinerary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        PreviewTimelineItem(
            time = "09:00",
            title = "Hotel check-in",
            tag = "Hotel",
            accent = MaterialTheme.colorScheme.primary
        )
        PreviewTimelineItem(
            time = "13:00",
            title = "Lunch near the old town",
            tag = "Food",
            accent = Color(0xFFFF8A3D)
        )
        PreviewTimelineItem(
            time = "17:30",
            title = "Sunset attraction",
            tag = "Activity",
            accent = Color(0xFF6C63FF)
        )
    }
}

@Composable
private fun PreviewTimelineItem(
    time: String,
    title: String,
    tag: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            modifier = Modifier.width(48.dp),
            text = time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag.first().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun BottomAppNavigation(
    activeItem: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Home", "My Trips", "Explore", "Community", "Profile").forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (item == activeItem) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.first().uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item == activeItem) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item == activeItem) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanNewTripScreen(
    appSkin: AppSkin,
    onAppSkinChange: (AppSkin) -> Unit,
    editingTripId: Long?,
    onTripSaved: (Long) -> Unit,
    onBack: () -> Unit
) {
    var destination by rememberSaveable(editingTripId) { mutableStateOf("") }
    var startDate by rememberSaveable(editingTripId) { mutableStateOf("") }
    var endDate by rememberSaveable(editingTripId) { mutableStateOf("") }
    var selectedObjectType by rememberSaveable(editingTripId) { mutableStateOf(TripObjectType.FAMILY_MEMBER) }
    var objectName by rememberSaveable(editingTripId) { mutableStateOf("") }
    var priorityOrder by rememberSaveable(editingTripId) { mutableStateOf("1") }
    var editingObjectId by rememberSaveable(editingTripId) { mutableStateOf<Long?>(null) }
    var nextObjectId by rememberSaveable(editingTripId) { mutableLongStateOf(-1L) }
    var saveStatus by rememberSaveable(editingTripId) { mutableStateOf<String?>(null) }
    var googleFetchStatus by rememberSaveable(editingTripId) { mutableStateOf<String?>(null) }
    var isFetchingGoogleDetails by remember { mutableStateOf(false) }
    var isLoadingTrip by remember(editingTripId) { mutableStateOf(editingTripId != null) }
    var selectedPoolTripId by rememberSaveable(editingTripId) { mutableStateOf<Long?>(null) }
    var selectedPoolTypeFilter by rememberSaveable(editingTripId) {
        mutableStateOf<TripObjectType?>(null)
    }
    var showLinkedItemPicker by rememberSaveable(editingTripId, editingObjectId) {
        mutableStateOf(false)
    }
    val attributeValues = remember(editingTripId) { mutableStateMapOf<TripObjectAttribute, String>() }
    val relatedObjectIds = remember(editingTripId) { mutableStateListOf<Long>() }
    val tripObjects = remember(editingTripId) { mutableStateListOf<TripObjectDraft>() }
    val context = LocalContext.current
    val database = (context.applicationContext as TripPlannerApplication).database
    val activeTrips by database.tripDao().observeActiveTrips().collectAsState(initial = emptyList())
    val generalPoolItems by database.poolItemDao().observeGeneralPoolItems().collectAsState(initial = emptyList())
    val selectedTripPoolItems by remember(database, selectedPoolTripId) {
        selectedPoolTripId?.let { tripId ->
            database.poolItemDao().observeTripPoolItems(tripId)
        } ?: flowOf(emptyList<PoolItemEntity>())
    }.collectAsState(initial = emptyList())
    val tripRepository = remember(database) { TripRepository(database) }
    val googlePlacesRepository = remember(database) {
        GooglePlacesRepository(
            context = context.applicationContext,
            cacheDao = database.googlePlaceCacheDao()
        )
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(editingTripId, tripRepository) {
        if (editingTripId == null) {
            isLoadingTrip = false
            return@LaunchedEffect
        }

        isLoadingTrip = true
        runCatching {
            tripRepository.getEditableTrip(editingTripId)
        }.onSuccess { editableTrip ->
            if (editableTrip == null) {
                saveStatus = "Trip #$editingTripId was not found"
            } else {
                destination = editableTrip.trip.destination
                startDate = editableTrip.trip.startDate
                endDate = editableTrip.trip.endDate
                tripObjects.clear()
                tripObjects.addAll(editableTrip.objects)
                nextObjectId = -1L
                priorityOrder = (
                    (editableTrip.objects.maxOfOrNull { it.priorityOrder } ?: 0) + 1
                ).toString()
                saveStatus = null
            }
        }.onFailure { error ->
            saveStatus = error.message ?: "Trip could not be loaded"
        }
        isLoadingTrip = false
    }

    fun nextAvailablePriority(): Int {
        val usedOrders = tripObjects.map { it.priorityOrder }.toSet()
        return generateSequence(1) { it + 1 }
            .first { it !in usedOrders }
    }

    fun resetObjectForm() {
        selectedObjectType = TripObjectType.FAMILY_MEMBER
        objectName = ""
        priorityOrder = nextAvailablePriority().toString()
        editingObjectId = null
        attributeValues.clear()
        relatedObjectIds.clear()
        googleFetchStatus = null
    }

    fun editObject(tripObject: TripObjectDraft) {
        selectedObjectType = tripObject.type
        objectName = tripObject.name
        priorityOrder = tripObject.priorityOrder.toString()
        editingObjectId = tripObject.id
        attributeValues.clear()
        attributeValues.putAll(tripObject.attributes)
        relatedObjectIds.clear()
        relatedObjectIds.addAll(tripObject.relatedObjectIds)
        googleFetchStatus = null
    }

    fun applyGooglePlaceDetails(details: GooglePlaceDetails) {
        if (objectName.isBlank()) {
            details.displayName?.let { objectName = it }
        }

        fun setAttribute(attribute: TripObjectAttribute, value: String?) {
            if (attribute in selectedObjectType.attributes && !value.isNullOrBlank()) {
                attributeValues[attribute] = value
            }
        }

        setAttribute(TripObjectAttribute.GOOGLE_PLACE_ID, details.placeId)
        setAttribute(TripObjectAttribute.ADDRESS, details.formattedAddress)
        setAttribute(TripObjectAttribute.LATITUDE, details.latitude?.toString())
        setAttribute(TripObjectAttribute.LONGITUDE, details.longitude?.toString())
        setAttribute(TripObjectAttribute.PHONE_NUMBER, details.phoneNumber)
        setAttribute(TripObjectAttribute.WEBSITE_URL, details.websiteUrl)
        setAttribute(TripObjectAttribute.GOOGLE_MAPS_URL, details.googleMapsUri)
        setAttribute(TripObjectAttribute.WORKING_HOURS, details.openingHours)
    }

    val parsedPriorityOrder = priorityOrder.toIntOrNull()
    val priorityError = when {
        priorityOrder.isBlank() -> null
        parsedPriorityOrder == null -> "Enter a number"
        parsedPriorityOrder < 1 -> "Use 1 or higher"
        tripObjects.any {
            it.priorityOrder == parsedPriorityOrder && it.id != editingObjectId
        } -> "Order number already used"
        else -> null
    }
    val canSaveObject = objectName.isNotBlank() &&
        parsedPriorityOrder != null &&
        priorityError == null
    val googlePlaceId = attributeValues[TripObjectAttribute.GOOGLE_PLACE_ID].orEmpty().trim()
    val canFetchGoogleDetails = selectedObjectType.supportsGooglePlaceDetails() &&
        googlePlaceId.isNotBlank() &&
        !isFetchingGoogleDetails

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editingTripId == null) "Add new trip" else "Edit existing trip")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TripHeroPanel(
                title = destination.ifBlank { "New trip adventure" },
                subtitle = tripDateSubtitle(startDate = startDate, endDate = endDate),
                status = when {
                    isLoadingTrip -> "Loading"
                    editingTripId != null -> "Editing"
                    tripObjects.isEmpty() -> "Draft"
                    else -> "Planned"
                }
            )
            TripSummaryStrip(
                firstTitle = "${tripObjects.size} Items",
                firstSubtitle = "In this itinerary",
                secondTitle = "${tripObjects.count { it.type == TripObjectType.FAMILY_MEMBER }} Travellers",
                secondSubtitle = "Family members",
                status = if (tripObjects.any { it.type == TripObjectType.HOTEL }) "Booked" else "Planning"
            )
            Text(
                text = "Trip basics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            SkinSelector(
                modifier = Modifier.fillMaxWidth(),
                selectedSkin = appSkin,
                onSkinSelected = onAppSkinChange
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Destination") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start date") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End date") },
                        singleLine = true
                    )
                }
            }
            Text(
                text = "Trip objects",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            val tripObjectIds = tripObjects.map { it.id }.toSet()
            val selectedPoolItems = if (selectedPoolTripId == null) {
                generalPoolItems
            } else {
                selectedTripPoolItems
            }
            val visiblePoolItems = selectedPoolItems.filter { poolItem ->
                selectedPoolTypeFilter == null || poolItem.type == selectedPoolTypeFilter
            }
            ItemPoolPanel(
                trips = activeTrips,
                selectedTripPoolId = selectedPoolTripId,
                selectedTypeFilter = selectedPoolTypeFilter,
                poolItems = visiblePoolItems,
                totalItemCount = selectedPoolItems.size,
                currentTripObjectIds = tripObjectIds,
                onSelectTripPool = { selectedPoolTripId = it },
                onSelectTypeFilter = { selectedPoolTypeFilter = it },
                onUseItem = { poolItem ->
                    coroutineScope.launch {
                        val pooledDraft = tripRepository.getPoolItemDraft(poolItem.id)
                            ?: return@launch
                        val currentObjectIds = tripObjects.map { it.id }.toSet()
                        upsertTripObject(
                            tripObjects = tripObjects,
                            savedObject = pooledDraft.copy(
                                priorityOrder = nextAvailablePriority(),
                                relatedObjectIds = pooledDraft.relatedObjectIds
                                    .filter { it in currentObjectIds }
                                    .toSet()
                            )
                        )
                        saveStatus = "${poolItem.name} linked to this trip"
                    }
                }
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripObjectType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedObjectType == type,
                                onClick = { selectedObjectType = type },
                                label = { Text(type.displayName) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = objectName,
                        onValueChange = { objectName = it },
                        label = { Text("${selectedObjectType.displayName} name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = priorityOrder,
                        onValueChange = { priorityOrder = it.filter(Char::isDigit) },
                        label = { Text("Priority / order") },
                        singleLine = true,
                        isError = priorityError != null,
                        supportingText = priorityError?.let { error ->
                            { Text(error) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                    selectedObjectType.attributes.forEach { attribute ->
                        TripAttributeInput(
                            attribute = attribute,
                            value = attributeValues[attribute].orEmpty(),
                            onValueChange = { attributeValues[attribute] = it }
                        )
                    }
                    if (selectedObjectType.supportsGooglePlaceDetails()) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            onClick = {
                                coroutineScope.launch {
                                    isFetchingGoogleDetails = true
                                    googleFetchStatus = null
                                    val cachedOnly = !context.isOnline()
                                    runCatching {
                                        if (cachedOnly) {
                                            googlePlacesRepository.getCachedPlace(googlePlaceId)
                                                ?: error("No cached Google details for this place ID")
                                        } else {
                                            googlePlacesRepository.fetchAndCachePlace(googlePlaceId)
                                        }
                                    }.onSuccess { details ->
                                        applyGooglePlaceDetails(details)
                                        googleFetchStatus = if (cachedOnly) {
                                            "Cached Google details applied"
                                        } else {
                                            "Google details fetched"
                                        }
                                    }.onFailure { error ->
                                        googleFetchStatus = error.message ?: "Google details unavailable"
                                    }
                                    isFetchingGoogleDetails = false
                                }
                            },
                            enabled = canFetchGoogleDetails,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (isFetchingGoogleDetails) {
                                    "Fetching Google details"
                                } else {
                                    "Fetch Google details"
                                }
                            )
                        }
                        googleFetchStatus?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val relatedObjectCandidates = tripObjects
                        .filter { it.id != editingObjectId }
                        .sortedBy { it.priorityOrder }
                    if (relatedObjectCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Linked items",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${relatedObjectIds.size} selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showLinkedItemPicker = !showLinkedItemPicker }
                            ) {
                                Text(if (showLinkedItemPicker) "Hide" else "Show")
                            }
                        }
                        if (showLinkedItemPicker) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                relatedObjectCandidates.forEach { tripObject ->
                                    val isRelated = tripObject.id in relatedObjectIds
                                    FilterChip(
                                        selected = isRelated,
                                        onClick = {
                                            if (isRelated) {
                                                relatedObjectIds.remove(tripObject.id)
                                            } else {
                                                relatedObjectIds.add(tripObject.id)
                                            }
                                        },
                                        label = {
                                            Text("${tripObject.priorityOrder}. ${tripObject.name}")
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            onClick = {
                                val objectId = editingObjectId ?: nextObjectId--
                                val savedObject = TripObjectDraft(
                                    id = objectId,
                                    type = selectedObjectType,
                                    name = objectName.trim(),
                                    priorityOrder = parsedPriorityOrder ?: nextAvailablePriority(),
                                    attributes = selectedObjectType.attributes
                                        .associateWith { attributeValues[it].orEmpty().trim() }
                                        .filterValues { it.isNotBlank() },
                                    relatedObjectIds = relatedObjectIds.toSet()
                                )

                                upsertTripObject(
                                    tripObjects = tripObjects,
                                    savedObject = savedObject
                                )
                                resetObjectForm()
                            },
                            enabled = canSaveObject,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (editingObjectId == null) "Add object" else "Save object")
                        }
                        if (editingObjectId != null) {
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                onClick = { resetObjectForm() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                    if (tripObjects.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tripObjects
                                .sortedBy { it.priorityOrder }
                                .forEach { tripObject ->
                                    TripObjectRow(
                                        tripObject = tripObject,
                                        relatedObjects = tripObjects
                                            .filter { it.id in tripObject.relatedObjectIds }
                                            .sortedBy { it.priorityOrder },
                                        onEdit = { editObject(tripObject) }
                                    )
                                }
                        }
                    }
                }
            }
            Text(
                text = "Map and bookings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            TripMapView(tripObjects = tripObjects)
            BookingsPanel(tripObjects = tripObjects)
            PrivateMapPresetPanel(
                tripObjects = tripObjects,
                isSavedTrip = editingTripId != null,
                onOpenGoogleMaps = {
                    val mapIntent = GoogleMapsIntents.openMapIntent(tripObjects)
                    if (mapIntent == null) {
                        saveStatus = "Add coordinates before opening Google Maps"
                    } else {
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: ActivityNotFoundException) {
                            saveStatus = "Google Maps app is not available"
                        }
                    }
                }
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = {
                    coroutineScope.launch {
                        runCatching {
                            if (editingTripId == null) {
                                tripRepository.createTrip(
                                    destination = destination,
                                    startDate = startDate,
                                    endDate = endDate,
                                    objects = tripObjects
                                )
                            } else {
                                tripRepository.updateTrip(
                                    tripId = editingTripId,
                                    destination = destination,
                                    startDate = startDate,
                                    endDate = endDate,
                                    objects = tripObjects
                                )
                            }
                        }.onSuccess { result ->
                            onTripSaved(result.tripId)
                            tripRepository.getEditableTrip(result.tripId)?.let { editableTrip ->
                                destination = editableTrip.trip.destination
                                startDate = editableTrip.trip.startDate
                                endDate = editableTrip.trip.endDate
                                tripObjects.clear()
                                tripObjects.addAll(editableTrip.objects)
                                nextObjectId = -1L
                                priorityOrder = nextAvailablePriority().toString()
                                resetObjectForm()
                            }
                            saveStatus = buildString {
                                append(
                                    if (editingTripId == null) {
                                        "Trip saved locally #${result.tripId}"
                                    } else {
                                        "Trip updated locally #${result.tripId}"
                                    }
                                )
                                result.privateMapPresetId?.let { presetId ->
                                    append(". Private map preset #$presetId")
                                }
                            }
                        }.onFailure { error ->
                            saveStatus = error.message ?: "Trip could not be saved"
                        }
                    }
                },
                enabled = destination.isNotBlank() && !isLoadingTrip,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (editingTripId == null) "Create trip" else "Save trip")
            }
            saveStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    LaunchedEffect(activeTrips, selectedPoolTripId) {
        if (selectedPoolTripId != null && activeTrips.none { it.id == selectedPoolTripId }) {
            selectedPoolTripId = null
        }
    }
}

private fun tripDateSubtitle(
    startDate: String,
    endDate: String
): String {
    val start = startDate.ifBlank { "Start date" }
    val end = endDate.ifBlank { "End date" }
    return "$start - $end"
}

private fun tripDateRange(trip: TripEntity): String {
    return tripDateSubtitle(
        startDate = trip.startDate,
        endDate = trip.endDate
    )
}

@Composable
private fun PrivateMapPresetPanel(
    tripObjects: List<TripObjectDraft>,
    isSavedTrip: Boolean,
    onOpenGoogleMaps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mappedItemCount = tripObjects.count { it.hasMapPresetPoint() }
    val status = when {
        mappedItemCount == 0 -> "No map items"
        isSavedTrip -> "$mappedItemCount stored"
        else -> "$mappedItemCount ready"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Private preset map",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Trip Planner",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        BookingSummaryRow(
            label = "Local preset",
            value = status,
            accent = MaterialTheme.colorScheme.primary
        )
        BookingSummaryRow(
            label = "Google Maps",
            value = if (mappedItemCount == 0) "Not ready" else "Open online",
            accent = Color(0xFF4F7CAC)
        )
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            onClick = onOpenGoogleMaps,
            enabled = tripObjects.isNotEmpty(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Open in Google Maps")
        }
    }
}

@Composable
private fun TripItemsPanel(
    tripObjects: List<TripObjectDraft>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Trip items",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        tripObjects
            .sortedBy { it.priorityOrder }
            .forEach { tripObject ->
                TripItemSummaryRow(tripObject = tripObject)
            }
    }
}

@Composable
private fun TripItemSummaryRow(
    tripObject: TripObjectDraft
) {
    val accent = accentForTripObjectType(tripObject.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tripObject.priorityOrder.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = tripObject.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tripObject.type.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun TripObjectDraft.hasMapPresetPoint(): Boolean {
    fun hasCoordinates(
        latitudeAttribute: TripObjectAttribute,
        longitudeAttribute: TripObjectAttribute
    ): Boolean {
        val latitude = attributes[latitudeAttribute]?.toDoubleOrNull()
        val longitude = attributes[longitudeAttribute]?.toDoubleOrNull()
        return latitude != null &&
            longitude != null &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    if (type == TripObjectType.TRANSPORTATION) {
        return hasCoordinates(
            latitudeAttribute = TripObjectAttribute.DEPARTURE_LATITUDE,
            longitudeAttribute = TripObjectAttribute.DEPARTURE_LONGITUDE
        ) || hasCoordinates(
            latitudeAttribute = TripObjectAttribute.ARRIVAL_LATITUDE,
            longitudeAttribute = TripObjectAttribute.ARRIVAL_LONGITUDE
        )
    }

    return hasCoordinates(
        latitudeAttribute = TripObjectAttribute.LATITUDE,
        longitudeAttribute = TripObjectAttribute.LONGITUDE
    )
}

@Composable
private fun BookingsPanel(
    tripObjects: List<TripObjectDraft>,
    modifier: Modifier = Modifier
) {
    val transportCount = tripObjects.count { it.type == TripObjectType.TRANSPORTATION }
    val hotelCount = tripObjects.count { it.type == TripObjectType.HOTEL }
    val activityCount = tripObjects.count {
        it.type == TripObjectType.PAID_ATTRACTION || it.type == TripObjectType.FREE_ATTRACTION
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bookings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Local itinerary",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        BookingSummaryRow(
            label = "Transportation",
            value = if (transportCount == 0) "No legs yet" else "$transportCount planned",
            accent = Color(0xFF4F7CAC)
        )
        BookingSummaryRow(
            label = "Hotels",
            value = if (hotelCount == 0) "No hotels yet" else "$hotelCount planned",
            accent = MaterialTheme.colorScheme.primary
        )
        BookingSummaryRow(
            label = "Activities",
            value = if (activityCount == 0) "No attractions yet" else "$activityCount planned",
            accent = Color(0xFFFF8A3D)
        )
    }
}

@Composable
private fun BookingSummaryRow(
    label: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.first().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripAttributeInput(
    attribute: TripObjectAttribute,
    value: String,
    onValueChange: (String) -> Unit
) {
    if (attribute.options.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = attribute.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attribute.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onValueChange(option) },
                        label = { Text(option) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
        return
    }

    val isLongText = attribute.inputKind == TripObjectAttributeInputKind.LONG_TEXT ||
        attribute.inputKind == TripObjectAttributeInputKind.MAP ||
        attribute.inputKind == TripObjectAttributeInputKind.IMAGE

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(attribute.displayName) },
        singleLine = !isLongText,
        minLines = if (isLongText) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalizationFor(attribute.inputKind),
            keyboardType = keyboardTypeFor(attribute.inputKind)
        )
    )
}

private fun keyboardTypeFor(inputKind: TripObjectAttributeInputKind): KeyboardType {
    return when (inputKind) {
        TripObjectAttributeInputKind.URL,
        TripObjectAttributeInputKind.IMAGE,
        TripObjectAttributeInputKind.MAP -> KeyboardType.Uri
        TripObjectAttributeInputKind.PHONE -> KeyboardType.Phone
        TripObjectAttributeInputKind.EMAIL -> KeyboardType.Email
        TripObjectAttributeInputKind.NUMBER -> KeyboardType.Number
        TripObjectAttributeInputKind.MONEY -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
}

private fun capitalizationFor(
    inputKind: TripObjectAttributeInputKind
): KeyboardCapitalization {
    return when (inputKind) {
        TripObjectAttributeInputKind.EMAIL,
        TripObjectAttributeInputKind.URL,
        TripObjectAttributeInputKind.IMAGE,
        TripObjectAttributeInputKind.MAP -> KeyboardCapitalization.None
        else -> KeyboardCapitalization.Sentences
    }
}

private fun upsertTripObject(
    tripObjects: SnapshotStateList<TripObjectDraft>,
    savedObject: TripObjectDraft
) {
    val existingIndex = tripObjects.indexOfFirst { it.id == savedObject.id }
    if (existingIndex >= 0) {
        tripObjects[existingIndex] = savedObject
    } else {
        tripObjects.add(savedObject)
    }

    tripObjects.indices.forEach { index ->
        val tripObject = tripObjects[index]
        if (tripObject.id == savedObject.id) return@forEach

        val nextRelatedObjectIds = if (tripObject.id in savedObject.relatedObjectIds) {
            tripObject.relatedObjectIds + savedObject.id
        } else {
            tripObject.relatedObjectIds - savedObject.id
        }

        if (nextRelatedObjectIds != tripObject.relatedObjectIds) {
            tripObjects[index] = tripObject.copy(
                relatedObjectIds = nextRelatedObjectIds
            )
        }
    }
}

private fun TripObjectType.supportsGooglePlaceDetails(): Boolean {
    return this in setOf(
        TripObjectType.HOTEL,
        TripObjectType.FOOD_PLACE,
        TripObjectType.SHOP,
        TripObjectType.PAID_ATTRACTION,
        TripObjectType.FREE_ATTRACTION
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkinSelector(
    selectedSkin: AppSkin,
    onSkinSelected: (AppSkin) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppSkin.entries.forEach { skin ->
            FilterChip(
                selected = selectedSkin == skin,
                onClick = { onSkinSelected(skin) },
                label = { Text(skin.label) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun TripObjectRow(
    tripObject: TripObjectDraft,
    relatedObjects: List<TripObjectDraft>,
    onEdit: () -> Unit
) {
    val accent = accentForTripObjectType(tripObject.type)
    var showLinkedItems by rememberSaveable(tripObject.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "#${tripObject.priorityOrder}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tripObject.type.displayName.first().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tripObject.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tripObject.type.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
            }
            if (tripObject.attributes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tripObject.attributes.entries.take(4).forEach { (attribute, value) ->
                        AttributeTag(label = attribute.displayName, value = value)
                    }
                }
            }
            if (relatedObjects.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { showLinkedItems = !showLinkedItems }
                    ) {
                        Text(
                            if (showLinkedItems) {
                                "Hide linked items (${relatedObjects.size})"
                            } else {
                                "Show linked items (${relatedObjects.size})"
                            }
                        )
                    }
                    if (showLinkedItems) {
                        Text(
                            text = relatedObjects.joinToString { "${it.priorityOrder}. ${it.name}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        ObjectThumbnail(
            type = tripObject.type,
            accent = accent,
            modifier = Modifier.size(58.dp)
        )
    }
}

@Composable
private fun AttributeTag(
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ObjectThumbnail(
    type: TripObjectType,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
            .aspectRatio(1f)
    ) {
        drawCircle(
            color = accent.copy(alpha = 0.18f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.50f, size.height * 0.50f)
        )
        when (type) {
            TripObjectType.TRANSPORTATION -> {
                drawLine(
                    color = accent,
                    start = Offset(size.width * 0.22f, size.height * 0.70f),
                    end = Offset(size.width * 0.76f, size.height * 0.32f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = accent, radius = 6f, center = Offset(size.width * 0.22f, size.height * 0.70f))
                drawCircle(color = accent, radius = 6f, center = Offset(size.width * 0.76f, size.height * 0.32f))
            }
            TripObjectType.HOTEL -> {
                drawRect(
                    color = accent,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.26f),
                    size = Size(size.width * 0.52f, size.height * 0.48f)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.34f, size.height * 0.40f),
                    size = Size(size.width * 0.12f, size.height * 0.12f)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.54f, size.height * 0.40f),
                    size = Size(size.width * 0.12f, size.height * 0.12f)
                )
            }
            TripObjectType.FOOD_PLACE -> {
                drawCircle(color = accent, radius = size.minDimension * 0.24f, center = Offset(size.width * 0.5f, size.height * 0.52f))
                drawCircle(color = Color.White, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.5f, size.height * 0.52f))
            }
            TripObjectType.FAMILY_MEMBER -> {
                drawCircle(color = accent, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.5f, size.height * 0.34f))
                drawArc(
                    color = accent,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.25f, size.height * 0.46f),
                    size = Size(size.width * 0.50f, size.height * 0.42f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
            else -> {
                val pin = Path().apply {
                    moveTo(size.width * 0.50f, size.height * 0.78f)
                    cubicTo(
                        size.width * 0.28f,
                        size.height * 0.56f,
                        size.width * 0.30f,
                        size.height * 0.26f,
                        size.width * 0.50f,
                        size.height * 0.24f
                    )
                    cubicTo(
                        size.width * 0.70f,
                        size.height * 0.26f,
                        size.width * 0.72f,
                        size.height * 0.56f,
                        size.width * 0.50f,
                        size.height * 0.78f
                    )
                    close()
                }
                drawPath(path = pin, color = accent)
                drawCircle(color = Color.White, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.5f, size.height * 0.42f))
            }
        }
    }
}

private fun accentForTripObjectType(type: TripObjectType): Color {
    return when (type) {
        TripObjectType.FAMILY_MEMBER -> Color(0xFF4F7CAC)
        TripObjectType.HOTEL -> Color(0xFF176B5B)
        TripObjectType.FOOD_PLACE -> Color(0xFFFF8A3D)
        TripObjectType.TRANSPORTATION -> Color(0xFF00A6A6)
        TripObjectType.SHOP -> Color(0xFF6C63FF)
        TripObjectType.PAID_ATTRACTION -> Color(0xFF8B5CF6)
        TripObjectType.FREE_ATTRACTION -> Color(0xFF2E9D62)
    }
}

@Composable
@Suppress("DEPRECATION")
private fun TripPlannerTheme(
    appSkin: AppSkin = AppSkin.System,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val useDarkSkin = when (appSkin) {
        AppSkin.System -> systemIsDark
        AppSkin.Light -> false
        AppSkin.Dark -> true
    }
    val colorScheme = if (useDarkSkin) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF8BD8C5),
            onPrimary = Color(0xFF00382F),
            secondary = Color(0xFFE0C4B4),
            background = Color(0xFF101916),
            surface = Color(0xFF18231F),
            surfaceVariant = Color(0xFF25332E),
            onSurface = Color(0xFFE7EFEB),
            onSurfaceVariant = Color(0xFFB8C8C1)
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF176B5B),
            onPrimary = Color.White,
            secondary = Color(0xFF6C584C),
            background = Color(0xFFF7FAF8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE7ECE9),
            onSurface = Color(0xFF1F2A25),
            onSurfaceVariant = Color(0xFF5D6862)
        )
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            view.systemUiVisibility = if (useDarkSkin) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
private fun MainMenuPreview() {
    TripPlannerTheme {
        MainMenuScreen(
            authSession = AuthSession(
                accountId = "preview",
                displayName = "Preview",
                provider = AuthProvider.LOCAL
            ),
            appSkin = AppSkin.System,
            onAppSkinChange = {},
            onSignOut = {},
            onManageTrips = {},
            onUseExistingTrip = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanNewTripPreview() {
    TripPlannerTheme {
        PlanNewTripScreen(
            appSkin = AppSkin.System,
            onAppSkinChange = {},
            editingTripId = null,
            onTripSaved = {},
            onBack = {}
        )
    }
}
