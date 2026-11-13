const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// The Android app is configured against this named Firestore database.
// Never let privileged Cloud Functions silently operate on the unrelated (default) database.
const FIRESTORE_DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID || "ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612";
const db = admin.firestore(FIRESTORE_DATABASE_ID);
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
  normalizeExamKey,
} = require("./examScoring");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");
const {
  validateCommunityMcqCreate,
  validateCommunityMcqAttempt,
  splitLegacySharedQuestions
} = require("./communityValidation");
const crypto = require("crypto");

exports.normalizeExamKey = normalizeExamKey;

/**
 * Permanently removes only the retired, hard-coded demo test IDs.
 * This allowlist is intentionally immutable from client input.
 */
exports.purgeRetiredDemoTests = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Admin authentication is required.");
  }
  const isAdmin = Boolean(
    context.auth.token && (
      context.auth.token.admin === true ||
      (context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true)
    )
  );
  if (!isAdmin) {
    throw new functions.https.HttpsError("permission-denied", "Only an authenticated admin can purge retired demo tests.");
  }

  const retiredIds = [
    "official_neet_ug_mock_01",
    "official_jee_mains_mock_01",
    "official_upsc_cds_mock_01",
    "official_ssc_cgl_mock_01",
    "official_banking_ibps_mock_01",
    "official_rrb_ntpc_mock_01",
    "official_ugc_net_paper1_mock_01",
    "official_cat_full_mock_01",
    "t1", "t2", "t3", "t4", "t5"
  ];
  const collectionNames = [
    "Tests_Data",
    "Tests",
    "LiveTests",
    "Shared_Community_Tests"
  ];
  const deletedPaths = [];

  for (const testId of retiredIds) {
    for (const collectionName of collectionNames) {
      const ref = db.collection(collectionName).doc(testId);
      const snapshot = await ref.get();
      if (snapshot.exists) {
        await db.recursiveDelete(ref);
        deletedPaths.push(collectionName + "/" + testId);
      }
    }
    const answerKeyRef = db.collection("TestAnswerKeys").doc(testId);
    const answerKeySnapshot = await answerKeyRef.get();
    if (answerKeySnapshot.exists) {
      await answerKeyRef.delete();
      deletedPaths.push("TestAnswerKeys/" + testId);
    }
  }

  return {
    success: true,
    checkedIds: retiredIds.length,
    deletedCount: deletedPaths.length,
    deletedPaths
  };
});

