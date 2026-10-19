const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const { evaluateTestAttempt } = require("./examScoring");

/**
 * Cloud Function: submitTestAttemptServerSide
 * Authoritatively validates test answers against server-side official questions,
 * computes score using exam-specific rules (marking schemes, negative penalty, optional section limits,
 * option equivalence), and atomically updates user's cumulative ranking score and public leaderboard.
 * 
 * Idempotent: Keys attempt record by session token or `${testId}_${timestamp}`.
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
  const { testId, answers, timeSpentSeconds, clientTimestamp, attemptSessionToken } = data;

  if (!testId || !answers) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing testId or answers."
    );
  }

  // 2. Fetch test document from Tests_Data or Tests
  let testDoc = await db.collection("Tests_Data").doc(testId).get();
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests").doc(testId).get();
  }
  if (!testDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Test document not found."
    );
  }

  const testData = testDoc.data();

  // 3. Authorization check: reject attempts on draft/unpublished tests unless creator or admin
  const isCallerAdmin = context.auth.token && (
    context.auth.token.admin === true ||
    (context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true)
  );
  const isPublished = testData.status === "APPROVED" || testData.status === "Published" || testData.isPublic === true;
  const isCreator = testData.creatorId === userId || testData.uploaderId === userId;

  if (!isCallerAdmin && !isPublished && !isCreator) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Cannot submit an attempt for an unpublished or restricted test."
    );
  }

  const countsTowardRanking = testData.countsTowardRanking === true && (testData.isOfficial === true || testData.isPublic === true);
  const targetExam = testData.targetExam || testData.subject || "NEET";

  // 4. Parse official questions
  let questions = [];
  if (testData.questionsJson) {
    try {
      questions = JSON.parse(testData.questionsJson);
    } catch (e) {
      questions = [];
    }
  }

  if (!questions || questions.length === 0) {
    const qSnap = await testDoc.ref.collection("questions").get();
    if (!qSnap.empty) {
      questions = qSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    } else {
      const qSnapUpper = await testDoc.ref.collection("Questions").get();
      if (!qSnapUpper.empty) {
        questions = qSnapUpper.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }
    }
  }

  if (questions.length === 0) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Test contains no questions to evaluate."
    );
  }

  // 5. Authoritatively evaluate score on the server
  const evalResult = evaluateTestAttempt(targetExam, questions, answers, timeSpentSeconds || 0);

  const attemptDocId = attemptSessionToken || `${testId}_${clientTimestamp || Date.now()}`;
  const userRef = db.collection("Users_Data").doc(userId);
  const attemptsRef = userRef.collection("Attempts");

  // 6. Atomic, Idempotent Transaction for Competitive Ranking and Persistence
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

    const attemptDocRef = attemptsRef.doc(attemptDocId);
    const existingAttemptSnap = await transaction.get(attemptDocRef);
    const isAlreadyRecorded = existingAttemptSnap.exists;

    const updates = {
      lastActiveAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (!isAlreadyRecorded) {
      updates.totalTestsAttempted = totalTests + 1;
    }

    if (evalResult.finalScore > currentMaxScore) {
      updates.maxScore = Math.round(evalResult.finalScore);
      updates.lastPercentile = evalResult.percentile;
    }

    // Award ranking points only on FIRST valid attempt on QUALIFYING OFFICIAL tests
    if (isFirstAttempt && countsTowardRanking && !isAlreadyRecorded) {
      const currentQCount = userData.qualifyingTestsCount || 0;
      const examKey = `cumulativeScore_${targetExam}`;
      const currentExamScore = userData[examKey] || userData.totalCumulativeScore || 0;
      const currentTotalScore = userData.totalCumulativeScore || 0;

      updates.qualifyingTestsCount = currentQCount + 1;
      updates[examKey] = currentExamScore + Math.round(evalResult.finalScore);
      updates.totalCumulativeScore = currentTotalScore + Math.round(evalResult.finalScore);
    }

    transaction.update(userRef, updates);

    // Persist detailed authoritative attempt record in Attempts collection
    const attemptRecord = {
      userId,
      testId,
      testTitle: testData.title || "",
      targetExam,
      score: Math.round(evalResult.finalScore),
      rawScore: evalResult.rawScore,
      maxScore: Math.round(evalResult.maxScore),
      percentile: evalResult.percentile,
      accuracy: evalResult.accuracyPercent,
      correctCount: evalResult.correctCount,
      incorrectCount: evalResult.wrongCount,
      skippedCount: evalResult.unansweredCount,
      positiveMarksEarned: evalResult.positiveMarksEarned,
      negativeMarksPenalty: evalResult.negativeMarksPenalty,
      timeSpentSeconds: timeSpentSeconds || 0,
      dateTaken: clientTimestamp || Date.now(),
      answersJson: typeof answers === "string" ? answers : JSON.stringify(answers),
      attemptSessionToken: attemptDocId,
      isFirstAttempt,
      countsTowardRanking,
      verifiedServerSide: true,
      authoritativeScore: evalResult.finalScore,
      submittedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    transaction.set(attemptDocRef, attemptRecord, { merge: true });

    // Also mirror to legacy lowercase attempts subcollection for backwards compatibility
    const legacyAttemptRef = userRef.collection("attempts").doc(attemptDocId);
    transaction.set(legacyAttemptRef, attemptRecord, { merge: true });

    // Atomically maintain public Leaderboard without exposing private user data
    const leaderboardRef = db.collection("Leaderboard").doc(`${targetExam}_${userId}`);
    transaction.set(leaderboardRef, {
      userId,
      displayName: userData.name || "Student",
      targetExam,
      score: updates.maxScore || currentMaxScore,
      totalTestsAttempted: updates.totalTestsAttempted || totalTests,
      percentile: evalResult.percentile,
      totalCumulativeScore: updates.totalCumulativeScore || userData.totalCumulativeScore || 0,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      success: true,
      verifiedServerSide: true,
      attemptId: attemptDocId,
      testId,
      targetExam,
      score: Math.round(evalResult.finalScore),
      rawScore: evalResult.rawScore,
      maxScore: Math.round(evalResult.maxScore),
      percentile: evalResult.percentile,
      accuracy: evalResult.accuracyPercent,
      correctCount: evalResult.correctCount,
      incorrectCount: evalResult.wrongCount,
      skippedCount: evalResult.unansweredCount,
      positiveMarksEarned: evalResult.positiveMarksEarned,
      negativeMarksPenalty: evalResult.negativeMarksPenalty,
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
