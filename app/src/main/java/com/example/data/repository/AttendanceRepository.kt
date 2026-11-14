package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AttendanceMode
import com.example.data.model.AttendanceRecord
import com.example.data.model.UserProfile
import com.example.data.model.VerificationLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AttendanceRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                logFlowError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(
        userId: String,
        fullName: String,
        email: String,
        photoBase64: String
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "fullName" to fullName.trim(),
                    "email" to email.trim(),
                    "photoBase64" to photoBase64,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "fullName" to fullName.trim(),
                    "email" to email.trim(),
                    "photoBase64" to photoBase64,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAttendanceHistory(userId: String): Flow<List<AttendanceRecord>> {
        val collectionRef = db.collection("users").document(userId).collection("attendance")
        return collectionRef
            .orderBy("dateKey", Query.Direction.DESCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    AttendanceRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, collectionRef.path)
                throw error
            }
    }

    fun observeVerificationLogs(userId: String): Flow<List<VerificationLog>> {
        val collectionRef = db.collection("users").document(userId).collection("attendance_logs")
        return collectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(40)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    VerificationLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logFlowError(error, OperationType.LIST, collectionRef.path)
                throw error
            }
    }

    suspend fun getAttendanceForDate(userId: String, dateKey: String): Result<AttendanceRecord?> {
        val docRef = db.collection("users").document(userId)
            .collection("attendance").document(dateKey)
        return try {
            val snapshot = docRef.get().await()
            val record = if (snapshot.exists()) {
                snapshot.toObject(
                    AttendanceRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordCheckIn(
        userId: String,
        userName: String,
        dateKey: String,
        confidence: Int
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("attendance").document(dateKey)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.failure(
                    IllegalStateException("FAILED: Already checked IN today ($dateKey).")
                )
            }
            val payload = mapOf(
                "userId" to userId,
                "userName" to userName.trim(),
                "dateKey" to dateKey,
                "inTime" to FieldValue.serverTimestamp(),
                "inConfidence" to confidence.coerceIn(65, 100),
                "faceVerificationStatus" to "VERIFIED",
                "attendanceStatus" to "CHECKED_IN",
                "starred" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordCheckOut(
        userId: String,
        dateKey: String,
        confidence: Int
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("attendance").document(dateKey)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                return Result.failure(
                    IllegalStateException("FAILED: Check-OUT cannot be completed before Check-IN.")
                )
            }
            val currentStatus = existing.getString("attendanceStatus")
            val existingOutTime = existing.getTimestamp("outTime")
            if (currentStatus == "COMPLETED" || existingOutTime != null) {
                return Result.failure(
                    IllegalStateException("FAILED: Already checked OUT today ($dateKey).")
                )
            }
            val updates = mapOf(
                "outTime" to FieldValue.serverTimestamp(),
                "outConfidence" to confidence.coerceIn(65, 100),
                "faceVerificationStatus" to "VERIFIED",
                "attendanceStatus" to "COMPLETED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStarred(
        userId: String,
        dateKey: String,
        newStarredValue: Boolean
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("attendance").document(dateKey)
        return try {
            val updates = mapOf(
                "starred" to newStarredValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun logVerificationAttempt(
        userId: String,
        userName: String,
        dateKey: String,
        mode: AttendanceMode,
        faceVerificationStatus: String,
        attendanceStatus: String,
        reason: String,
        confidence: Int
    ): Result<Unit> {
        val logId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId)
            .collection("attendance_logs").document(logId)
        return try {
            val payload = mapOf(
                "userId" to userId,
                "userName" to userName.ifBlank { "User" }.take(80),
                "dateKey" to dateKey,
                "actionType" to mode.name,
                "faceVerificationStatus" to faceVerificationStatus,
                "attendanceStatus" to attendanceStatus,
                "reason" to reason.ifBlank { "Verification attempt" }.take(240),
                "confidence" to confidence.coerceIn(0, 100),
                "timestamp" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun logFlowError(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        handleFirestoreError(firestoreError ?: error, op, path)
    }
}