/**
 * Cloud Function: submitTestAttemptServerSide
 * Authoritatively validates test answers against server-side official questions and protected answer keys,
 * computes score using exam-specific rules (marking schemes, negative penalty, optional section limits,
 * option equivalence), and atomically updates user's cumulative ranking score and public leaderboard.
 * 
 * Strict Idempotency: Keys attempt record by stable logical attemptSessionToken.
 * Rejects conflicting answer payloads on identical attemptSessionToken.
 * Returns previously recorded canonical result on identical retries without double incrementing counters.
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
  const { testId, answers, timeSpentSeconds, clientTimestamp, attemptSessionToken } = data || {};

  // Validate required parameters
  if (!testId || typeof testId !== "string" || testId.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing or invalid testId parameter."
    );
  }

  if (!attemptSessionToken || typeof attemptSessionToken !== "string" || attemptSessionToken.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing or invalid attemptSessionToken. A stable logical attempt identity is required."
    );
  }

  if (!answers || typeof answers !== "object" || Array.isArray(answers)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Answers payload must be a valid key-value object."
    );
  }

  const answerKeysCount = Object.keys(answers).length;
  if (answerKeysCount > 1000) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Answer payload exceeds maximum permitted questions limit."
    );
  }

  const cleanTimeSpent = Number(timeSpentSeconds);
  if (isNaN(cleanTimeSpent) || cleanTimeSpent < 0 || cleanTimeSpent > 86400) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Invalid timeSpentSeconds: must be non-negative and less than 24 hours."
    );
  }

  // 1b. Verify Authoritative Account Status & Ban Enforcement (Fail closed)
  const userRef = db.collection("Users_Data").doc(userId);
  let userSnap;
  try {
    userSnap = await userRef.get();
  } catch (err) {
    throw new functions.https.HttpsError(
      "internal",
      "Failed to verify account status. Please try again."
    );
  }

  if (!userSnap.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "User record does not exist."
    );
  }

  const initialUserData = userSnap.data();
  try {
    assertActiveUserStatus(initialUserData, userId);
  } catch (err) {
    throw new functions.https.HttpsError(
      err.code || "permission-denied",
      err.message
    );
  }

  // 2. Fetch test document from Tests_Data or Tests
  const cleanTestId = testId.trim();
  let testDoc = await db.collection("Tests_Data").doc(cleanTestId).get();
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("LiveTests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("Shared_Community_Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Test document not found."
    );
  }

  const testData = testDoc.data();
  if (testDoc.ref.parent.id === "Shared_Community_Tests" &&
      (testData.answerKeySeparated !== true || testData.sharingState !== "COMMUNITY_SHARED")) {
    throw new functions.https.HttpsError("failed-precondition", "Community test has not passed answer-key separation.");
  }
  if (testDoc.ref.parent.id === "LiveTests" &&
      (testData.processingStatus !== "Ready" || testData.questionsVerified !== true)
  ) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Live test questions have not completed Gemini verification."
    );
  }

  // 3. Authorization check & explicit publication policy
  const isCallerAdmin = Boolean(
    context.auth.token && (
      context.auth.token.admin === true ||
      (context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true)
    )
  );
  const isCreator = (testData.creatorId && testData.creatorId === userId) ||
                    (testData.uploaderId && testData.uploaderId === userId);

  const testStatus = String(testData.status || "").trim();
  const isApprovedOrPublished = testStatus === "APPROVED" || testStatus === "Published";
  const isExplicitlyRestricted = testStatus === "Under Review" || testStatus === "REJECTED" || testStatus === "DRAFT";
  const hasPublicFlag = testData.isPublic === true || testData.isOfficial === true;

  // A public flag alone MUST NOT allow a pending or rejected test to be treated as approved
  const isPubliclyAvailable = isApprovedOrPublished && !isExplicitlyRestricted && hasPublicFlag;

  if (!isCallerAdmin && !isCreator && !isPubliclyAvailable) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Cannot submit an attempt for an unpublished, pending moderation, or restricted test."
    );
  }

  const countsTowardRanking = testData.countsTowardRanking === true && (testData.isOfficial === true || testData.isPublic === true);
  const rawExamIdentity = testData.examId || testData.targetExam;
  const canonicalExam = normalizeExamKey(rawExamIdentity);
  if (canonicalExam === "UNKNOWN") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Test has no valid canonical exam identity. Scoring and ranking were not calculated."
    );
  }

  // 4. Load questions and trusted answer keys (Protected Separation)
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

  // Check for server-protected answer keys document (denied to normal clients by Firestore rules)
  let privateKeyDoc = await testDoc.ref.collection("private_keys").doc("answerKey").get();
  if (!privateKeyDoc.exists) {
    privateKeyDoc = await db.collection("TestAnswerKeys").doc(cleanTestId).get();
  }

  const privateKeyData = privateKeyDoc.exists ? privateKeyDoc.data() : null;
  if (testDoc.ref.parent.id === "Shared_Community_Tests") {
    const keyList = privateKeyData?.questions || privateKeyData?.keys || [];
    const keyIds = new Set(keyList.map(item => item && item.id ? String(item.id) : "").filter(Boolean));
    if (!privateKeyDoc.exists || questions.some(question => !keyIds.has(String(question.id)))) {
      throw new functions.https.HttpsError("failed-precondition", "Community test is missing one or more private grading keys.");
    }
  }
  let solutionKeys = {};
  try {
    const validated = validateAndMergeAnswerKeys(testData, questions, privateKeyData);
    questions = validated.questions;
    solutionKeys = validated.solutionKeys;
  } catch (err) {
    throw new functions.https.HttpsError(err.code || "failed-precondition", err.message);
  }

  if (questions.length === 0) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Test contains no questions to evaluate."
    );
  }

  // Validate question IDs and payload format
  const validQuestionIds = new Set(questions.map(q => String(q.id)));
  for (const qId of Object.keys(answers)) {
    if (!validQuestionIds.has(String(qId))) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        `Unexpected question identifier "${qId}" not present in official test questions.`
      );
    }
    const val = answers[qId];
    const opt = typeof val === "object" && val !== null ? val.ans : val;
    if (typeof opt === "string" && opt.length > 100) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Answer option exceeds allowable length limit."
      );
    }
  }

  // 5. Authoritatively evaluate score on the server
  const evalResult = evaluateTestAttempt(canonicalExam, questions, answers, cleanTimeSpent);

  const attemptDocId = attemptSessionToken.trim();
  const attemptsRef = userRef.collection("Attempts");

  // 5b. Compute population-based percentile on this test if it qualifies for competitive ranking
  let testTotalCandidates = 0;
  let testHigherCount = 0;
  let testRank = 0;
  let attemptPercentile = 0.0;

  if (countsTowardRanking) {
    try {
      const testEntriesRef = db.collection("Test_Leaderboards").doc(cleanTestId).collection("Entries");
      const higherSnap = await testEntriesRef.where("score", ">", evalResult.finalScore).count().get();
      testHigherCount = higherSnap.data().count;

      const totalSnap = await testEntriesRef.count().get();
      const existingCount = totalSnap.data().count;
      testRank = testHigherCount + 1;
      testTotalCandidates = Math.max(1, existingCount + 1);
      attemptPercentile = calculatePopulationPercentile(testRank, testTotalCandidates);
    } catch (e) {
      testRank = 0;
      testTotalCandidates = 0;
      attemptPercentile = 0.0;
    }
  }

  // 6. Atomic, Idempotent Transaction for Competitive Ranking and Persistence
  return db.runTransaction(async (transaction) => {
    const attemptDocRef = attemptsRef.doc(attemptDocId);
    const existingAttemptSnap = await transaction.get(attemptDocRef);

    // Idempotency check: handle identical retries vs conflicting reuse
    if (existingAttemptSnap.exists) {
      const existing = existingAttemptSnap.data();
      if (existing.userId !== userId || existing.testId !== cleanTestId) {
        throw new functions.https.HttpsError(
          "already-exists",
          "Attempt ID belongs to a different test or user."
        );
      }

      // Check whether payload matches previous submission
      const currentAnswersStr = typeof answers === "string" ? answers : JSON.stringify(answers);
      const storedAnswersStr = typeof existing.answersJson === "string" ? existing.answersJson : JSON.stringify(existing.answersJson);

      if (currentAnswersStr === storedAnswersStr) {
        // Return existing canonical result without re-incrementing counters
        return {
          success: true,
          verifiedServerSide: true,
          attemptId: attemptDocId,
          testId: cleanTestId,
          targetExam: canonicalExam,
          score: existing.score,
          rawScore: existing.rawScore || existing.score,
          maxScore: existing.maxScore,
          percentile: existing.percentile || 0.0,
          testRank: existing.testRank || 1,
          testTotalCandidates: existing.testTotalCandidates || 1,
          accuracy: existing.accuracy,
          correctCount: existing.correctCount,
          incorrectCount: existing.incorrectCount,
          skippedCount: existing.skippedCount,
          positiveMarksEarned: existing.positiveMarksEarned || 0,
          negativeMarksPenalty: existing.negativeMarksPenalty || 0,
          isFirstAttempt: existing.isFirstAttempt || false,
          countsTowardRanking: existing.countsTowardRanking || false,
          isRetry: true
        };
      } else {
        throw new functions.https.HttpsError(
          "already-exists",
          "Conflicting attempt submission with different answers for existing attempt ID."
        );
      }
    }

    // Check if user has previously attempted this test
    const existingAttemptsSnap = await transaction.get(
      attemptsRef.where("testId", "==", cleanTestId).limit(1)
    );
    const isFirstAttempt = existingAttemptsSnap.empty;

    const userSnap = await transaction.get(userRef);
    if (!userSnap.exists) {
      throw new functions.https.HttpsError("not-found", "User record does not exist.");
    }

    const userData = userSnap.data();
    try {
      assertActiveUserStatus(userData, userId);
    } catch (err) {
      throw new functions.https.HttpsError(
        err.code || "permission-denied",
        err.message
      );
    }
    const currentMaxScore = userData.maxScore || 0;
    const totalTests = userData.totalTestsAttempted || 0;

    const updates = {
      lastActiveAt: admin.firestore.FieldValue.serverTimestamp(),
      totalTestsAttempted: totalTests + 1,
    };

    if (evalResult.finalScore > currentMaxScore) {
      updates.maxScore = Math.round(evalResult.finalScore);
    }

    // Award ranking points only on FIRST valid attempt on QUALIFYING OFFICIAL tests
    const examScoreKey = `cumulativeScore_${canonicalExam}`;
    const examQCountKey = `qualifyingTestsCount_${canonicalExam}`;
    const currentExamScore = userData[examScoreKey] || 0;
    const currentExamQCount = userData[examQCountKey] || 0;
    const currentTotalScore = userData.totalCumulativeScore || 0;
    let newExamScore = currentExamScore;

    // Full Test vs Part Test weighting for cumulative ranking:
    // Full Syllabus mock tests = 1.0x; Chapterwise / Topic / Part syllabus tests = 0.2x
    const testCategory = String(testData.category || testData.testType || "").toLowerCase();
    const isPartTest = testData.isPartTest === true ||
      testCategory.includes("chapter") ||
      testCategory.includes("topic") ||
      testCategory.includes("subject") ||
      testCategory.includes("part");
    
    const rankingWeight = typeof testData.rankingWeight === "number"
      ? testData.rankingWeight
      : (isPartTest ? 0.2 : 1.0);
    
    const weightedScore = Math.round(evalResult.finalScore * rankingWeight);

    if (isFirstAttempt && countsTowardRanking) {
      newExamScore = currentExamScore + weightedScore;
      updates[examQCountKey] = currentExamQCount + 1;
      updates.qualifyingTestsCount = (userData.qualifyingTestsCount || 0) + 1;
      updates[examScoreKey] = newExamScore;
      updates.totalCumulativeScore = currentTotalScore + weightedScore;
    }

    transaction.update(userRef, updates);

    const qualifyingTests = updates[examQCountKey] !== undefined
      ? updates[examQCountKey]
      : currentExamQCount;

    const isEligible = qualifyingTests >= 2;
    const competitiveScore = updates[examScoreKey] !== undefined ? updates[examScoreKey] : currentExamScore;

    // Persist detailed authoritative attempt record in Attempts collection
    const attemptRecord = {
      userId,
      testId: cleanTestId,
      testTitle: testData.title || "",
      targetExam: canonicalExam,
      score: Math.round(evalResult.finalScore),
      rawScore: evalResult.rawScore,
      maxScore: Math.round(evalResult.maxScore),
      percentile: attemptPercentile, // Real population-based percentile calculated against candidate cohort
      testRank: testRank,
      testTotalCandidates: testTotalCandidates,
      accuracy: evalResult.accuracyPercent,
      correctCount: evalResult.correctCount,
      incorrectCount: evalResult.wrongCount,
      skippedCount: evalResult.unansweredCount,
      positiveMarksEarned: evalResult.positiveMarksEarned,
      negativeMarksPenalty: evalResult.negativeMarksPenalty,
      timeSpentSeconds: cleanTimeSpent,
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

    // Mirror to legacy lowercase attempts subcollection for backwards compatibility
    const legacyAttemptRef = userRef.collection("attempts").doc(attemptDocId);
    transaction.set(legacyAttemptRef, attemptRecord, { merge: true });

    // Atomically maintain per-test leaderboard entry for qualifying first attempts
    if (countsTowardRanking && isFirstAttempt) {
      const testEntryRef = db.collection("Test_Leaderboards").doc(cleanTestId).collection("Entries").doc(userId);
      transaction.set(testEntryRef, {
        userId,
        displayName: userData.name || "Student",
        score: Math.round(evalResult.finalScore),
        rawScore: evalResult.rawScore,
        accuracy: evalResult.accuracyPercent,
        submittedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }

    // Atomically maintain public Leaderboard using the consistent competitive score
    // Only write to competitive Leaderboard if the attempt counts toward ranking or user is already ranked
    if (countsTowardRanking && isFirstAttempt) {
      const leaderboardRef = db.collection("Leaderboard").doc(`${canonicalExam}_${userId}`);
      transaction.set(leaderboardRef, {
        userId,
        displayName: userData.name || "Student",
        targetExam: canonicalExam,
        score: competitiveScore, // Consistent: ranking score is the cumulative competitive score!
        maxSingleScore: updates.maxScore || currentMaxScore,
        totalTestsAttempted: updates.totalTestsAttempted || totalTests + 1,
        qualifyingTestsCount: qualifyingTests,
        isEligibleForRanking: isEligible,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    } else if (userData[examQCountKey] && userData[examQCountKey] >= 2) {
      // User is already an eligible ranked participant for this canonicalExam; keep test count updated without altering score
      const leaderboardRef = db.collection("Leaderboard").doc(`${canonicalExam}_${userId}`);
      transaction.set(leaderboardRef, {
        totalTestsAttempted: updates.totalTestsAttempted || totalTests + 1,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }

    return {
      success: true,
      verifiedServerSide: true,
      attemptId: attemptDocId,
      testId: cleanTestId,
      targetExam: canonicalExam,
      score: Math.round(evalResult.finalScore),
      rawScore: evalResult.rawScore,
      maxScore: Math.round(evalResult.maxScore),
      percentile: attemptPercentile,
      testRank,
      testTotalCandidates,
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
 * Cloud Function: getUserRankingDetails
 * Returns authoritative, population-based competition rank, exact percentile,
 * eligibility status, and comparative target information for the authenticated student.
 */
