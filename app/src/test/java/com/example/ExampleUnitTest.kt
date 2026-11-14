package com.example

import com.example.data.model.FaceVerificationResult
import com.example.ui.viewmodel.AttendanceViewModel
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import org.junit.Test
import java.util.Date

class ExampleUnitTest {

    @Test
    fun faceVerificationResult_requiresFaceSamePersonAndThreshold() {
        val valid = FaceVerificationResult(
            faceDetectedInLive = true,
            isSamePerson = true,
            confidenceScore = 88,
            summary = "Same individual"
        )
        val lowConfidence = valid.copy(confidenceScore = 50)
        val mismatch = valid.copy(isSamePerson = false, confidenceScore = 95)
        val noFace = valid.copy(faceDetectedInLive = false, confidenceScore = 95)

        assertThat(valid.isVerified).isTrue()
        assertThat(lowConfidence.isVerified).isFalse()
        assertThat(mismatch.isVerified).isFalse()
        assertThat(noFace.isVerified).isFalse()
    }

    @Test
    fun dateKeyAndTimestampFormatting_matchExpectedFormat() {
        val dateKey = AttendanceViewModel.currentDateKey()
        assertThat(dateKey).matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

        assertThat(AttendanceViewModel.formatTime(null)).isEqualTo("--:--")
        val formatted = AttendanceViewModel.formatTime(Timestamp(Date(1_700_000_000_000L)))
        assertThat(formatted).isNotEmpty()
    }
}
