package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometric.FaceRecognitionService
import com.example.biometric.ImageBitmapUtils
import com.example.biometric.RegistrationFaceCheck
import com.example.data.model.AttendanceMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.UserProfile
import com.example.data.model.VerificationLog
import com.example.data.repository.AttendanceRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface VerificationBannerState {
    data object Idle : VerificationBannerState
    data object Verifying : VerificationBannerState
    data class Success(
        val title: String,
        val detail: String,
        val confidence: Int
    ) : VerificationBannerState

    data class Failed(
        val title: String,
        val detail: String
    ) : VerificationBannerState
}

enum class HistoryFilter(val label: String) {
    ALL("All Records"),
    STARRED("Starred"),
    LOGS("Audit Log")
}

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val faceService: FaceRecognitionService,
    private val currentUserId: String
) : ViewModel() {

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (root == null && error is CancellationException) throw error
                val msg = (root ?: error).localizedMessage ?: "Failed to load user profile."
                Log.w(TAG, "Error observing profile", root ?: error)
                emit(UiState.Error(msg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val attendanceHistoryState: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeAttendanceHistory(currentUserId)
            .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (root == null && error is CancellationException) throw error
                val msg = (root ?: error).localizedMessage ?: "Failed to observe attendance history."
                Log.w(TAG, "Error observing attendance history", root ?: error)
                emit(UiState.Error(msg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val verificationLogsState: StateFlow<UiState<List<VerificationLog>>> =
        repository.observeVerificationLogs(currentUserId)
            .map<List<VerificationLog>, UiState<List<VerificationLog>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (root == null && error is CancellationException) throw error
                val msg = (root ?: error).localizedMessage ?: "Failed to observe verification logs."
                Log.w(TAG, "Error observing logs", root ?: error)
                emit(UiState.Error(msg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _selectedMode = MutableStateFlow(AttendanceMode.IN)
    val selectedMode: StateFlow<AttendanceMode> = _selectedMode.asStateFlow()

    private val _isScannerActive = MutableStateFlow(true)
    val isScannerActive: StateFlow<Boolean> = _isScannerActive.asStateFlow()

    private val _verificationBanner = MutableStateFlow<VerificationBannerState>(VerificationBannerState.Idle)
    val verificationBanner: StateFlow<VerificationBannerState> = _verificationBanner.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _isStarredDrawerOpen = MutableStateFlow(false)
    val isStarredDrawerOpen: StateFlow<Boolean> = _isStarredDrawerOpen.asStateFlow()

    private val _isEditingProfile = MutableStateFlow(false)
    val isEditingProfile: StateFlow<Boolean> = _isEditingProfile.asStateFlow()

    private val _registrationBusy = MutableStateFlow(false)
    val registrationBusy: StateFlow<Boolean> = _registrationBusy.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    fun setAttendanceMode(mode: AttendanceMode) {
        _selectedMode.value = mode
        if (_verificationBanner.value !is VerificationBannerState.Verifying) {
            _verificationBanner.value = VerificationBannerState.Idle
        }
    }

    fun toggleScanner(active: Boolean) {
        _isScannerActive.value = active
        if (!active && _verificationBanner.value is VerificationBannerState.Verifying) {
            _verificationBanner.value = VerificationBannerState.Idle
        }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun openStarredView(open: Boolean) {
        _isStarredDrawerOpen.value = open
    }

    fun setEditingProfile(editing: Boolean) {
        _isEditingProfile.value = editing
        _registrationError.value = null
    }

    fun dismissBanner() {
        _verificationBanner.value = VerificationBannerState.Idle
    }

    suspend fun checkRegistrationPhoto(bitmap: Bitmap): RegistrationFaceCheck {
        return faceService.validateRegistrationPhoto(bitmap)
    }

    fun saveAccountRegistration(
        fullName: String,
        email: String,
        photoBitmap: Bitmap
    ) {
        if (fullName.trim().length < 2) {
            _registrationError.value = "Please enter your full name (at least 2 characters)."
            return
        }
        viewModelScope.launch {
            _registrationBusy.value = true
            _registrationError.value = null
            try {
                val faceCheck = faceService.validateRegistrationPhoto(photoBitmap)
                if (!faceCheck.hasValidFace) {
                    _registrationError.value = faceCheck.message
                    _registrationBusy.value = false
                    return@launch
                }
                val base64 = ImageBitmapUtils.bitmapToJpegBase64(photoBitmap)
                val result = repository.saveUserProfile(
                    userId = currentUserId,
                    fullName = fullName.trim(),
                    email = email,
                    photoBase64 = base64
                )
                result.onSuccess {
                    _isEditingProfile.value = false
                }.onFailure { err ->
                    _registrationError.value = err.localizedMessage ?: "Failed to save account."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _registrationError.value = e.localizedMessage ?: "Account registration failed."
            } finally {
                _registrationBusy.value = false
            }
        }
    }

    fun processLiveFaceCapture(
        liveBitmap: Bitmap,
        profile: UserProfile,
        registeredBitmap: Bitmap?
    ) {
        if (_verificationBanner.value is VerificationBannerState.Verifying) return

        val mode = _selectedMode.value
        val todayKey = currentDateKey()

        viewModelScope.launch {
            _verificationBanner.value = VerificationBannerState.Verifying

            // 1. Validate daily IN / OUT rules BEFORE accepting attendance
            val todayResult = repository.getAttendanceForDate(currentUserId, todayKey)
            if (todayResult.isFailure) {
                val msg = todayResult.exceptionOrNull()?.localizedMessage
                    ?: "Could not verify today's attendance state."
                _verificationBanner.value = VerificationBannerState.Failed(
                    title = "FAILED • Server Validation Error",
                    detail = msg
                )
                return@launch
            }
            val todayRecord = todayResult.getOrNull()

            when (mode) {
                AttendanceMode.IN -> {
                    if (todayRecord != null) {
                        val inText = formatTime(todayRecord.inTime)
                        val reason = "Duplicate Check-IN blocked. You already checked IN today at $inText."
                        logAttempt(
                            userName = profile.fullName,
                            dateKey = todayKey,
                            mode = mode,
                            faceStatus = "SKIPPED",
                            attendanceStatus = "FAILED",
                            reason = reason,
                            confidence = 0
                        )
                        _verificationBanner.value = VerificationBannerState.Failed(
                            title = "FAILED • Duplicate Check-IN",
                            detail = reason
                        )
                        _isScannerActive.value = false
                        return@launch
                    }
                }
                AttendanceMode.OUT -> {
                    if (todayRecord == null) {
                        val reason = "Check-OUT cannot be completed before Check-IN today."
                        logAttempt(
                            userName = profile.fullName,
                            dateKey = todayKey,
                            mode = mode,
                            faceStatus = "SKIPPED",
                            attendanceStatus = "FAILED",
                            reason = reason,
                            confidence = 0
                        )
                        _verificationBanner.value = VerificationBannerState.Failed(
                            title = "FAILED • Check-IN Required First",
                            detail = reason
                        )
                        _isScannerActive.value = false
                        return@launch
                    }
                    if (todayRecord.outTime != null || todayRecord.attendanceStatus == "COMPLETED") {
                        val outText = formatTime(todayRecord.outTime)
                        val reason = "Duplicate Check-OUT blocked. You already checked OUT today at $outText."
                        logAttempt(
                            userName = profile.fullName,
                            dateKey = todayKey,
                            mode = mode,
                            faceStatus = "SKIPPED",
                            attendanceStatus = "FAILED",
                            reason = reason,
                            confidence = 0
                        )
                        _verificationBanner.value = VerificationBannerState.Failed(
                            title = "FAILED • Duplicate Check-OUT",
                            detail = reason
                        )
                        _isScannerActive.value = false
                        return@launch
                    }
                }
            }

            // 2. Verify live face against registered profile photo
            val refBitmap = registeredBitmap ?: ImageBitmapUtils.base64ToBitmap(profile.photoBase64)
            if (refBitmap == null) {
                _verificationBanner.value = VerificationBannerState.Failed(
                    title = "FAILED • Reference Photo Missing",
                    detail = "Registered reference photo could not be decoded. Please update your profile photo."
                )
                return@launch
            }

            val faceVerification = faceService.verifyFaces(refBitmap, liveBitmap)
            if (faceVerification.isFailure) {
                val errMsg = faceVerification.exceptionOrNull()?.localizedMessage
                    ?: "Face verification request failed."
                logAttempt(
                    userName = profile.fullName,
                    dateKey = todayKey,
                    mode = mode,
                    faceStatus = "FAILED",
                    attendanceStatus = "FAILED",
                    reason = errMsg,
                    confidence = 0
                )
                _verificationBanner.value = VerificationBannerState.Failed(
                    title = "FAILED • Verification Error",
                    detail = errMsg
                )
                return@launch
            }

            val match = faceVerification.getOrThrow()
            if (!match.isVerified) {
                val reason = if (!match.faceDetectedInLive) {
                    "No clear face detected in live capture. ${match.summary}"
                } else {
                    "Face does not match registered profile (${match.confidenceScore}% match). ${match.summary}"
                }
                logAttempt(
                    userName = profile.fullName,
                    dateKey = todayKey,
                    mode = mode,
                    faceStatus = "FAILED",
                    attendanceStatus = "FAILED",
                    reason = reason,
                    confidence = match.confidenceScore
                )
                _verificationBanner.value = VerificationBannerState.Failed(
                    title = "FAILED • Face Mismatch",
                    detail = reason
                )
                return@launch
            }

            // 3. Commit server-timestamped attendance record to Firestore
            val writeResult = when (mode) {
                AttendanceMode.IN -> repository.recordCheckIn(
                    userId = currentUserId,
                    userName = profile.fullName,
                    dateKey = todayKey,
                    confidence = match.confidenceScore
                )
                AttendanceMode.OUT -> repository.recordCheckOut(
                    userId = currentUserId,
                    dateKey = todayKey,
                    confidence = match.confidenceScore
                )
            }

            writeResult.onSuccess {
                val successMsg = "Check-${mode.label} verified (${match.confidenceScore}% match) & saved with server time."
                logAttempt(
                    userName = profile.fullName,
                    dateKey = todayKey,
                    mode = mode,
                    faceStatus = "VERIFIED",
                    attendanceStatus = "SUCCESS",
                    reason = successMsg,
                    confidence = match.confidenceScore
                )
                _verificationBanner.value = VerificationBannerState.Success(
                    title = "VERIFIED • Check-${mode.label} Accepted",
                    detail = successMsg,
                    confidence = match.confidenceScore
                )
                _isScannerActive.value = false
            }.onFailure { err ->
                val reason = err.localizedMessage ?: "Server validation rejected attendance write."
                logAttempt(
                    userName = profile.fullName,
                    dateKey = todayKey,
                    mode = mode,
                    faceStatus = "VERIFIED",
                    attendanceStatus = "FAILED",
                    reason = reason,
                    confidence = match.confidenceScore
                )
                _verificationBanner.value = VerificationBannerState.Failed(
                    title = "FAILED • Rule Validation",
                    detail = reason
                )
                _isScannerActive.value = false
            }
        }
    }

    fun toggleRecordStar(record: AttendanceRecord) {
        viewModelScope.launch {
            repository.toggleStarred(
                userId = currentUserId,
                dateKey = record.dateKey,
                newStarredValue = !record.starred
            )
        }
    }

    private suspend fun logAttempt(
        userName: String,
        dateKey: String,
        mode: AttendanceMode,
        faceStatus: String,
        attendanceStatus: String,
        reason: String,
        confidence: Int
    ) {
        repository.logVerificationAttempt(
            userId = currentUserId,
            userName = userName,
            dateKey = dateKey,
            mode = mode,
            faceVerificationStatus = faceStatus,
            attendanceStatus = attendanceStatus,
            reason = reason,
            confidence = confidence
        )
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    companion object {
        private const val TAG = "AttendanceVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun currentDateKey(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(Date())
        }

        fun formatTime(timestamp: Timestamp?): String {
            if (timestamp == null) return "--:--"
            val fmt = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            return fmt.format(timestamp.toDate())
        }
    }
}