exports.getUserRankingDetails = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to retrieve ranking details."
    );
  }

  const userId = context.auth.uid;
  const { targetExam } = data || {};
  const canonicalExam = normalizeExamKey(targetExam || "NEET");

  const userDoc = await db.collection("Users_Data").doc(userId).get();
  if (!userDoc.exists) {
    return {
      isEligible: false,
      status: "Rank Pending",
      rank: 0,
      rankString: "Pending",
      percentile: 0.0,
      competitiveScore: 0,
      qualifyingTestsCount: 0,
      minQualifyingTests: 2,
      totalEligible: 0,
      isOfficialAir: false,
      message: "Complete 2 qualifying tests to unlock your provisional ranking!",
      targetExam: canonicalExam
    };
  }

  const userData = userDoc.data();
  try {
    assertActiveUserStatus(userData, userId);
  } catch (err) {
    throw new functions.https.HttpsError(
      err.code || "permission-denied",
      err.message
    );
  }

  const eligibility = getExamEligibility(userData, canonicalExam);
  const qualifyingCount = eligibility.qualifyingCount;
  const competitiveScore = eligibility.competitiveScore;

  // Query eligible participant population for this specific exam
  const eligibleQuery = db.collection("Leaderboard")
    .where("targetExam", "==", canonicalExam)
    .where("isEligibleForRanking", "==", true);

  const eligibleSnap = await eligibleQuery.count().get();
  const totalEligible = eligibleSnap.data().count;

  if (qualifyingCount < 2) {
    return {
      isEligible: false,
      status: "Rank Pending",
      rank: 0,
      rankString: "Pending",
      percentile: 0.0,
      competitiveScore,
      qualifyingTestsCount: qualifyingCount,
      minQualifyingTests: 2,
      totalEligible,
      isOfficialAir: false,
      message: `Complete ${2 - qualifyingCount} more qualifying test(s) to unlock your ranking!`,
      targetExam: canonicalExam
    };
  }

  // Count candidates strictly above this candidate's score
  const higherQuery = db.collection("Leaderboard")
    .where("targetExam", "==", canonicalExam)
    .where("isEligibleForRanking", "==", true)
    .where("score", ">", competitiveScore);

  const higherSnap = await higherQuery.count().get();
  const higherCount = higherSnap.data().count;
  const rank = higherCount + 1;

  // Standard Competition Percentile Formula:
  // P = ((N - rank + 1) / N) * 100
  const percentile = calculatePopulationPercentile(rank, totalEligible);

  const isOfficialAir = false; // Platform rankings are never disguised as official national AIR
  const status = totalEligible >= 50 ? "Platform Rank" : "Provisional Rank";

  // Next target motivation
  let nextTarget = null;
  if (rank > 1) {
    const higherDocsSnap = await db.collection("Leaderboard")
      .where("targetExam", "==", canonicalExam)
      .where("isEligibleForRanking", "==", true)
      .where("score", ">", competitiveScore)
      .orderBy("score", "asc")
      .limit(1)
      .get();
    if (!higherDocsSnap.empty) {
      const targetDoc = higherDocsSnap.docs[0].data();
      const targetScore = targetDoc.score || 0;
      const ptsAway = (targetScore - competitiveScore) + 1;
      const name = targetDoc.displayName || "Student";
      nextTarget = { name, rank: rank - 1, ptsAway };
    }
  }

  const message = rank === 1
    ? `🎉 Congratulations! You are Rank #1 on the ${canonicalExam} Leaderboard!`
    : nextTarget
      ? `Next target: ${nextTarget.name} (#${nextTarget.rank}) — You are ${nextTarget.ptsAway} pts away! Take 1 test to overtake.`
      : `Keep practicing to climb higher on the ${canonicalExam} leaderboard!`;

  return {
    isEligible: true,
    status,
    isOfficialAir,
    rank,
    rankString: `#${rank}`,
    percentile,
    competitiveScore,
    qualifyingTestsCount: qualifyingCount,
    totalEligible,
    nextTarget,
    message,
    targetExam: canonicalExam
  };
});

