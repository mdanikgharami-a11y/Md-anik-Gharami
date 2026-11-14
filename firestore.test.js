const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const DATE_KEY = "2026-11-13";
const VALID_PHOTO = "A".repeat(150);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validServerLikeTime(offsetMs = -2000) {
  return new Date(Date.now() + offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profile or attendance", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = validServerLikeTime();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("attendance").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice",
      photoBase64: VALID_PHOTO,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user can create and read own profile, but cannot access another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = validServerLikeTime();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Johnson",
      email: "alice@example.com",
      photoBase64: VALID_PHOTO,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("User cannot inject ghost field or invalid photo into profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = validServerLikeTime();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Johnson",
      photoBase64: VALID_PHOTO,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Daily attendance: Check-IN succeeds once, duplicate Check-IN fails, Check-OUT succeeds once, duplicate Check-OUT fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc(DATE_KEY);
  const inTs = validServerLikeTime(-5000);
  const outTs = validServerLikeTime(-1000);

  // 1. Check-IN succeeds
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      userName: "Alice Johnson",
      dateKey: DATE_KEY,
      inTime: inTs,
      inConfidence: 95,
      faceVerificationStatus: "VERIFIED",
      attendanceStatus: "CHECKED_IN",
      starred: false,
      createdAt: inTs,
      updatedAt: inTs,
    })
  );

  // 2. Duplicate Check-IN (trying to overwrite inTime) fails
  await assertFails(
    docRef.update({
      inTime: validServerLikeTime(-500),
      updatedAt: validServerLikeTime(-500),
    })
  );

  // 3. Star toggle succeeds without mutating times
  await assertSucceeds(
    docRef.update({
      starred: true,
      updatedAt: validServerLikeTime(-2000),
    })
  );

  // 4. Check-OUT succeeds once
  await assertSucceeds(
    docRef.update({
      outTime: outTs,
      outConfidence: 93,
      faceVerificationStatus: "VERIFIED",
      attendanceStatus: "COMPLETED",
      updatedAt: outTs,
    })
  );

  // 5. Duplicate Check-OUT fails
  await assertFails(
    docRef.update({
      outTime: validServerLikeTime(-200),
      outConfidence: 94,
      faceVerificationStatus: "VERIFIED",
      attendanceStatus: "COMPLETED",
      updatedAt: validServerLikeTime(-200),
    })
  );

  // 6. Delete attendance record fails (permanent storage)
  await assertFails(docRef.delete());
});

test("OUT cannot be completed before IN (cannot create attendance with COMPLETED or outTime)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc(DATE_KEY);
  const now = validServerLikeTime();

  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      userName: "Alice Johnson",
      dateKey: DATE_KEY,
      inTime: now,
      outTime: now,
      inConfidence: 90,
      outConfidence: 90,
      faceVerificationStatus: "VERIFIED",
      attendanceStatus: "COMPLETED",
      starred: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Client cannot set future timestamp or tamper with inTime", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc(DATE_KEY);
  const futureDate = validServerLikeTime(86400000);
  const now = validServerLikeTime();

  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      userName: "Alice Johnson",
      dateKey: DATE_KEY,
      inTime: futureDate,
      inConfidence: 95,
      faceVerificationStatus: "VERIFIED",
      attendanceStatus: "CHECKED_IN",
      starred: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Verification logs are append-only and isolated per user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance_logs").doc("log_1");

  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      userName: "Alice Johnson",
      dateKey: DATE_KEY,
      actionType: "IN",
      faceVerificationStatus: "FAILED",
      attendanceStatus: "FAILED",
      reason: "Face did not match registered profile.",
      confidence: 31,
      timestamp: validServerLikeTime(),
    })
  );

  await assertFails(logRef.update({ attendanceStatus: "SUCCESS" }));
  await assertFails(logRef.delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("attendance_logs").get());
});
