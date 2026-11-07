const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
admin.initializeApp();

const db = admin.firestore();
const {
  evaluateTestAttempt,
  validateAndMergeAnswerKeys,
  normalizeExamKey,
  resolveCanonicalExamIdentity,
  getMarkingSchemeForExam,
} = require("./examScoring");
const {
  RANKING_CONFIG, clampRankingLimit, canAdvanceRankingMigrationCheckpoint, canonicalizeAnswerPayload, resolveRankingWeight, calculateWeightedRankingScore,
  isEligibleScoringResult, isEligibleHistoricalRankingTest,
  getIndiaDateKey, getIndiaWeekId, getRankingPeriodIds, isValidRankingPeriodId, isValidRankingWeekId, normalizeClassLevel, resolveClassLevelFilter,
  getLeagueForXp, calculateXpAward, calculateDailyStreak, calculatePercentileFromCounts, buildTestRankingSnapshot, getRankingStatus, getCohortRankingStatus
} = require("./rankingEngine");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");

exports.normalizeExamKey = normalizeExamKey;

// Unknown or malformed visibility values fail closed; only explicit PUBLIC is public.
function isProfilePublic(value) {
  return String(value || "").trim().toUpperCase() === "PUBLIC";
}

async function getCurrentPublicVisibilityByUid(userIds) {
  const uniqueIds = [...new Set(userIds.map(uid => String(uid || "").trim()).filter(Boolean))];
  const result = new Map();
  if (uniqueIds.length === 0) return result;
  try {
    const refs = uniqueIds.map(uid => db.collection("Users_Data").doc(uid));
    const snapshots = await db.getAll(...refs);
    uniqueIds.forEach((uid, index) => {
      const snapshot = snapshots[index];
      result.set(uid, Boolean(snapshot && snapshot.exists &&
        isProfilePublic(snapshot.data().profileVisibility)));
    });
  } catch (error) {
    console.error("Failed to verify live profile visibility for leaderboard response; hiding all affected identities.", error);
    uniqueIds.forEach(uid => result.set(uid, false));
  }
  return result;
}