/**
 * Cloud Function: setAdminRole
 * Callable function to assign the 'admin' custom claim to a target UID.
 * Restricted to existing super-admins or verified primary owner (abhi952817@gmail.com).
 */
exports.setAdminRole = functions.https.onCall(async (data, context) => {
  try {
    validateSetAdminRoleAuth(context);
  } catch (err) {
    throw new functions.https.HttpsError(
      err.code || "permission-denied",
      err.message
    );
  }

  const { targetUid, isAdmin: grantAdmin } = data || {};
  let uidToPromote = targetUid;
  if (!uidToPromote && data && data.email) {
    const userRecord = await admin.auth().getUserByEmail(data.email);
    uidToPromote = userRecord.uid;
  }

  if (!uidToPromote) {
    throw new functions.https.HttpsError("invalid-argument", "Missing targetUid or email.");
  }

  await admin.auth().setCustomUserClaims(uidToPromote, { admin: grantAdmin !== false });
  return { success: true, targetUid: uidToPromote, admin: grantAdmin !== false };
});

/**
 * Cloud Function: getTestAttemptSolutions (Remediation F-016)
 * Dedicated, authoritative solution retrieval flow.
 * Solutions are strictly gated:
 * 1. User must be authenticated and active (not banned).
 * 2. User must have an existing, completed attempt record for this test (verified on server).
 * 3. Cannot be accessed across different users or arbitrary attempt IDs.
 * 4. Test must not explicitly disallow solution review.
 * 5. Live test solutions are held until the live exam window ends.
 */
