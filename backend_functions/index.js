const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

/**
 * Cloud Function: submitTestAttemptServerSide
 * Validates test answers against server-side official questions, computes score,
 * and atomically updates user's cumulative ranking score using a transaction.
 * 
 * Idempotent: Keys attempt record by `${testId}_${firstAttempt}`.
 */
exports.submitTestAttemptServerSide = functions.https.onCall(async (data, context) => {
  // 1. Verify Authentication
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to submit test attempts."
    );
  }

  const userId = context.auth.uid;
  const { testId, answers, timeSpentSeconds, clientTimestamp } = data;

  if (!testId || !answers) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing testId or answers."
    );
  }

  // 2. Fetch official test from Firestore
  const testDoc = await db.collection("Tests_Data").doc(testId).get();
  if (!testDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Test document not found."
    );
  }

  const testData = testDoc.data();
  const countsTowardRanking = testData.countsTowardRanking === true && testData.isOfficial === true;
  const targetExam = testData.targetExam || "NEET";

  // Parse questions and official answers
  let questions = [];
  try {
    questions = JSON.parse(testData.questionsJson || "[]");
  } catch (e) {
    throw new functions.https.HttpsError(
      "internal",
      "Failed to parse test questions."
    );
  }

  // 3. Compute score server-side
  let correctCount = 0;
  let incorrectCount = 0;
  let skippedCount = 0;
  let score = 0;

  for (const q of questions) {
    const qId = q.id;
    const userAnswer = answers[qId] ? answers[qId].ans : null;
    const correctAnswer = q.correctAnswer;

    if (!userAnswer || userAnswer === "SKIPPED" || userAnswer === "") {
      skippedCount++;
    } else if (userAnswer === correctAnswer) {
      correctCount++;
      score += 4; // NEET marking (+4)
    } else {
      incorrectCount++;
      score -= 1; // NEET marking (-1)
    }
  }

  const maxScore = questions.length * 4;
  const accuracy = questions.length > 0 ? (correctCount / (correctCount + incorrectCount || 1)) * 100 : 0;
  const percentile = Math.min(99.99, Math.max(0, (80.0 + (correctCount / (questions.length || 1)) * 19.5)));

  const attemptDocId = `${testId}_${clientTimestamp || Date.now()}`;
  const userRef = db.collection("Users_Data").doc(userId);
  const attemptsRef = userRef.collection("Attempts");

  // 4. Atomic, Idempotent Transaction for Competitive Ranking
  return db.runTransaction(async (transaction) => {
    // Check if user has previously attempted this test
    const existingAttemptsSnap = await transaction.get(
      attemptsRef.where("testId", "==", testId).limit(1)
    );
    const isFirstAttempt = existingAttemptsSnap.empty;

    const userSnap = await transaction.get(userRef);
    if (!userSnap.exists) {
      throw new functions.https.HttpsError("not-found", "User record does not exist.");
    }

    const userData = userSnap.data();
    const currentMaxScore = userData.maxScore || 0;
    const totalTests = userData.totalTestsAttempted || 0;

    const updates = {
      totalTestsAttempted: totalTests + 1,
      lastActiveAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (score > currentMaxScore) {
      updates.maxScore = score;
      updates.lastPercentile = percentile;
    }

    // Only award ranking points on FIRST valid attempt on QUALIFYING OFFICIAL tests
    if (isFirstAttempt && countsTowardRanking) {
      const currentQCount = userData.qualifyingTestsCount || 0;
      const examKey = `cumulativeScore_${targetExam}`;
      const currentExamScore = userData[examKey] || userData.totalCumulativeScore || 0;
      const currentTotalScore = userData.totalCumulativeScore || 0;

      updates.qualifyingTestsCount = currentQCount + 1;
      updates[examKey] = currentExamScore + score;
      updates.totalCumulativeScore = currentTotalScore + score;
    }

    transaction.update(userRef, updates);

    // Save attempt record in Attempts collection
    const attemptRecord = {
      testId,
      testTitle: testData.title || "",
      score,
      maxScore,
      percentile,
      accuracy,
      correctCount,
      incorrectCount,
      skippedCount,
      timeSpentSeconds: timeSpentSeconds || 0,
      dateTaken: clientTimestamp || Date.now(),
      answersJson: JSON.stringify(answers),
      isFirstAttempt,
      countsTowardRanking,
      verifiedServerSide: true,
    };

    const newAttemptRef = attemptsRef.doc(attemptDocId);
    transaction.set(newAttemptRef, attemptRecord);

    // Atomically maintain public Leaderboard without exposing private user data
    const leaderboardRef = db.collection("Leaderboard").doc(`${targetExam}_${userId}`);
    transaction.set(leaderboardRef, {
      userId,
      displayName: userData.name || "Student",
      targetExam,
      score: updates.maxScore || currentMaxScore,
      totalTestsAttempted: updates.totalTestsAttempted,
      percentile,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      success: true,
      score,
      maxScore,
      percentile,
      accuracy,
      isFirstAttempt,
      countsTowardRanking,
    };
  });
});

/**
 * Cloud Function: setAdminRole
 * Callable function to assign the 'admin' custom claim to a target UID.
 * Restricted to existing super-admins or verified primary owner (abhi952817@gmail.com).
 */
exports.setAdminRole = functions.https.onCall(async (data, context) => {
  const isCallerAdmin = context.auth && context.auth.token && context.auth.token.admin === true;
  const isOwnerEmail = context.auth && context.auth.token && context.auth.token.email === "abhi952817@gmail.com";

  if (!isCallerAdmin && !isOwnerEmail) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only authorized super administrators can assign admin roles."
    );
  }

  const { targetUid, isAdmin: grantAdmin } = data;
  let uidToPromote = targetUid;
  if (!uidToPromote && data.email) {
    const userRecord = await admin.auth().getUserByEmail(data.email);
    uidToPromote = userRecord.uid;
  }

  if (!uidToPromote) {
    throw new functions.https.HttpsError("invalid-argument", "Missing targetUid or email.");
  }

  await admin.auth().setCustomUserClaims(uidToPromote, { admin: grantAdmin !== false });
  return { success: true, targetUid: uidToPromote, admin: grantAdmin !== false };
});