function requireCanonicalExamId(value, fieldName = "targetExam") {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      fieldName + " is required; the ranking API will not silently default to NEET."
    );
  }
  const canonical = normalizeExamKey(value);
  if (!canonical || canonical === "UNKNOWN" || canonical === "ALL") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A specific, supported exam identity is required for ranking."
    );
  }
  try {
    // Reuse the scoring engine's supported-exam gate so unknown aliases cannot
    // create empty or misleading boards under arbitrary canonical-looking IDs.
    getMarkingSchemeForExam(canonical);
  } catch (_) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "The requested exam does not have a supported scoring policy."
    );
  }
  return canonical;
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
  const canonicalAnswersJson = canonicalizeAnswerPayload(answers);
  const answersHash = crypto.createHash("sha256").update(canonicalAnswersJson).digest("hex");
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
  // Match Firestore Rules exactly; lowercase or unknown status variants are not student-visible.
  const isApprovedOrPublished = ["APPROVED", "PUBLISHED", "Published"].includes(testStatus);
  const isExplicitlyRestricted = testStatus === "UNDER REVIEW" || testStatus === "REJECTED" || testStatus === "DRAFT";
  // Official status is provenance, not visibility. A private official paper is not
  // publicly attemptable and must not award public XP or competitive ranking points.
  const hasPublicFlag = testData.isPublic === true;

  // A public flag alone MUST NOT allow a pending or rejected test to be treated as approved
  const isPubliclyAvailable = isApprovedOrPublished && !isExplicitlyRestricted && hasPublicFlag;

  if (!isCallerAdmin && !isCreator && !isPubliclyAvailable) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Cannot submit an attempt for an unpublished, pending moderation, or restricted test."
    );
  }

  // Competitive AIR is restricted to explicitly approved, public, official papers.
  // A public community/practice test must never enter the official exam ranking.
  const testCountsTowardRanking =
    testData.countsTowardRanking === true &&
    testData.isOfficial === true &&
    testData.isPublic === true &&
    isPubliclyAvailable;
  let canonicalExam;
  try {
    canonicalExam = resolveCanonicalExamIdentity(testData);
  } catch (err) {
    throw new functions.https.HttpsError(err.code || "failed-precondition", err.message);
  }
  const resolvedRankingWeight = resolveRankingWeight(testData);
  if (testCountsTowardRanking && resolvedRankingWeight === null) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Official ranking test has no verified Full Test (1.0x) or Part Test (0.2x) classification."
    );
  }
  const rankingWeight = testCountsTowardRanking ? resolvedRankingWeight : 0;

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
      questions = qSnap.docs.map(doc => ({ ...doc.data(), id: doc.id }));
    } else {
      const qSnapUpper = await testDoc.ref.collection("Questions").get();
      if (!qSnapUpper.empty) {
        questions = qSnapUpper.docs.map(doc => ({ ...doc.data(), id: doc.id }));
      }
    }
  }

  // Check for server-protected answer keys document (denied to normal clients by Firestore rules)
  let privateKeyDoc = await testDoc.ref.collection("private_keys").doc("answerKey").get();
  if (!privateKeyDoc.exists) {
    privateKeyDoc = await db.collection("TestAnswerKeys").doc(cleanTestId).get();
  }

  const privateKeyData = privateKeyDoc.exists ? privateKeyDoc.data() : null;
  const initialAnswerKeyPayload = privateKeyData && privateKeyData.questions !== undefined
    ? privateKeyData.questions
    : null;
  const initialAnswerKeyHash = crypto.createHash("sha256")
    .update(canonicalizeAnswerPayload(initialAnswerKeyPayload)).digest("hex");
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
  // A result with any ungraded response or invalid score bounds must never affect AIR.
  const countsTowardRanking = testCountsTowardRanking && isEligibleScoringResult(evalResult);

  const attemptDocId = attemptSessionToken.trim();
  // Lowercase `attempts` is the app repository’s canonical history path.
  // Uppercase `Attempts` is read-only legacy compatibility; new attempts are not mirrored there.
  const attemptsRef = userRef.collection("attempts");
  const legacyAttemptsRef = userRef.collection("Attempts");
  const stableUserTestKey = userId + ":" + cleanTestId;
  const stableKeyHash = crypto.createHash("sha256").update(stableUserTestKey).digest("hex");
  const firstAttemptLockRef = db.collection("Ranking_Attempt_Locks").doc(stableKeyHash);
  const xpTransactionRef = db.collection("XP_Transactions").doc(stableKeyHash);
  const examEntryRef = db.collection("Exam_Leaderboards").doc(canonicalExam)
    .collection("Entries").doc(userId);

  // 6. Atomic, Idempotent Transaction for Competitive Ranking and Persistence
  const submissionResult = await db.runTransaction(async (transaction) => {
    // Recompute India-local period boundaries on every transaction retry.
    const rankingNow = Date.now();
    const rankingDateKey = getIndiaDateKey(rankingNow);
    const rankingWeekId = getIndiaWeekId(rankingNow);
    const rankingPeriodIds = getRankingPeriodIds(rankingNow);

    // Re-read the exact test and answer-key documents inside the transaction.
    // If an admin edits visibility, identity, scoring metadata, or keys while this
    // submission is being evaluated, abort instead of persisting a stale score.
    const currentTestSnap = await transaction.get(testDoc.ref);
    const currentPrivateKeySnap = await transaction.get(privateKeyDoc.ref);
    if (!currentTestSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Test was removed during submission.");
    }
    const currentTestData = currentTestSnap.data() || {};
    const immutableSubmissionFields = [
      "isPublic", "isOfficial", "countsTowardRanking", "status", "examId",
      "targetExam", "stageId", "paperId", "testType", "testCategory", "category",
      "durationMinutes", "questionsJson"
    ];
    for (const field of immutableSubmissionFields) {
      if (currentTestData[field] !== testData[field]) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Test configuration changed during submission. Refresh the test and submit again."
        );
      }
    }
    const currentAnswerKeyPayload = currentPrivateKeySnap.exists
      ? (currentPrivateKeySnap.get("questions") ?? null)
      : null;
    const currentAnswerKeyHash = crypto.createHash("sha256")
      .update(canonicalizeAnswerPayload(currentAnswerKeyPayload)).digest("hex");
    if (currentAnswerKeyHash !== initialAnswerKeyHash) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "The answer key changed during submission. Refresh the test before retrying."
      );
    }

    const attemptDocRef = attemptsRef.doc(attemptDocId);
    const legacyAttemptDocRef = legacyAttemptsRef.doc(attemptDocId);
    const existingAttemptSnap = await transaction.get(attemptDocRef);
    const legacyAttemptDocSnap = await transaction.get(legacyAttemptDocRef);

    // Idempotency check across canonical and legacy locations. If both copies exist,
    // they must describe the same logical attempt; never silently pick conflicting data.
    const storedHashFor = (record) => {
      if (record && record.answersHash) return record.answersHash;
      if (!record || record.answersJson === undefined) return null;
      try {
        const legacyAnswers = typeof record.answersJson === "string"
          ? JSON.parse(record.answersJson)
          : record.answersJson;
        return crypto.createHash("sha256").update(canonicalizeAnswerPayload(legacyAnswers)).digest("hex");
      } catch (_) {
        return null;
      }
    };
    if (existingAttemptSnap.exists && legacyAttemptDocSnap.exists) {
      const canonical = existingAttemptSnap.data();
      const legacy = legacyAttemptDocSnap.data();
      if (canonical.userId !== legacy.userId || canonical.testId !== legacy.testId ||
          storedHashFor(canonical) !== storedHashFor(legacy)) {
        throw new functions.https.HttpsError(
          "data-loss",
          "Conflicting canonical and legacy copies exist for this attempt. Manual reconciliation is required."
        );
      }
    }
    const existingRecord = existingAttemptSnap.exists
      ? existingAttemptSnap.data()
      : (legacyAttemptDocSnap.exists ? legacyAttemptDocSnap.data() : null);

    // Idempotency check: handle identical retries vs conflicting reuse
    if (existingRecord) {
      const existing = existingRecord;
      if (existing.userId !== userId || existing.testId !== cleanTestId) {
        throw new functions.https.HttpsError(
          "already-exists",
          "Attempt ID belongs to a different test or user."
        );
      }

      // Check whether payload matches previous submission
      const storedAnswersHash = storedHashFor(existing);

      if (storedAnswersHash && storedAnswersHash === answersHash) {
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

          accuracy: existing.accuracy,
          correctCount: existing.correctCount,
          incorrectCount: existing.incorrectCount,
          skippedCount: existing.skippedCount,
          ungradedCount: existing.ungradedCount || 0,
          positiveMarksEarned: existing.positiveMarksEarned || 0,
          negativeMarksPenalty: existing.negativeMarksPenalty || 0,
          isFirstAttempt: existing.isFirstAttempt || false,
          isFirstQualifyingAttempt: existing.isFirstQualifyingAttempt === true,
          countsTowardRanking: existing.countsTowardRanking === true,
          isRetry: true
        };
      } else {
        throw new functions.https.HttpsError(
          "already-exists",
          "Conflicting attempt submission with different answers for existing attempt ID."
        );
      }
    }

    // Read a deterministic lock plus legacy attempts. The lock prevents concurrent
    // first submissions from both being counted as the first valid attempt.
    const lockSnap = countsTowardRanking ? await transaction.get(firstAttemptLockRef) : null;
    const existingAttemptsSnap = await transaction.get(
      attemptsRef.where("testId", "==", cleanTestId).limit(1)
    );
    const legacyExistingAttemptsSnap = await transaction.get(
      legacyAttemptsRef.where("testId", "==", cleanTestId).limit(1)
    );
    const isFirstAttempt = existingAttemptsSnap.empty && legacyExistingAttemptsSnap.empty;
    const priorQualifyingAttemptsSnap = countsTowardRanking ? await transaction.get(
      attemptsRef.where("testId", "==", cleanTestId)
        .where("countsTowardRanking", "==", true)
        .where("verifiedServerSide", "==", true).limit(1)
    ) : null;
    const legacyPriorQualifyingAttemptsSnap = countsTowardRanking ? await transaction.get(
      legacyAttemptsRef.where("testId", "==", cleanTestId)
        .where("countsTowardRanking", "==", true)
        .where("verifiedServerSide", "==", true).limit(1)
    ) : null;
    const isFirstQualifyingAttempt = countsTowardRanking &&
      priorQualifyingAttemptsSnap.empty && legacyPriorQualifyingAttemptsSnap.empty &&
      (!lockSnap || !lockSnap.exists);

    const userSnap = await transaction.get(userRef);
    const existingV2RankSnap = await transaction.get(examEntryRef);
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
      updates.maxScore = evalResult.finalScore;
    }

    // Award ranking points only on FIRST valid attempt on QUALIFYING OFFICIAL tests
    // Exam_Leaderboards is the single source of truth for v2 cumulative score/count.
    // Legacy Users_Data score aggregates are intentionally not read or updated.
    const v2RankData = existingV2RankSnap.exists ? existingV2RankSnap.data() : {};
    const currentExamScore = Number(v2RankData.score || 0);
    const currentExamQCount = Number(v2RankData.qualifyingTestsCount || 0);
    let newExamScore = currentExamScore;

    // Normalize to a common 10,000-point full-test scale before applying test weight.
    const weightedScore = isFirstQualifyingAttempt
      ? calculateWeightedRankingScore(evalResult.finalScore, evalResult.maxScore, rankingWeight)
      : 0;

    if (isFirstQualifyingAttempt) {
      newExamScore = currentExamScore + weightedScore;
    }

    // All transaction reads must happen before writes.
    const xpTransactionSnap = await transaction.get(xpTransactionRef);
    const shouldAwardXp = isFirstAttempt && !xpTransactionSnap.exists && isPubliclyAvailable;
    const xpAward = shouldAwardXp ? calculateXpAward(evalResult.correctCount) : 0;
    const currentXpTotal = Number(userData.xpTotal || 0);
    const profileIsPublic = isProfilePublic(userData.profileVisibility);
    const userIsBanned = userData.isBanned === true;
    const currentWeekXp = userData.xpWeekId === rankingWeekId ? Number(userData.xpThisWeek || 0) : 0;
    const previousActivityDate = String(userData.lastActivityDateIST || "");
    const currentStreak = calculateDailyStreak(previousActivityDate, rankingDateKey, userData.currentStreak);
    const shouldRecordActivity = isPubliclyAvailable;
    const newXpTotal = currentXpTotal + xpAward;
    const newWeekXp = currentWeekXp + xpAward;

    let halfPeriodRef = null;
    let monthPeriodRef = null;
    let halfPeriodSnap = null;
    let monthPeriodSnap = null;
    if (isFirstQualifyingAttempt) {
      halfPeriodRef = db.collection("Ranking_Periods").doc(canonicalExam)
        .collection("Periods").doc(rankingPeriodIds.halfMonthId)
        .collection("Entries").doc(userId);
      monthPeriodRef = db.collection("Ranking_Periods").doc(canonicalExam)
        .collection("Periods").doc(rankingPeriodIds.monthId)
        .collection("Entries").doc(userId);
      halfPeriodSnap = await transaction.get(halfPeriodRef);
      monthPeriodSnap = await transaction.get(monthPeriodRef);
    }

    if (shouldRecordActivity) {
      updates.currentStreak = currentStreak;
      updates.longestStreak = Math.max(Number(userData.longestStreak || 0), currentStreak);
      updates.lastActivityDateIST = rankingDateKey;
    }
    if (shouldAwardXp) {
      updates.xpTotal = newXpTotal;
      updates.xpThisWeek = newWeekXp;
      updates.xpWeekId = rankingWeekId;
      updates.xpLeague = getLeagueForXp(newXpTotal);
    }

    transaction.update(userRef, updates);

    if (isFirstQualifyingAttempt) {
      transaction.create(firstAttemptLockRef, {
        userId, testId: cleanTestId, examId: canonicalExam,
        attemptId: attemptDocId, lockedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }

    if (shouldAwardXp) {
      transaction.create(xpTransactionRef, {
        userId, testId: cleanTestId, attemptId: attemptDocId,
        type: "FIRST_TEST_COMPLETION", xp: xpAward, correctCount: evalResult.correctCount,
        weekId: rankingWeekId, activityDateIST: rankingDateKey,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });
      transaction.set(db.collection("XP_Leaderboard_AllTime").doc("Entries").collection("Students").doc(userId), {
        userId, displayName: profileIsPublic ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: profileIsPublic ? "PUBLIC" : "PRIVATE", isBanned: userIsBanned, xpTotal: newXpTotal,
        xpLeague: getLeagueForXp(newXpTotal), updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      transaction.set(db.collection("XP_Leaderboard_Weekly").doc(rankingWeekId).collection("Entries").doc(userId), {
        userId, displayName: profileIsPublic ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: profileIsPublic ? "PUBLIC" : "PRIVATE", isBanned: userIsBanned, xp: newWeekXp,
        xpLeague: getLeagueForXp(newXpTotal),
        weekId: rankingWeekId, updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    }

    const qualifyingTests = currentExamQCount + (isFirstQualifyingAttempt ? 1 : 0);
    const isEligible = qualifyingTests >= RANKING_CONFIG.minQualifyingTests && !userIsBanned;
    const competitiveScore = newExamScore;

    if (isFirstQualifyingAttempt) {
      const periodBase = {
        userId,
        displayName: profileIsPublic ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: profileIsPublic ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
        classLevel: normalizeClassLevel(userData.classLevel),
        targetExam: canonicalExam,
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      };
      const previousHalf = halfPeriodSnap && halfPeriodSnap.exists ? halfPeriodSnap.data() : {};
      const previousMonth = monthPeriodSnap && monthPeriodSnap.exists ? monthPeriodSnap.data() : {};
      const halfCount = Number(previousHalf.qualifyingTestsCount || 0) + 1;
      const monthCount = Number(previousMonth.qualifyingTestsCount || 0) + 1;
      transaction.set(halfPeriodRef, {
        ...periodBase,
        rankingScore: Number(previousHalf.rankingScore || 0) + weightedScore,
        qualifyingTestsCount: halfCount,
        isEligibleForRanking: halfCount >= RANKING_CONFIG.minQualifyingTests,
        periodType: "HALF_MONTH", periodId: rankingPeriodIds.halfMonthId
      }, { merge: true });
      transaction.set(monthPeriodRef, {
        ...periodBase,
        rankingScore: Number(previousMonth.rankingScore || 0) + weightedScore,
        qualifyingTestsCount: monthCount,
        isEligibleForRanking: monthCount >= RANKING_CONFIG.minQualifyingTests,
        periodType: "MONTH", periodId: rankingPeriodIds.monthId
      }, { merge: true });
    }

    // Persist the single canonical authoritative attempt record in the app history collection.
    const attemptRecord = {
      userId,
      testId: cleanTestId,
      testTitle: testData.title || "",
      targetExam: canonicalExam,
      score: evalResult.finalScore,
      rawScore: evalResult.rawScore,
      maxScore: evalResult.maxScore,
      accuracy: evalResult.accuracyPercent,
      correctCount: evalResult.correctCount,
      incorrectCount: evalResult.wrongCount,
      skippedCount: evalResult.unansweredCount,
      ungradedCount: evalResult.ungradedCount || 0,
      positiveMarksEarned: evalResult.positiveMarksEarned,
      negativeMarksPenalty: evalResult.negativeMarksPenalty,
      timeSpentSeconds: cleanTimeSpent,
      dateTaken: clientTimestamp || Date.now(),
      answersJson: canonicalAnswersJson,
      answersHash,
      attemptSessionToken: attemptDocId,
      isFirstAttempt,
      countsTowardRanking,
      rankingWeight: countsTowardRanking ? rankingWeight : 0,
      normalizedRankingPoints: isFirstQualifyingAttempt ? weightedScore : 0,
      isFirstQualifyingAttempt,
      xpAwarded: shouldAwardXp ? xpAward : 0,
      verifiedServerSide: true,
      authoritativeScore: evalResult.finalScore,
      submittedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    transaction.set(attemptDocRef, attemptRecord, { merge: true });

    // Atomically maintain per-test leaderboard entry for qualifying first attempts
    if (isFirstQualifyingAttempt) {
      const testEntryRef = db.collection("Test_Leaderboards").doc(cleanTestId).collection("Entries").doc(userId);
      transaction.set(testEntryRef, {
        userId,
        displayName: profileIsPublic ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: profileIsPublic ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
        isEligibleForRanking: isEligible,
        score: evalResult.finalScore,
        rawScore: evalResult.rawScore,
        accuracy: evalResult.accuracyPercent,
        submittedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }

    // Exam_Leaderboards is the sole canonical AIR source. Do not dual-write the legacy
    // Leaderboard collection; stale legacy rows remain untouched until a separately approved migration.

    if (isFirstQualifyingAttempt) {
      const publicProfile = profileIsPublic;
      transaction.set(examEntryRef, {
        userId,
        displayName: publicProfile ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: publicProfile ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
        classLevel: normalizeClassLevel(userData.classLevel),
        targetExam: canonicalExam,
        score: competitiveScore,
        maxSingleScore: evalResult.finalScore,
        qualifyingTestsCount: qualifyingTests,
        isEligibleForRanking: isEligible,
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        rankingWeightVersion: RANKING_CONFIG.version,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });

      // Per-user rank details are derived from the canonical Exam_Leaderboards entry.
    }

    return {
      success: true,
      verifiedServerSide: true,
      attemptId: attemptDocId,
      testId: cleanTestId,
      targetExam: canonicalExam,
      score: evalResult.finalScore,
      rawScore: evalResult.rawScore,
      maxScore: evalResult.maxScore,
      accuracy: evalResult.accuracyPercent,
      correctCount: evalResult.correctCount,
      incorrectCount: evalResult.wrongCount,
      skippedCount: evalResult.unansweredCount,
      ungradedCount: evalResult.ungradedCount || 0,
      positiveMarksEarned: evalResult.positiveMarksEarned,
      negativeMarksPenalty: evalResult.negativeMarksPenalty,
      isFirstAttempt,
      countsTowardRanking,
      rankingWeight: countsTowardRanking ? rankingWeight : 0,
      normalizedRankingPoints: isFirstQualifyingAttempt ? weightedScore : 0,
      xpAwarded: shouldAwardXp ? xpAward : 0,
      xpTotal: newXpTotal,
      xpThisWeek: newWeekXp,
      xpLeague: getLeagueForXp(newXpTotal),
      currentStreak: shouldRecordActivity ? currentStreak : Number(userData.currentStreak || 0),
      rankingPeriodIds: isFirstQualifyingAttempt ? rankingPeriodIds : null,
      isFirstQualifyingAttempt
    };
  });

  // Per-test rank is a post-commit live snapshot of the canonical first-attempt
  // leaderboard row. A retry resolves the same row; repeat attempts never rank
  // their uncommitted score. Rank-query failure cannot undo a committed score.
  let testRankingSnapshot;
  if (!submissionResult.countsTowardRanking) {
    testRankingSnapshot = {
      rankStatus: "NOT_ELIGIBLE",
      testRank: null,
      testTotalCandidates: null,
      percentile: null
    };
  } else if (!submissionResult.isFirstQualifyingAttempt) {
    testRankingSnapshot = {
      rankStatus: "NOT_RANKED_REPEAT_ATTEMPT",
      testRank: null,
      testTotalCandidates: null,
      percentile: null
    };
  } else {
    try {
      const testEntryRef = db.collection("Test_Leaderboards").doc(cleanTestId)
        .collection("Entries").doc(userId);
      const canonicalEntrySnap = await testEntryRef.get();
      if (!canonicalEntrySnap.exists) {
        throw new Error("Canonical per-test leaderboard entry is missing after commit.");
      }
      const canonicalEntry = canonicalEntrySnap.data() || {};
      const testEntriesRef = db.collection("Test_Leaderboards").doc(cleanTestId).collection("Entries");
      const [higherSnap, totalSnap] = await Promise.all([
        testEntriesRef.where("isBanned", "==", false)
          .where("score", ">", Number(canonicalEntry.score)).count().get(),
        testEntriesRef.where("isBanned", "==", false).count().get()
      ]);
      testRankingSnapshot = buildTestRankingSnapshot({
        countsTowardRanking: true,
        isFirstQualifyingAttempt: true,
        canonicalEntry,
        higherCount: higherSnap.data().count,
        totalCount: totalSnap.data().count
      });
    } catch (rankError) {
      console.error("Per-test rank snapshot unavailable after committed submission:", rankError);
      testRankingSnapshot = {
        rankStatus: "UNAVAILABLE",
        testRank: null,
        testTotalCandidates: null,
        percentile: null
      };
    }
  }

  return { ...submissionResult, ...testRankingSnapshot };
});

/**
 * Keep public leaderboard labels synchronized when a student changes name/privacy.
 * Ranking remains counted, but private identities are anonymized across every public board.
 */
exports.syncRankingProfileVisibility = functions.firestore
  .document("Users_Data/{userId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};
    const beforeVisibility = String(before.profileVisibility || "").trim().toUpperCase();
    const afterVisibility = String(after.profileVisibility || "").trim().toUpperCase();
    const beforeName = before.displayName || before.name || "Student";
    const afterName = after.displayName || after.name || "Student";
    const beforeBanned = before.isBanned === true;
    const afterBanned = after.isBanned === true;
    const beforeClassLevel = normalizeClassLevel(before.classLevel);
    const afterClassLevel = normalizeClassLevel(after.classLevel);
    if (beforeVisibility === afterVisibility && beforeName === afterName &&
        beforeBanned === afterBanned && beforeClassLevel === afterClassLevel) return null;

    const isPublic = isProfilePublic(after.profileVisibility);
    const displayName = isPublic ? afterName : "Private Student";
    const entrySnapshot = await db.collectionGroup("Entries")
      .where("userId", "==", context.params.userId).get();
    const allTimeSnapshot = await db.collection("XP_Leaderboard_AllTime")
      .doc("Entries").collection("Students")
      .where("userId", "==", context.params.userId).get();
    // Legacy rows are updated only for privacy/ban hygiene; no new score writes target this collection.
    const legacyLeaderboardSnapshot = await db.collection("Leaderboard")
      .where("userId", "==", context.params.userId).get();
    const docs = [...entrySnapshot.docs, ...allTimeSnapshot.docs, ...legacyLeaderboardSnapshot.docs];
    for (let offset = 0; offset < docs.length; offset += 450) {
      const batch = db.batch();
      docs.slice(offset, offset + 450).forEach(doc => {
        const row = doc.data();
        const update = {
          displayName,
          profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
          isBanned: afterBanned,
          classLevel: afterClassLevel,
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        };
        if (row.qualifyingTestsCount !== undefined) {
          update.isEligibleForRanking = !afterBanned &&
            Number(row.qualifyingTestsCount || 0) >= RANKING_CONFIG.minQualifyingTests;
        }
        batch.set(doc.ref, update, { merge: true });
      });
      await batch.commit();
    }
    return { updatedEntries: docs.length };
  });

/**
 * Rebuilds one user's v2 exam ranking materializations from immutable, server-verified
 * attempt records. This is an admin-only, idempotent migration path for pre-v2 users.
 */
async function rebuildRankingV2ForUser(userId) {
  const userRef = db.collection("Users_Data").doc(userId);
  const userSnap = await userRef.get();
  if (!userSnap.exists) return { userId, migrated: false, reason: "USER_NOT_FOUND" };
  const [upperSnap, lowerSnap] = await Promise.all([
    userRef.collection("Attempts").get(),
    userRef.collection("attempts").get()
  ]);
  const byAttemptId = new Map();
  const answerHashForMigration = record => {
    if (record && typeof record.answersHash === "string" && record.answersHash) return record.answersHash;
    if (!record || record.answersJson === undefined) return null;
    try {
      const answers = typeof record.answersJson === "string"
        ? JSON.parse(record.answersJson)
        : record.answersJson;
      return crypto.createHash("sha256").update(canonicalizeAnswerPayload(answers)).digest("hex");
    } catch (_) {
      return null;
    }
  };
  [...upperSnap.docs, ...lowerSnap.docs].forEach(doc => {
    const record = doc.data();
    // Offline/local backup records are history only, never ranking inputs and never
    // candidates for reconciliation against the server-authoritative attempt copy.
    if (record.verifiedServerSide !== true || record.countsTowardRanking !== true) return;
    const key = String(record.attemptSessionToken || doc.id);
    const previous = byAttemptId.get(key);
    if (previous) {
      const sameIdentity = previous.userId === record.userId && previous.testId === record.testId;
      const previousHash = answerHashForMigration(previous);
      const currentHash = answerHashForMigration(record);
      const sameResult = Number(previous.authoritativeScore ?? previous.score) ===
          Number(record.authoritativeScore ?? record.score) &&
        Number(previous.maxScore) === Number(record.maxScore) &&
        previous.countsTowardRanking === record.countsTowardRanking;
      if (!sameIdentity || !previousHash || !currentHash || previousHash !== currentHash || !sameResult) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Conflicting canonical/legacy attempt copies found. Ranking backfill stopped for manual reconciliation."
        );
      }
      // Prefer the app's lowercase canonical history document after proving equivalence.
      if (doc.ref.parent.id === "attempts") byAttemptId.set(key, record);
    } else {
      byAttemptId.set(key, record);
    }
  });
  const attemptTime = attempt => {
    const value = attempt && (attempt.submittedAt || attempt.dateTaken);
    if (value && typeof value.toMillis === "function") return value.toMillis();
    if (typeof value === "number" && Number.isFinite(value)) return value < 100000000000 ? value * 1000 : value;
    const parsed = Date.parse(String(value || ""));
    return Number.isFinite(parsed) ? parsed : 0;
  };
  const attempts = [...byAttemptId.values()].sort((a, b) => attemptTime(a) - attemptTime(b));
  const seenTests = new Set();
  const exams = new Map();
  const periods = new Map();

  for (const attempt of attempts) {
    const testId = String(attempt.testId || "");
    if (!testId || seenTests.has(testId)) continue;
    if (attempt.verifiedServerSide !== true || attempt.countsTowardRanking !== true) continue;

    const rawScore = Number(attempt.authoritativeScore !== undefined ? attempt.authoritativeScore : attempt.score);
    const maxScore = Number(attempt.maxScore);
    if (!Number.isFinite(rawScore) || !Number.isFinite(maxScore) ||
        maxScore <= 0 || rawScore < 0 || rawScore > maxScore + 0.000001) continue;
    let testSnap = await db.collection("Tests_Data").doc(testId).get();
    if (!testSnap.exists) testSnap = await db.collection("Tests").doc(testId).get();
    // Do not backfill orphaned, private, community, pending, or unapproved tests.
    // Old attempt flags alone are not sufficient proof of official ranking eligibility.
    if (!testSnap.exists) continue;
    const testData = testSnap.data() || {};
    if (!isEligibleHistoricalRankingTest(testData)) continue;

    // Historical ranking must use the test's explicit exam identity, never a subject
    // label or an attempt-supplied identity that could redirect it into another board.
    let examId;
    try {
      // The same strict resolver used for live submissions rejects examId/targetExam conflicts.
      examId = resolveCanonicalExamIdentity(testData);
    } catch (_) {
      continue;
    }
    if (attempt.targetExam && normalizeExamKey(attempt.targetExam) !== examId) continue;
    const storedWeight = Number(attempt.rankingWeight);
    const weight = storedWeight === 1 || storedWeight === 0.2 ? storedWeight : resolveRankingWeight(testData);
    if (weight !== 1 && weight !== 0.2) continue;
    seenTests.add(testId);
    const points = calculateWeightedRankingScore(rawScore, maxScore, weight);
    const aggregate = exams.get(examId) || { score: 0, count: 0, maxSingleScore: 0 };
    aggregate.score += points;
    aggregate.count += 1;
    aggregate.maxSingleScore = Math.max(aggregate.maxSingleScore, rawScore);
    exams.set(examId, aggregate);

    const timestamp = attemptTime(attempt);
    // Missing/invalid historical timestamps must never be assigned to the migration day.
    // Keep the exam aggregate, but omit period buckets whose source date cannot be proven.
    if (timestamp > 0) {
      const periodIds = getRankingPeriodIds(timestamp);
      for (const item of [
      { type: "HALF_MONTH", id: periodIds.halfMonthId },
      { type: "MONTH", id: periodIds.monthId }
    ]) {
      const key = examId + ":" + item.type + ":" + item.id;
      const period = periods.get(key) || {
        examId, periodType: item.type, periodId: item.id, score: 0, count: 0
      };
      period.score += points;
      period.count += 1;
      periods.set(key, period);
      }
    }
  }

  const userData = userSnap.data();
  const isPublic = isProfilePublic(userData.profileVisibility);
  const displayName = isPublic ? (userData.displayName || userData.name || "Student") : "Private Student";
  const classLevel = normalizeClassLevel(userData.classLevel);
  const userIsBanned = userData.isBanned === true;
  const userUpdates = { rankingBackfillVersion: RANKING_CONFIG.version,
    rankingBackfilledAt: admin.firestore.FieldValue.serverTimestamp() };
  let totalCumulativeScore = 0;
  let batch = db.batch();
  let batchWriteCount = 0;
  const enqueueSet = (ref, data, options) => {
    batch.set(ref, data, options);
    batchWriteCount += 1;
  };
  const enqueueUpdate = (ref, data) => {
    batch.update(ref, data);
    batchWriteCount += 1;
  };
  const flushBatchIfNeeded = async () => {
    if (batchWriteCount < 450) return;
    await batch.commit();
    batch = db.batch();
    batchWriteCount = 0;
  };

  for (const [examId, aggregate] of exams.entries()) {
    const eligible = aggregate.count >= RANKING_CONFIG.minQualifyingTests;
    // Exam_Leaderboards is the only canonical exam-score store. Do not mirror the
    // same aggregate into Users_Data, which previously created a second source of truth.
    totalCumulativeScore += aggregate.score;

    enqueueSet(db.collection("Exam_Leaderboards").doc(examId).collection("Entries").doc(userId), {
      userId, displayName, profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
      isBanned: userIsBanned, classLevel,
      targetExam: examId, score: aggregate.score, maxSingleScore: aggregate.maxSingleScore,
      qualifyingTestsCount: aggregate.count, isEligibleForRanking: eligible && !userIsBanned,
      scoreBasis: "NORMALIZED_WEIGHTED_10000_V2", rankingWeightVersion: RANKING_CONFIG.version,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(), backfilled: true
    }, { merge: true });
    await flushBatchIfNeeded();

    // User_Ranks is a legacy duplicate; the rebuilt Exam_Leaderboards entry is canonical.
  }

  for (const period of periods.values()) {
    const eligible = period.count >= RANKING_CONFIG.minQualifyingTests;
    enqueueSet(db.collection("Ranking_Periods").doc(period.examId)
      .collection("Periods").doc(period.periodId).collection("Entries").doc(userId), {
        userId, displayName, profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned, classLevel,
        targetExam: period.examId, rankingScore: period.score,
        qualifyingTestsCount: period.count, isEligibleForRanking: eligible && !userIsBanned,
        periodType: period.periodType, periodId: period.periodId,
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(), backfilled: true
      }, { merge: true });
    await flushBatchIfNeeded();
  }

  enqueueUpdate(userRef, userUpdates);
  await batch.commit();
  return {
    userId, migrated: true, examsMigrated: exams.size,
    qualifyingAttempts: [...exams.values()].reduce((sum, item) => sum + item.count, 0),
    periodsMigrated: periods.size, totalCumulativeScore
  };
}

exports.hasTestRankingContributions = functions.https.onCall(async (data, context) => {
  try { validateSetAdminRoleAuth(context); }
  catch (err) {
    throw new functions.https.HttpsError(err.code || "permission-denied", err.message);
  }
  const testId = String((data || {}).testId || "").trim();
  if (!testId) throw new functions.https.HttpsError("invalid-argument", "testId is required.");
  const entries = await db.collection("Test_Leaderboards").doc(testId).collection("Entries").limit(1).get();
  return { success: true, hasContributions: !entries.empty };
});

exports.getRankingMigrationStatus = functions.https.onCall(async (data, context) => {
  try { validateSetAdminRoleAuth(context); }
  catch (err) {
    throw new functions.https.HttpsError(err.code || "permission-denied", err.message);
  }
  const snapshot = await db.collection("Config").doc("Ranking_Migration_State").get();
  if (!snapshot.exists) {
    return { success: true, completed: false, hasMore: true, lastProcessedUid: null, lastBatchProcessed: 0 };
  }
  const state = snapshot.data();
  return {
    success: true,
    completed: state.completed === true,
    hasMore: state.hasMore !== false,
    lastProcessedUid: state.lastProcessedUid || null,
    lastBatchProcessed: Number(state.lastBatchProcessed || 0),
    updatedAt: state.updatedAt || null
  };
});

exports.rebuildRankingV2Batch = functions.https.onCall(async (data, context) => {
  try { validateSetAdminRoleAuth(context); }
  catch (err) {
    throw new functions.https.HttpsError(err.code || "permission-denied", err.message);
  }
  const input = data || {};
  const limit = clampRankingLimit(input.limit, 10, 1, 20);
  const migrationStateRef = db.collection("Config").doc("Ranking_Migration_State");
  const migrationStateSnap = await migrationStateRef.get();
  const migrationState = migrationStateSnap.exists ? migrationStateSnap.data() : {};
  const savedCursor = migrationState.lastProcessedUid || null;
  const requestedCursor = input.cursor ? String(input.cursor) : null;
  if (requestedCursor && requestedCursor !== savedCursor) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Migration cursor does not match the server checkpoint. Refresh migration status and continue from the saved cursor."
    );
  }
  if (migrationState.completed === true) {
    return { success: true, processed: 0, nextCursor: null, hasMore: false, alreadyCompleted: true, results: [] };
  }
  const effectiveCursor = savedCursor;
  let query = db.collection("Users_Data").orderBy(admin.firestore.FieldPath.documentId()).limit(limit);
  if (effectiveCursor) query = query.startAfter(String(effectiveCursor));
  const snapshot = await query.get();
  const results = [];
  for (const doc of snapshot.docs) {
    results.push(await rebuildRankingV2ForUser(doc.id));
  }
  const proposedCursor = snapshot.size === limit ? snapshot.docs[snapshot.docs.length - 1].id : null;
  const proposedHasMore = snapshot.size === limit;
  let nextCursor = proposedCursor || effectiveCursor || null;
  let hasMore = proposedHasMore;
  let checkpointAdvanced = false;
  await db.runTransaction(async transaction => {
    const latestStateSnap = await transaction.get(migrationStateRef);
    const latestState = latestStateSnap.exists ? latestStateSnap.data() : {};
    if (!canAdvanceRankingMigrationCheckpoint(latestState, effectiveCursor)) {
      // Another worker already advanced or completed this checkpoint. Never regress it.
      nextCursor = latestState.completed === true ? null : (latestState.lastProcessedUid || null);
      hasMore = latestState.completed === true ? false : latestState.hasMore === true;
      return;
    }
    transaction.set(migrationStateRef, {
      lastProcessedUid: proposedCursor || effectiveCursor || null,
      hasMore: proposedHasMore,
      completed: !proposedHasMore,
      lastBatchProcessed: snapshot.size,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    checkpointAdvanced = true;
  });
  return {
    success: true, processed: snapshot.size,
    nextCursor, hasMore, checkpointAdvanced, results
  };
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
  const canonicalExam = requireCanonicalExamId(targetExam);
  const classLevel = resolveClassLevelFilter((data || {}).classLevel);
  if (classLevel === null) throw new functions.https.HttpsError("invalid-argument", "classLevel must be All, Class 11, Class 12, or Dropper.");
  const applyClassFilter = query => classLevel === "All" ? query : query.where("classLevel", "==", classLevel);

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
  const userClassLevel = normalizeClassLevel(userData.classLevel);
  if (classLevel !== "All" && classLevel !== userClassLevel) {
    return {
      isEligible: false, status: "Not In Class Cohort", rank: 0,
      rankString: "N/A", percentile: 0, competitiveScore: 0,
      qualifyingTestsCount: 0, totalEligible: 0,
      targetExam: canonicalExam, classLevel,
      message: "Your profile class does not match the selected class leaderboard."
    };
  }

  const materializedEntry = await db.collection("Exam_Leaderboards").doc(canonicalExam)
    .collection("Entries").doc(userId).get();
  const entryData = materializedEntry.exists ? materializedEntry.data() : null;
  const legacyQualifyingCount = Number(userData["qualifyingTestsCount_" + canonicalExam] || 0);
  if (!entryData && legacyQualifyingCount > 0) {
    return {
      isEligible: false, status: "Ranking Migration Pending",
      rank: 0, rankString: "Pending", percentile: 0.0,
      competitiveScore: 0, qualifyingTestsCount: legacyQualifyingCount,
      minQualifyingTests: RANKING_CONFIG.minQualifyingTests,
      totalEligible: 0, isOfficialAir: false, isOfficialAkAir: false,
      rankingScope: "AK_TEST_SERIES",
      message: "Legacy ranking data is awaiting a verified v2 rebuild. Legacy score fields are not used as the new ranking source.",
      targetExam: canonicalExam
    };
  }
  const qualifyingCount = entryData ? Number(entryData.qualifyingTestsCount || 0) : 0;
  const competitiveScore = entryData ? Number(entryData.score || 0) : 0;

  // Query eligible participant population for this specific exam
  const eligibleQuery = applyClassFilter(
    db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
      .where("isEligibleForRanking", "==", true)
      .where("isBanned", "==", false)
  );

  const eligibleSnap = await eligibleQuery.count().get();
  const totalEligible = eligibleSnap.data().count;

  if (qualifyingCount < RANKING_CONFIG.minQualifyingTests || !entryData || entryData.isEligibleForRanking !== true) {
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
  const higherQuery = applyClassFilter(
    db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
      .where("isEligibleForRanking", "==", true)
      .where("isBanned", "==", false)
      .where("score", ">", competitiveScore)
  );

  const higherSnap = await higherQuery.count().get();
  const higherCount = higherSnap.data().count;
  const rank = higherCount + 1;

  // Standard Competition Percentile Formula:
  // P = ((N - rank + 1) / N) * 100
  const equalSnap = await applyClassFilter(
    db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
      .where("isEligibleForRanking", "==", true)
      .where("isBanned", "==", false)
      .where("score", "==", competitiveScore)
  ).count().get();
  const equalCount = equalSnap.data().count;
  const belowCount = Math.max(0, totalEligible - higherCount - equalCount);
  const percentile = calculatePercentileFromCounts(belowCount, equalCount, totalEligible);

  const rankingStatus = getCohortRankingStatus(totalEligible, classLevel);
  const isOfficialAir = rankingStatus.isOfficialAkAir; // Only unfiltered exam cohort is labeled AK AIR

  // Next target motivation
  let nextTarget = null;
  if (rank > 1) {
    const higherDocsSnap = await applyClassFilter(
      db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
        .where("isEligibleForRanking", "==", true)
        .where("isBanned", "==", false)
        .where("score", ">", competitiveScore)
        .orderBy("score", "asc")
    ).limit(1).get();
    if (!higherDocsSnap.empty) {
      const targetDoc = higherDocsSnap.docs[0].data();
      const targetScore = Number(targetDoc.score || 0);
      const ptsAway = Math.max(0, targetScore - competitiveScore);
      const targetHigherSnap = await applyClassFilter(
        db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
          .where("isEligibleForRanking", "==", true)
          .where("isBanned", "==", false)
          .where("score", ">", targetScore)
      ).count().get();
      const targetRank = targetHigherSnap.data().count + 1;
      const name = targetDoc.displayName || "Student";
      nextTarget = { name, rank: targetRank, ptsAway };
    }
  }

  const message = rank === 1
    ? `🎉 Congratulations! You are Rank #1 on the ${canonicalExam} Leaderboard!`
    : nextTarget
      ? `Next target: ${nextTarget.name} (#${nextTarget.rank}) is ${nextTarget.ptsAway} normalized ranking points ahead.`
      : `Keep practicing to climb higher on the ${canonicalExam} leaderboard!`;

  return {
    isEligible: true,
    status: rankingStatus.status,
    rankLabel: rankingStatus.rankLabel,
    isOfficialAir,
    isOfficialAkAir: rankingStatus.isOfficialAkAir,
    isOfficialClassRank: rankingStatus.isOfficialClassRank,
    rankingScope: "AK_TEST_SERIES",
    rank,
    rankString: `#${rank}`,
    percentile,
    competitiveScore,
    qualifyingTestsCount: qualifyingCount,
    totalEligible,
    nextTarget,
    message,
    targetExam: canonicalExam,
    classLevel
  };
});

/**
 * Public exam leaderboard. Private students retain their competitive position but
 * their identity is replaced with a neutral label; no email or private profile data is returned.
 */
exports.getExamLeaderboard = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  const input = data || {};
  const examId = requireCanonicalExamId(input.targetExam);
  const classLevel = resolveClassLevelFilter(input.classLevel);
  if (classLevel === null) throw new functions.https.HttpsError("invalid-argument", "classLevel must be All, Class 11, Class 12, or Dropper.");
  const applyClassFilter = query => classLevel === "All" ? query : query.where("classLevel", "==", classLevel);
  const limit = clampRankingLimit(input.limit, 100, 1, 100);
  const snapshot = await applyClassFilter(db.collection("Exam_Leaderboards").doc(examId)
    .collection("Entries").where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false))
    .orderBy("score", "desc").limit(limit).get();
  const totalSnap = await applyClassFilter(db.collection("Exam_Leaderboards").doc(examId)
    .collection("Entries").where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)).count().get();
  const totalEligible = totalSnap.data().count;
  const airStatus = getCohortRankingStatus(totalEligible, classLevel);
  const rows = snapshot.docs.map(doc => doc.data());
  const rowUserIds = snapshot.docs.map(doc => doc.id);
  const currentVisibility = await getCurrentPublicVisibilityByUid(rowUserIds);
  let previousScore = null;
  let competitionRank = 0;
  const entries = rows.map((row, index) => {
    const score = Number(row.score || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const uid = rowUserIds[index];
    const isPrivate = !isProfilePublic(row.profileVisibility) || currentVisibility.get(uid) !== true;
    return {
      rank: competitionRank,
      userId: isPrivate ? null : uid,
      displayName: isPrivate ? "Private Student" : (row.displayName || "Student"),
      score,
      qualifyingTestsCount: Number(row.qualifyingTestsCount || 0),
      profileVisibility: isPrivate ? "PRIVATE" : "PUBLIC"
    };
  });
  return { success: true, targetExam: examId, classLevel, rankingScope: "AK_TEST_SERIES",
    scoreBasis: "NORMALIZED_WEIGHTED_10000_V2", totalEligible,
    status: airStatus.status, isOfficialAkAir: airStatus.isOfficialAkAir,
    isOfficialClassRank: airStatus.isOfficialClassRank, entries };
});