exports.getTestAttemptSolutions = functions.https.onCall(async (data, context) => {
  // 1. Verify Authentication
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to retrieve test solutions."
    );
  }

  const userId = context.auth.uid;
  const { testId, attemptSessionToken } = data || {};

  // 2. Validate Parameters
  if (!testId || typeof testId !== "string" || testId.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing or invalid testId parameter."
    );
  }

  if (!attemptSessionToken || typeof attemptSessionToken !== "string" || attemptSessionToken.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing or invalid attemptSessionToken parameter."
    );
  }

  const cleanTestId = testId.trim();
  const cleanAttemptId = attemptSessionToken.trim();

  // 3. Verify Authoritative Account Status & Ban Enforcement
  const userRef = db.collection("Users_Data").doc(userId);
  let userSnap;
  try {
    userSnap = await userRef.get();
  } catch (err) {
    throw new functions.https.HttpsError(
      "internal",
      "Failed to verify account status. Please try again."
    );
  }

  if (!userSnap.exists) {
    throw new functions.https.HttpsError("not-found", "User record does not exist.");
  }

  const userData = userSnap.data();
  try {
    assertActiveUserStatus(userData, userId);
  } catch (err) {
    throw new functions.https.HttpsError(
      err.code || "permission-denied",
      err.message
    );
  }

  // 4. Verify Attempt Ownership and Completion Status (Fail-Closed)
  let attemptSnap = await userRef.collection("Attempts").doc(cleanAttemptId).get();
  if (!attemptSnap.exists) {
    attemptSnap = await userRef.collection("attempts").doc(cleanAttemptId).get();
  }

  if (!attemptSnap.exists) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Solutions can only be accessed after completing and submitting the test attempt."
    );
  }

  const attemptData = attemptSnap.data();
  if (attemptData.userId !== userId || attemptData.testId !== cleanTestId) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Attempt does not match authenticated user or requested test."
    );
  }

  const isCompleted = attemptData.verifiedServerSide === true ||
                      attemptData.submissionStatus === "CONFIRMED" ||
                      attemptData.submissionStatus === "COMPLETED" ||
                      Boolean(attemptData.submittedAt);

  if (!isCompleted) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Test attempt is not completed. You must complete and submit the test before retrieving solutions."
    );
  }

  // 5. Verify Test Document and Review Permissions
  let testDoc = await db.collection("Tests_Data").doc(cleanTestId).get();
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("Shared_Community_Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("LiveTests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    throw new functions.https.HttpsError("not-found", "Test document not found.");
  }

  const testData = testDoc.data();
  if (testDoc.ref.parent.id === "Shared_Community_Tests" &&
      (testData.answerKeySeparated !== true || testData.sharingState !== "COMMUNITY_SHARED")) {
    throw new functions.https.HttpsError("failed-precondition", "Community test has not passed answer-key separation.");
  }
  const isCallerAdmin = Boolean(
    context.auth.token && (
      context.auth.token.admin === true ||
      (context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true)
    )
  );

  // Check explicit review disallowance
  if (!isCallerAdmin && testData.allowSolutionsReview === false) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Detailed solutions review has been disabled for this examination."
    );
  }

  // Check live test window
  if (!isCallerAdmin && (testData.isLive === true || testData.liveEndTime)) {
    const now = Date.now();
    const liveEndTime = typeof testData.liveEndTime === "number"
      ? testData.liveEndTime
      : (testData.liveEndTime?.toMillis ? testData.liveEndTime.toMillis() : 0);
    if (liveEndTime > 0 && now < liveEndTime) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Solutions for live examinations are locked until the live exam window concludes."
      );
    }
  }

  // 6. Securely Load Questions and Private Solution Keys
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

  let privateKeyDoc = await testDoc.ref.collection("private_keys").doc("answerKey").get();
  if (!privateKeyDoc.exists) {
    privateKeyDoc = await db.collection("TestAnswerKeys").doc(cleanTestId).get();
  }

  const privateKeyData = privateKeyDoc.exists ? privateKeyDoc.data() : null;
  if (testDoc.ref.parent.id === "Shared_Community_Tests") {
    const keyList = privateKeyData?.questions || privateKeyData?.keys || [];
    const keyIds = new Set(keyList.map(item => item && item.id ? String(item.id) : "").filter(Boolean));
    if (!privateKeyDoc.exists || questions.some(question => !keyIds.has(String(question.id)))) {
      throw new functions.https.HttpsError("failed-precondition", "Community test is missing one or more private solution keys.");
    }
  }
  let solutionKeys = {};
  try {
    const validated = validateAndMergeAnswerKeys(testData, questions, privateKeyData);
    solutionKeys = validated.solutionKeys;
  } catch (err) {
    throw new functions.https.HttpsError(err.code || "failed-precondition", err.message);
  }

  return {
    success: true,
    testId: cleanTestId,
    attemptSessionToken: cleanAttemptId,
    solutionKeys,
  };
});



