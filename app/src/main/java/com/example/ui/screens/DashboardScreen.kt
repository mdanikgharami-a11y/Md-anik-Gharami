package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.biometric.FaceRecognitionService
import com.example.biometric.ImageBitmapUtils
import com.example.data.model.AttendanceMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.UserProfile
import com.example.data.model.VerificationLog
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.VerificationBannerState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    profile: UserProfile,
    historyState: UiState<List<AttendanceRecord>>,
    logsState: UiState<List<VerificationLog>>,
    selectedMode: AttendanceMode,
    isScannerActive: Boolean,
    verificationBanner: VerificationBannerState,
    historyFilter: HistoryFilter,
    isStarredViewOpen: Boolean,
    isDarkTheme: Boolean,
    faceService: FaceRecognitionService,
    onToggleTheme: () -> Unit,
    onSelectMode: (AttendanceMode) -> Unit,
    onToggleScanner: (Boolean) -> Unit,
    onCaptureLiveFace: (Bitmap, Bitmap?) -> Unit,
    onDismissBanner: () -> Unit,
    onSelectHistoryFilter: (HistoryFilter) -> Unit,
    onToggleStarRecord: (AttendanceRecord) -> Unit,
    onOpenStarredView: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val registeredBitmap = remember(profile.photoBase64) {
        ImageBitmapUtils.base64ToBitmap(profile.photoBase64)
    }

    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val allRecords = (historyState as? UiState.Success)?.data ?: emptyList()
    val allLogs = (logsState as? UiState.Success)?.data ?: emptyList()
    val todayKey = remember(currentMillis / 60_000L) { AttendanceViewModel.currentDateKey() }
    val todayRecord = remember(allRecords, todayKey) {
        allRecords.firstOrNull { it.dateKey == todayKey }
    }
    val starredRecords = remember(allRecords) { allRecords.filter { it.starred } }

    if (isStarredViewOpen) {
        BackHandler { onOpenStarredView(false) }
        StarredRecordsView(
            starredRecords = starredRecords,
            totalRecords = allRecords.size,
            completedRecords = allRecords.count { it.attendanceStatus == "COMPLETED" },
            onToggleStar = onToggleStarRecord,
            onBack = { onOpenStarredView(false) }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TOP BAR: Top-left Star ⭐ button + Top-right IN/OUT & ON/OFF toggle switch
            item {
                DashboardTopBar(
                    starredCount = starredRecords.size,
                    selectedMode = selectedMode,
                    isScannerActive = isScannerActive,
                    isDarkTheme = isDarkTheme,
                    onStarClick = { onOpenStarredView(true) },
                    onToggleTheme = onToggleTheme,
                    onSelectMode = onSelectMode,
                    onToggleScanner = onToggleScanner,
                    onEditProfile = onEditProfile,
                    onSignOut = onSignOut
                )
            }

            // 2. USER IDENTITY + LIVE DATE/TIME + ATTENDANCE STATUS CARD
            item {
                IdentityAndStatusCard(
                    profile = profile,
                    registeredBitmap = registeredBitmap,
                    currentMillis = currentMillis,
                    todayRecord = todayRecord
                )
            }

            // 3. GREEN SUCCESS / RED FAILED VERIFICATION BANNER
            item {
                AnimatedVisibility(
                    visible = verificationBanner !is VerificationBannerState.Idle,
                    enter = expandVertically(spring()) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    VerificationFeedbackCard(
                        banner = verificationBanner,
                        onDismiss = onDismissBanner
                    )
                }
            }

            // 4. CENTER: LARGE FACE SCAN CAMERA AREA
            item {
                FaceScanCameraArea(
                    isScannerActive = isScannerActive,
                    isVerifying = verificationBanner is VerificationBannerState.Verifying,
                    selectedMode = selectedMode,
                    faceService = faceService,
                    onActivateScanner = { onToggleScanner(true) },
                    onCaptureFrameForVerification = { liveBmp ->
                        onCaptureLiveFace(liveBmp, registeredBitmap)
                    }
                )
            }

            // 5. ATTENDANCE HISTORY HEADER & FILTERS
            item {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Attendance History",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "${allRecords.size} days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = historyFilter == filter,
                                onClick = { onSelectHistoryFilter(filter) },
                                label = {
                                    val labelText = when (filter) {
                                        HistoryFilter.STARRED -> "${filter.label} (${starredRecords.size})"
                                        HistoryFilter.LOGS -> "${filter.label} (${allLogs.size})"
                                        HistoryFilter.ALL -> filter.label
                                    }
                                    Text(labelText)
                                },
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 6. ATTENDANCE HISTORY LIST / AUDIT LOG LIST
            if (historyFilter == HistoryFilter.LOGS) {
                when (logsState) {
                    UiState.Loading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item {
                        ErrorCard(message = logsState.message)
                    }
                    is UiState.Success -> {
                        if (logsState.data.isEmpty()) {
                            item {
                                EmptyHistoryCard(
                                    title = "No verification attempts yet",
                                    subtitle = "All successful and failed verification attempts will appear here permanently."
                                )
                            }
                        } else {
                            items(logsState.data, key = { it.id }) { log ->
                                VerificationLogCard(log = log)
                            }
                        }
                    }
                }
            } else {
                when (historyState) {
                    UiState.Loading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item {
                        ErrorCard(message = historyState.message)
                    }
                    is UiState.Success -> {
                        val visibleRecords = if (historyFilter == HistoryFilter.STARRED) {
                            starredRecords
                        } else {
                            allRecords
                        }
                        if (visibleRecords.isEmpty()) {
                            item {
                                EmptyHistoryCard(
                                    title = if (historyFilter == HistoryFilter.STARRED) {
                                        "No starred records yet"
                                    } else {
                                        "No attendance records yet"
                                    },
                                    subtitle = if (historyFilter == HistoryFilter.STARRED) {
                                        "Tap the star icon on any attendance record to bookmark it here."
                                    } else {
                                        "Turn ON the top-right scanner switch and verify your face to check IN."
                                    }
                                )
                            }
                        } else {
                            items(visibleRecords, key = { it.dateKey }) { record ->
                                AttendanceRecordCard(
                                    record = record,
                                    onToggleStar = { onToggleStarRecord(record) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DashboardTopBar(
    starredCount: Int,
    selectedMode: AttendanceMode,
    isScannerActive: Boolean,
    isDarkTheme: Boolean,
    onStarClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectMode: (AttendanceMode) -> Unit,
    onToggleScanner: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TOP LEFT: Star Button ⭐ + Overflow Menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onStarClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("star_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (starredCount > 0) {
                                Badge(
                                    containerColor = semantic.star,
                                    contentColor = Color.Black
                                ) {
                                    Text(starredCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Open starred attendance records",
                            tint = semantic.star,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("account_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Account options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Update Name & Face Photo") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign Out") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSignOut()
                            }
                        )
                    }
                }
            }

            // TOP RIGHT: IN / OUT Toggle Switch with ON/OFF Functionality
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        AttendanceMode.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
                            val pillColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    if (mode == AttendanceMode.IN) semantic.success else MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                label = "modePill"
                            )
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = pillColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { onSelectMode(mode) }
                                    .testTag("mode_${mode.name.lowercase()}_button")
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = isScannerActive,
                        onCheckedChange = onToggleScanner,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = semantic.success
                        ),
                        modifier = Modifier.testTag("scanner_power_switch")
                    )
                    Text(
                        text = if (isScannerActive) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isScannerActive) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityAndStatusCard(
    profile: UserProfile,
    registeredBitmap: Bitmap?,
    currentMillis: Long,
    todayRecord: AttendanceRecord?
) {
    val semantic = LocalSemanticColors.current
    val dateStr = remember(currentMillis / 60_000L) {
        SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(Date(currentMillis))
    }
    val timeStr = remember(currentMillis / 1000L) {
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(currentMillis))
    }

    val (statusLabel, statusColor, statusBg) = when {
        todayRecord == null -> Triple(
            "Not Checked IN Today",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        todayRecord.attendanceStatus == "COMPLETED" -> Triple(
            "Completed • IN & OUT Saved",
            semantic.success,
            semantic.successContainer
        )
        else -> Triple(
            "Checked IN at ${AttendanceViewModel.formatTime(todayRecord.inTime)}",
            semantic.success,
            semantic.successContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (registeredBitmap != null) {
                        Image(
                            bitmap = registeredBitmap.asImageBitmap(),
                            contentDescription = "Registered profile photo for ${profile.fullName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationFeedbackCard(
    banner: VerificationBannerState,
    onDismiss: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    when (banner) {
        VerificationBannerState.Idle -> Unit
        VerificationBannerState.Verifying -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Verifying live face against registered profile photo...",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        is VerificationBannerState.Success -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = semantic.successContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verification_success_banner")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        tint = semantic.success,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = banner.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = semantic.success
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = banner.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss message")
                    }
                }
            }
        }
        is VerificationBannerState.Failed -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = semantic.failedContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verification_failed_banner")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = "Failed",
                        tint = semantic.failed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = banner.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = semantic.failed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = banner.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss error")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecordCard(
    record: AttendanceRecord,
    onToggleStar: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val isCompleted = record.attendanceStatus == "COMPLETED"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_card_${record.dateKey}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.userName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = record.dateKey,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = semantic.successContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (isCompleted) "COMPLETED" else "CHECKED IN",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.success,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("star_record_${record.dateKey}")
                    ) {
                        Icon(
                            imageVector = if (record.starred) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (record.starred) "Unstar record" else "Star record",
                            tint = if (record.starred) semantic.star else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimeBadgeColumn(
                    label = "IN TIME",
                    value = AttendanceViewModel.formatTime(record.inTime),
                    icon = Icons.AutoMirrored.Rounded.Login,
                    tint = semantic.success,
                    modifier = Modifier.weight(1f)
                )
                TimeBadgeColumn(
                    label = "OUT TIME",
                    value = if (record.outTime != null) AttendanceViewModel.formatTime(record.outTime) else "Pending",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    tint = if (record.outTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Face Verification: ${record.faceVerificationStatus} (${record.inConfidence}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantic.success
                )
                Text(
                    text = "Status: ${if (isCompleted) "IN & OUT Done" else "Active Shift"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimeBadgeColumn(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun VerificationLogCard(log: VerificationLog) {
    val semantic = LocalSemanticColors.current
    val isSuccess = log.attendanceStatus == "SUCCESS"
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = null,
                tint = if (isSuccess) semantic.success else semantic.failed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${log.actionType} • ${log.attendanceStatus}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSuccess) semantic.success else semantic.failed
                    )
                    Text(
                        text = "${log.dateKey} ${AttendanceViewModel.formatTime(log.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Face: ${log.faceVerificationStatus} • Confidence: ${log.confidence}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StarredRecordsView(
    starredRecords: List<AttendanceRecord>,
    totalRecords: Int,
    completedRecords: Int,
    onToggleStar: (AttendanceRecord) -> Unit,
    onBack: () -> Unit
) {
    val semantic = LocalSemanticColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to dashboard"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = semantic.star,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Starred Records & Insights",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStatTile(
                        label = "Total Days",
                        value = totalRecords.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatTile(
                        label = "Completed",
                        value = completedRecords.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatTile(
                        label = "Starred",
                        value = starredRecords.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Server-side Firestore rules lock all IN/OUT timestamps and allow only one Check-IN and one Check-OUT per day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (starredRecords.isEmpty()) {
                item {
                    EmptyHistoryCard(
                        title = "No Starred Records Yet",
                        subtitle = "Tap the star icon on any attendance history card to save important days here."
                    )
                }
            } else {
                items(starredRecords, key = { it.dateKey }) { record ->
                    AttendanceRecordCard(
                        record = record,
                        onToggleStar = { onToggleStar(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val semantic = LocalSemanticColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = semantic.failedContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = semantic.failed,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
