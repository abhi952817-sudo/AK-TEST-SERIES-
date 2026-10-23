const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
} = require("./examScoring");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");

/**
 * Normalizes exam titles to canonical group identifiers to guarantee
 * that rankings and percentiles compare candidates within identical exam categories.
 */
function normalizeExamKey(examName) {
  const name = String(examName || "").trim().toLowerCase();
  if (name.includes("neet")) return "NEET";
  if (name.includes("jee adv") || name === "jee_advanced") return "JEE_ADVANCED";
  if (name.includes("jee")) return "JEE_MAIN";
  if (name.includes("cuet")) return "CUET";
  if (name.includes("upsc") || name.includes("civil services")) return "UPSC";
  if (name.includes("cgl") || name.includes("ssc_cgl")) return "SSC_CGL";
  if (name.includes("chsl") || name.includes("ssc_chsl")) return "SSC_CHSL";
  if (name.includes("mts") || name.includes("ssc_mts")) return "SSC_MTS";
  if (name.includes("gate")) return "GATE";
  if (name.includes("nda")) return "NDA";
  if (name.includes("cds")) return "CDS";
  if (name.includes("cat")) return "CAT";
  if (name.includes("ctet") || name.includes("uptet")) return "CTET";
  if (name.includes("rrb") || name.includes("ntpc")) return "RRB_NTPC";
  return String(examName || "NEET").trim().toUpperCase().replace(/\s+/g, "_");
}

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
    throw new functions.https.HttpsError(
      "not-found",
      "Test document not found."
    );
  }

  const testData = testDoc.data();

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
  const canonicalExam = normalizeExamKey(testData.targetExam || testData.subject || "NEET");

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
  const userRef = db.collection("Users_Data").doc(userId);
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
          solutionKeys,
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
      solutionKeys,
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