/**
 * Creates a one-question Community MCQ. The public post contains options only;
 * the correct option is stored in a separate server-only document.
 */
exports.createCommunityMcq = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to create a Community MCQ.");
  }
  const uid = context.auth.uid;
  let payload;
  try {
    payload = validateCommunityMcqCreate(data);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "invalid-argument", error.message);
  }

  const userRef = db.collection("Users_Data").doc(uid);
  const userSnap = await userRef.get();
  try {
    assertActiveUserStatus(userSnap.exists ? userSnap.data() : null, uid);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "permission-denied", error.message);
  }

  const requestDigest = crypto.createHash("sha256").update(JSON.stringify({
    title: payload.title,
    question: payload.question,
    subject: payload.subject,
    targetExam: payload.targetExam,
    options: payload.options,
    correctOptionIndex: payload.correctOptionIndex,
    imageUrl: payload.imageUrl
  })).digest("hex");
  const postId = crypto.createHash("sha256").update(uid + ":" + payload.requestId).digest("hex").slice(0, 40);
  const postRef = db.collection("Community_Posts").doc(postId);
  const keyRef = db.collection("Community_MCQ_AnswerKeys").doc(postId);

  const existing = await postRef.get();
  if (existing.exists) {
    const existingKey = await keyRef.get();
    if (existing.data().creatorId !== uid || !existingKey.exists || existingKey.data().requestDigest !== requestDigest) {
      throw new functions.https.HttpsError("already-exists", "This request ID was already used with different content.");
    }
    return { success: true, postId, duplicate: true };
  }

  const authorName = String(userSnap.data().name || userSnap.data().displayName || context.auth.token.name || "Student").slice(0, 80);
  const now = Date.now();
  const batch = db.batch();
  batch.create(postRef, {
    id: postId,
    authorName,
    authorSubtitle: payload.targetExam + " Aspirant",
    title: payload.title,
    content: payload.question,
    subject: payload.subject,
    targetExam: payload.targetExam,
    imageUrl: payload.imageUrl,
    postType: "MCQ",
    mcqOptions: payload.options,
    creatorId: uid,
    isOfficial: false,
    isMonitor: false,
    isPinned: false,
    upvotes: 0,
    comments: 0,
    views: "0",
    timestamp: now,
    moderationState: "VISIBLE"
  });
  batch.create(keyRef, {
    creatorId: uid,
    correctOptionIndex: payload.correctOptionIndex,
    requestDigest,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  await batch.commit();
  return { success: true, postId, duplicate: false };
});

