package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AttendanceMode
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID
import org.junit.Assert.fail

class AttendanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueDateKey(): String {
        val randomDay = (10..28).random()
        val randomMonth = (10..12).random()
        val randomYear = (2020..2099).random()
        return "%04d-%02d-%02d".format(randomYear, randomMonth, randomDay)
    }

    @Test
    fun saveProfileAndObserve_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = AttendanceRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(
                userId = uid,
                fullName = "Alice Mercer",
                email = "alice@test.com",
                photoBase64 = "A".repeat(160)
            )
        }
        assertThat(result.isSuccess).isTrue()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null }
        }
        assertThat(observed?.fullName).isEqualTo("Alice Mercer")
        assertThat(observed?.userId).isEqualTo(uid)
    }

    @Test
    fun checkInOnce_thenDuplicateCheckIn_failsAndCheckOutOnce_thenDuplicateCheckOut_fails() = runBlocking {
        val uid = signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = AttendanceRepository(firestore)
        val dateKey = uniqueDateKey()

        // 1. Check-IN succeeds once
        val checkInResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCheckIn(
                userId = uid,
                userName = "Bob Vance",
                dateKey = dateKey,
                confidence = 94
            )
        }
        assertThat(checkInResult.exceptionOrNull()).isNull()

        // 2. Duplicate Check-IN fails
        val dupCheckIn = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCheckIn(
                userId = uid,
                userName = "Bob Vance",
                dateKey = dateKey,
                confidence = 95
            )
        }
        assertThat(dupCheckIn.isFailure).isTrue()

        // 3. Toggle star succeeds
        val starResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleStarred(uid, dateKey, true)
        }
        assertThat(starResult.exceptionOrNull()).isNull()

        // 4. Check-OUT succeeds once
        val checkOutResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCheckOut(uid, dateKey, 91)
        }
        assertThat(checkOutResult.exceptionOrNull()).isNull()

        // 5. Duplicate Check-OUT fails
        val dupCheckOut = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCheckOut(uid, dateKey, 92)
        }
        assertThat(dupCheckOut.isFailure).isTrue()

        // 6. Verify emitted history contains COMPLETED record
        val history = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAttendanceHistory(uid).first { list -> list.any { it.dateKey == dateKey } }
        }
        val record = history.first { it.dateKey == dateKey }
        assertThat(record.attendanceStatus).isEqualTo("COMPLETED")
        assertThat(record.starred).isTrue()
        assertThat(record.inTime).isNotNull()
        assertThat(record.outTime).isNotNull()
    }

    @Test
    fun checkOutBeforeCheckIn_fails() = runBlocking {
        val uid = signInTestUser("carol_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = AttendanceRepository(firestore)
        val dateKey = uniqueDateKey()

        val checkOutResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCheckOut(uid, dateKey, 90)
        }
        assertThat(checkOutResult.isFailure).isTrue()
    }

    @Test
    fun logVerificationAttempt_authenticatedUser_succeeds() = runBlocking {
        val uid = signInTestUser("dan_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = AttendanceRepository(firestore)

        val logResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logVerificationAttempt(
                userId = uid,
                userName = "Dan Cole",
                dateKey = uniqueDateKey(),
                mode = AttendanceMode.IN,
                faceVerificationStatus = "FAILED",
                attendanceStatus = "FAILED",
                reason = "Face did not match profile.",
                confidence = 32
            )
        }
        assertThat(logResult.isSuccess).isTrue()
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val repository = AttendanceRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(
                userId = aliceUid,
                fullName = "Alice Isolation",
                email = "alice@iso.com",
                photoBase64 = "B".repeat(160)
            ).getOrThrow()
        }

        signInTestUser("bob_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(aliceUid)
        }
        assertThat(crossReadResult.isFailure).isTrue()
        val firestoreException = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertThat(firestoreException?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun observeAttendance_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = AttendanceRepository(firestore)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAttendanceHistory("some_uid_123").first()
            }
            fail("Expected PERMISSION_DENIED exception")
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertThat(firestoreException.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