/**
 * 15-day and monthly exam leaderboard. The period itself is an isolated score cohort.
 * Top three are returned separately for award/recognition workflows.
 */
exports.getRankingPeriodLeaderboard = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  const input = data || {};
  const examId = requireCanonicalExamId(input.targetExam);
  const classLevel = resolveClassLevelFilter(input.classLevel);
  if (classLevel === null) throw new functions.https.HttpsError("invalid-argument", "classLevel must be All, Class 11, Class 12, or Dropper.");
  const applyClassFilter = query => classLevel === "All" ? query : query.where("classLevel", "==", classLevel);
  const periodType = String(input.periodType || "HALF_MONTH").toUpperCase();
  if (!["HALF_MONTH", "MONTH"].includes(periodType)) {
    throw new functions.https.HttpsError("invalid-argument", "periodType must be HALF_MONTH or MONTH.");
  }
  const currentPeriods = getRankingPeriodIds(Date.now());
  const periodId = String(input.periodId || (periodType === "MONTH" ? currentPeriods.monthId : currentPeriods.halfMonthId));
  if (!isValidRankingPeriodId(periodType, periodId)) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid ranking period identifier.");
  }
  const limit = clampRankingLimit(input.limit, 100, 1, 100);
  const snapshot = await applyClassFilter(db.collection("Ranking_Periods").doc(examId)
    .collection("Periods").doc(periodId).collection("Entries")
    .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false))
    .orderBy("rankingScore", "desc").limit(limit).get();
  const periodRows = snapshot.docs.map(doc => doc.data());
  const periodUserIds = snapshot.docs.map(doc => doc.id);
  const currentVisibility = await getCurrentPublicVisibilityByUid(periodUserIds);
  let previousScore = null;
  let competitionRank = 0;
  const entries = snapshot.docs.map((doc, index) => {
    const row = periodRows[index];
    const score = Number(row.rankingScore || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const uid = periodUserIds[index];
    const isPrivate = !isProfilePublic(row.profileVisibility) || currentVisibility.get(uid) !== true;
    return {
      rank: competitionRank,
      userId: isPrivate ? null : uid,
      displayName: isPrivate ? "Private Student" : (row.displayName || "Student"),
      score,
      qualifyingTestsCount: Number(row.qualifyingTestsCount || 0)
    };
  });
  const thirdPlaceRank = entries.length >= 3 ? entries[2].rank : (entries.length ? entries[entries.length - 1].rank : 0);
  return {
    success: true, targetExam: examId, classLevel, periodType, periodId,
    rankingScope: "AK_TEST_SERIES", entries,
    topThree: entries.filter(entry => thirdPlaceRank > 0 && entry.rank <= thirdPlaceRank)
  };
});