/**
 * Records one canonical attempt per student and reveals the correct option only
 * after the attempt record is committed in the same Firestore transaction.
 */
exports.submitCommunityMcqAttempt = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to attempt this MCQ.");
  }
  const uid = context.auth.uid;
  let payload;
  try {
    payload = validateCommunityMcqAttempt(data);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "invalid-argument", error.message);
  }

  const userRef = db.collection("Users_Data").doc(uid);
  const postRef = db.collection("Community_Posts").doc(payload.postId);
  const keyRef = db.collection("Community_MCQ_AnswerKeys").doc(payload.postId);
  const attemptRef = db.collection("Community_MCQ_Attempts").doc(payload.postId).collection("Users").doc(uid);

  try {
    const result = await db.runTransaction(async transaction => {
      const userSnap = await transaction.get(userRef);
      assertActiveUserStatus(userSnap.exists ? userSnap.data() : null, uid);

      const postSnap = await transaction.get(postRef);
      const keySnap = await transaction.get(keyRef);
      const attemptSnap = await transaction.get(attemptRef);

      if (!postSnap.exists || postSnap.data().postType !== "MCQ" || postSnap.data().moderationState === "HIDDEN") {
        throw new functions.https.HttpsError("not-found", "Community MCQ is unavailable.");
      }
      const options = postSnap.data().mcqOptions;
      if (!Array.isArray(options) || ![4, 5].includes(options.length) ||
          payload.optionIndex < 0 || payload.optionIndex >= options.length) {
        throw new functions.https.HttpsError("invalid-argument", "Selected option is outside the MCQ option range.");
      }
      if (!keySnap.exists) {
        throw new functions.https.HttpsError("failed-precondition", "MCQ answer key is unavailable.");
      }

      if (attemptSnap.exists) {
        const prior = attemptSnap.data();
        return {
          selectedOptionIndex: prior.selectedOptionIndex,
          correctOptionIndex: prior.correctOptionIndex,
          isCorrect: prior.isCorrect,
          duplicate: true
        };
      }

      const correctOptionIndex = keySnap.data().correctOptionIndex;
      if (!Number.isInteger(correctOptionIndex) || correctOptionIndex < 0 || correctOptionIndex >= options.length) {
        throw new functions.https.HttpsError("failed-precondition", "MCQ answer key is invalid.");
      }
      const attempt = {
        userId: uid,
        postId: payload.postId,
        selectedOptionIndex: payload.optionIndex,
        correctOptionIndex,
        isCorrect: payload.optionIndex === correctOptionIndex,
        attemptedAt: admin.firestore.FieldValue.serverTimestamp()
      };
      transaction.create(attemptRef, attempt);
      return {
        selectedOptionIndex: attempt.selectedOptionIndex,
        correctOptionIndex: attempt.correctOptionIndex,
        isCorrect: attempt.isCorrect,
        duplicate: false
      };
    });
    return { success: true, ...result };
  } catch (error) {
    if (error instanceof functions.https.HttpsError) throw error;
    console.error("submitCommunityMcqAttempt failed", error);
    throw new functions.https.HttpsError("internal", "MCQ attempt could not be recorded.");
  }
});


/**
 * Admin-only, paged migration for legacy Community shared tests that stored
 * correct answers/explanations inside broadly readable question documents.
 * Dry-run is the default. A write run requires an explicit confirmation token.
 */
