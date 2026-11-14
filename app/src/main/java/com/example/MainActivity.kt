package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.biometric.FaceRecognitionService
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.AttendanceRepository
import com.example.security.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RegistrationScreen
import com.example.ui.theme.FaceAttendTheme
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check before Compose UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val themePrefs = remember { ThemePreferences(applicationContext) }
            val themeOverride by themePrefs.isDarkThemeOverride.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = themeOverride ?: systemDark
            val scope = rememberCoroutineScope()

            FaceAttendTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            scope.launch {
                                themePrefs.setDarkTheme(!isDarkTheme)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val isFirebaseInitialized = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty() ||
            runCatching { FirebaseApp.initializeApp(context) != null }.getOrDefault(false)
    }
    if (!isFirebaseInitialized) {
        MissingFirebaseConfigScreen()
        return
    }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onAuthSuccess = {}
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedHost(
                currentUserId = uid,
                defaultName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty(),
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedHost(
    currentUserId: String,
    defaultName: String,
    userEmail: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val faceService = remember { FaceRecognitionService() }
    val viewModel: AttendanceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AttendanceViewModel(
                    repository = AttendanceRepository(db),
                    faceService = faceService,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val historyState by viewModel.attendanceHistoryState.collectAsStateWithLifecycle()
    val logsState by viewModel.verificationLogsState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val isScannerActive by viewModel.isScannerActive.collectAsStateWithLifecycle()
    val verificationBanner by viewModel.verificationBanner.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val isStarredViewOpen by viewModel.isStarredDrawerOpen.collectAsStateWithLifecycle()
    val isEditingProfile by viewModel.isEditingProfile.collectAsStateWithLifecycle()
    val registrationBusy by viewModel.registrationBusy.collectAsStateWithLifecycle()
    val registrationError by viewModel.registrationError.collectAsStateWithLifecycle()

    when (val state = profileState) {
        UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        is UiState.Success -> {
            val profile = state.data
            if (profile == null || isEditingProfile) {
                RegistrationScreen(
                    existingProfile = profile,
                    defaultName = defaultName,
                    userEmail = userEmail,
                    isBusy = registrationBusy,
                    errorMessage = registrationError,
                    onValidatePhoto = { bmp -> viewModel.checkRegistrationPhoto(bmp) },
                    onSaveAccount = { name, email, bmp ->
                        viewModel.saveAccountRegistration(name, email, bmp)
                    },
                    onCancelEdit = if (profile != null) {
                        { viewModel.setEditingProfile(false) }
                    } else null,
                    onSignOut = onSignOut
                )
            } else {
                DashboardScreen(
                    profile = profile,
                    historyState = historyState,
                    logsState = logsState,
                    selectedMode = selectedMode,
                    isScannerActive = isScannerActive,
                    verificationBanner = verificationBanner,
                    historyFilter = historyFilter,
                    isStarredViewOpen = isStarredViewOpen,
                    isDarkTheme = isDarkTheme,
                    faceService = faceService,
                    onToggleTheme = onToggleTheme,
                    onSelectMode = viewModel::setAttendanceMode,
                    onToggleScanner = viewModel::toggleScanner,
                    onCaptureLiveFace = { liveBmp, regBmp ->
                        viewModel.processLiveFaceCapture(liveBmp, profile, regBmp)
                    },
                    onDismissBanner = viewModel::dismissBanner,
                    onSelectHistoryFilter = viewModel::setHistoryFilter,
                    onToggleStarRecord = viewModel::toggleRecordStar,
                    onOpenStarredView = viewModel::openStarredView,
                    onEditProfile = { viewModel.setEditingProfile(true) },
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun MissingFirebaseConfigScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Configuration required",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Firebase Config Missing (google-services.json)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "This APK was built without app/google-services.json, so Firebase Authentication and Firestore could not initialize.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "How to fix:\n1. Generate the APK directly from AI Studio's Settings menu, OR\n2. Add GOOGLE_SERVICES_JSON and DEBUG_KEYSTORE_BASE64 in GitHub Repository Secrets and re-run the workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