/**
 * Weekly and lifetime XP leaderboard. XP never contributes to exam AIR.
 */
exports.getXpLeaderboard = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  const mode = String((data || {}).mode || "WEEKLY").toUpperCase();
  const limit = clampRankingLimit((data || {}).limit, 100, 1, 100);
  let query;
  if (mode === "ALL_TIME") {
    query = db.collection("XP_Leaderboard_AllTime").doc("Entries")
      .collection("Students").where("isBanned", "==", false).orderBy("xpTotal", "desc").limit(limit);
  } else if (mode === "WEEKLY") {
    const weekId = String((data || {}).weekId || getIndiaWeekId(Date.now()));
    if (!isValidRankingWeekId(weekId)) {
      throw new functions.https.HttpsError("invalid-argument", "weekId must be a valid ISO week identifier (YYYY-Www).");
    }
    query = db.collection("XP_Leaderboard_Weekly").doc(weekId)
      .collection("Entries").where("isBanned", "==", false).orderBy("xp", "desc").limit(limit);
  } else {
    throw new functions.https.HttpsError("invalid-argument", "mode must be WEEKLY or ALL_TIME.");
  }
  const snapshot = await query.get();
  const xpRows = snapshot.docs.map(doc => doc.data());
  const xpUserIds = snapshot.docs.map(doc => doc.id);
  const currentVisibility = await getCurrentPublicVisibilityByUid(xpUserIds);
  let previousScore = null;
  let competitionRank = 0;
  const entries = snapshot.docs.map((doc, index) => {
    const row = xpRows[index];
    const score = Number(mode === "ALL_TIME" ? row.xpTotal || 0 : row.xp || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const uid = xpUserIds[index];
    const isPrivate = !isProfilePublic(row.profileVisibility) || currentVisibility.get(uid) !== true;
    return {
      rank: competitionRank, userId: isPrivate ? null : uid,
      displayName: isPrivate ? "Private Student" : (row.displayName || "Student"),
      xp: score, league: row.xpLeague || null
    };
  });
  return { success: true, mode, entries };
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
    throw new functions.https.HttpsError("not-found", "Test document not found.");
  }

  const testData = testDoc.data();
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
      questions = qSnap.docs.map(doc => ({ ...doc.data(), id: doc.id }));
    } else {
      const qSnapUpper = await testDoc.ref.collection("Questions").get();
      if (!qSnapUpper.empty) {
        questions = qSnapUpper.docs.map(doc => ({ ...doc.data(), id: doc.id }));
      }
    }
  }

  let privateKeyDoc = await testDoc.ref.collection("private_keys").doc("answerKey").get();
  if (!privateKeyDoc.exists) {
    privateKeyDoc = await db.collection("TestAnswerKeys").doc(cleanTestId).get();
  }

  const privateKeyData = privateKeyDoc.exists ? privateKeyDoc.data() : null;
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