exports.migrateLegacyCommunitySharedTests = functions.https.onCall(async (data, context) => {
  try {
    validateSetAdminRoleAuth(context);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "permission-denied", error.message);
  }

  const dryRun = !data || data.dryRun !== false;
  if (!dryRun && data.confirm !== "MIGRATE_SHARED_COMMUNITY_TESTS") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "A write migration requires confirm=MIGRATE_SHARED_COMMUNITY_TESTS after backup and dry-run review."
    );
  }
  const pageSize = Math.max(1, Math.min(Number.isInteger(data?.pageSize) ? data.pageSize : 5, 10));
  const cursor = typeof data?.startAfter === "string" ? data.startAfter.trim() : "";
  let query = db.collection("Shared_Community_Tests")
    .orderBy(admin.firestore.FieldPath.documentId())
    .limit(pageSize);
  if (cursor) query = query.startAfter(cursor);

  const snapshot = await query.get();
  const results = [];
  let migratedCount = 0;
  let skippedCount = 0;

  for (const doc of snapshot.docs) {
    const existing = doc.data();
    if (existing.answerKeySeparated === true && existing.sharingState === "COMMUNITY_SHARED") {
      results.push({ testId: doc.id, status: "ALREADY_SAFE" });
      skippedCount += 1;
      continue;
    }

    let rawQuestions = Array.isArray(existing.questions) ? existing.questions : null;
    if (!rawQuestions && typeof existing.questionsJson === "string" && existing.questionsJson.trim()) {
      try {
        rawQuestions = JSON.parse(existing.questionsJson);
      } catch (_) {
        rawQuestions = null;
      }
    }

    let separated;
    try {
      separated = splitLegacySharedQuestions(rawQuestions, doc.id);
    } catch (error) {
      results.push({ testId: doc.id, status: "SKIPPED_UNSAFE", reason: error.message });
      skippedCount += 1;
      continue;
    }

    if (!dryRun) {
      const batch = db.batch();
      batch.update(doc.ref, {
        questions: admin.firestore.FieldValue.delete(),
        questionsJson: admin.firestore.FieldValue.delete(),
        answerKeySeparated: true,
        sharingState: "COMMUNITY_SHARED",
        isPublic: true,
        isOfficial: false,
        countsTowardRanking: false,
        status: "Published",
        migratedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      for (const question of separated.publicQuestions) {
        batch.set(doc.ref.collection("questions").doc(question.id), question);
      }
      batch.set(doc.ref.collection("private_keys").doc("answerKey"), {
        testId: doc.id,
        questions: separated.privateKeys,
        migratedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      batch.set(db.collection("Community_Moderation_Audit").doc(), {
        actorUid: context.auth.uid,
        action: "MIGRATE_SHARED_TEST_ANSWER_KEYS",
        targetType: "SHARED_COMMUNITY_TEST",
        targetId: doc.id,
        reason: "Separated legacy answer keys from public Community question documents.",
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await batch.commit();
      migratedCount += 1;
    } else {
      migratedCount += 1;
    }

    results.push({
      testId: doc.id,
      status: dryRun ? "WOULD_MIGRATE" : "MIGRATED",
      questionCount: separated.publicQuestions.length
    });
  }

  return {
    success: true,
    dryRun,
    scannedCount: snapshot.size,
    migratedOrReadyCount: migratedCount,
    skippedCount,
    results,
    nextCursor: snapshot.size === pageSize ? snapshot.docs[snapshot.docs.length - 1].id : null
  };
});


/**
 * Admin-only, paged migration that marks pre-moderation Community posts visible.
 * Hidden/removed posts are never overwritten. Dry-run is the default.
 */
exports.migrateLegacyCommunityPostVisibility = functions.https.onCall(async (data, context) => {
  try {
    validateSetAdminRoleAuth(context);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "permission-denied", error.message);
  }

  const dryRun = !data || data.dryRun !== false;
  if (!dryRun && data.confirm !== "MIGRATE_COMMUNITY_POST_VISIBILITY") {
    throw new functions.https.HttpsError("failed-precondition", "A write migration requires explicit confirmation after reviewing the dry run.");
  }
  const pageSize = Math.max(1, Math.min(Number.isInteger(data?.pageSize) ? data.pageSize : 50, 100));
  const cursor = typeof data?.startAfter === "string" ? data.startAfter.trim() : "";
  let query = db.collection("Community_Posts")
    .orderBy(admin.firestore.FieldPath.documentId())
    .limit(pageSize);
  if (cursor) query = query.startAfter(cursor);

  const snapshot = await query.get();
  const results = [];
  let readyCount = 0;
  let skippedCount = 0;

  for (const doc of snapshot.docs) {
    const state = doc.get("moderationState");
    if (typeof state === "string" && state.length > 0) {
      results.push({ postId: doc.id, status: state === "VISIBLE" ? "ALREADY_VISIBLE" : "PRESERVED_" + state });
      skippedCount += 1;
      continue;
    }

    if (!dryRun) {
      const batch = db.batch();
      batch.update(doc.ref, { moderationState: "VISIBLE" });
      batch.set(db.collection("Community_Moderation_Audit").doc(), {
        actorUid: context.auth.uid,
        action: "MIGRATE_POST_VISIBILITY",
        targetType: "COMMUNITY_POST",
        targetId: doc.id,
        reason: "Set the legacy post visibility state to VISIBLE; no content was changed.",
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      await batch.commit();
    }
    readyCount += 1;
    results.push({ postId: doc.id, status: dryRun ? "WOULD_SET_VISIBLE" : "SET_VISIBLE" });
  }

  return {
    success: true,
    dryRun,
    scannedCount: snapshot.size,
    readyOrUpdatedCount: readyCount,
    skippedCount,
    results,
    nextCursor: snapshot.size === pageSize ? snapshot.docs[snapshot.docs.length - 1].id : null
  };
});
