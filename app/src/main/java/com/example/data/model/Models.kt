package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val photoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val dateKey: String = "",
    @ServerTimestamp val inTime: Timestamp? = null,
    val outTime: Timestamp? = null,
    val inConfidence: Long = 0L,
    val outConfidence: Long? = null,
    val faceVerificationStatus: String = "VERIFIED",
    val attendanceStatus: String = "CHECKED_IN",
    val starred: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VerificationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val dateKey: String = "",
    val actionType: String = "IN",
    val faceVerificationStatus: String = "FAILED",
    val attendanceStatus: String = "FAILED",
    val reason: String = "",
    val confidence: Long = 0L,
    @ServerTimestamp val timestamp: Timestamp? = null
)

enum class AttendanceMode(val label: String) {
    IN("IN"),
    OUT("OUT")
}

data class FaceVerificationResult(
    val faceDetectedInLive: Boolean,
    val isSamePerson: Boolean,
    val confidenceScore: Int,
    val summary: String
) {
    val isVerified: Boolean
        get() = faceDetectedInLive && isSamePerson && confidenceScore >= 65
}
