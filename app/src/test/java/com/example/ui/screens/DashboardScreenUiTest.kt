package com.example.ui.screens

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.biometric.FaceRecognitionService
import com.example.data.model.AttendanceMode
import com.example.data.model.UserProfile
import com.example.ui.theme.FaceAttendTheme
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.VerificationBannerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleProfile(): UserProfile {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return UserProfile(
            userId = "user_1",
            fullName = "Nadia Karim",
            email = "nadia@example.com",
            photoBase64 = encoded
        )
    }

    @Test
    fun mainScreen_displaysCameraPreviewAreaAndDedicatedRecognitionButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)

        var toggledScannerValue: Boolean? = null
        var selectedModeValue: AttendanceMode? = null

        composeRule.setContent {
            FaceAttendTheme(darkTheme = false) {
                DashboardScreen(
                    profile = sampleProfile(),
                    historyState = UiState.Success(emptyList()),
                    logsState = UiState.Success(emptyList()),
                    selectedMode = AttendanceMode.IN,
                    isScannerActive = false,
                    verificationBanner = VerificationBannerState.Idle,
                    historyFilter = HistoryFilter.ALL,
                    isStarredViewOpen = false,
                    isDarkTheme = false,
                    faceService = FaceRecognitionService(),
                    onToggleTheme = {},
                    onSelectMode = { selectedModeValue = it },
                    onToggleScanner = { toggledScannerValue = it },
                    onCaptureLiveFace = { _, _ -> },
                    onDismissBanner = {},
                    onSelectHistoryFilter = {},
                    onToggleStarRecord = {},
                    onOpenStarredView = {},
                    onEditProfile = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithTag("star_button").assertIsDisplayed()
        composeRule.onNodeWithTag("scanner_power_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_out_button").performClick()
        assertEquals(AttendanceMode.OUT, selectedModeValue)

        composeRule.onNodeWithTag("face_scan_camera_area").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("camerax_preview_container").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("initiate_face_recognition_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(toggledScannerValue == true)
    }
}
