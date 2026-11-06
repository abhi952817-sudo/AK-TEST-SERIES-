const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
admin.initializeApp();

const db = admin.firestore();
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
  normalizeExamKey,
} = require("./examScoring");
const {
  RANKING_CONFIG, resolveRankingWeight, calculateWeightedRankingScore,
  getIndiaDateKey, getIndiaWeekId, getRankingPeriodIds,
  getLeagueForXp, calculateXpAward, calculatePercentileFromCounts, getRankingStatus
} = require("./rankingEngine");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");

exports.normalizeExamKey = normalizeExamKey;

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

  const testStatus = String(testData.status || "").trim().toUpperCase();
  const isApprovedOrPublished = testStatus === "APPROVED" || testStatus === "PUBLISHED";
  const isExplicitlyRestricted = testStatus === "UNDER REVIEW" || testStatus === "REJECTED" || testStatus === "DRAFT";
  const hasPublicFlag = testData.isPublic === true || testData.isOfficial === true;

  // A public flag alone MUST NOT allow a pending or rejected test to be treated as approved
  const isPubliclyAvailable = isApprovedOrPublished && !isExplicitlyRestricted && hasPublicFlag;

  if (!isCallerAdmin && !isCreator && !isPubliclyAvailable) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Cannot submit an attempt for an unpublished, pending moderation, or restricted test."
    );
  }

  const countsTowardRanking = testData.countsTowardRanking === true && isPubliclyAvailable;
  const canonicalExam = normalizeExamKey(testData.targetExam || testData.subject || "NEET");
  const rankingWeight = resolveRankingWeight(testData);

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
  const attemptsRef = userRef.collection("Attempts");
  const stableUserTestKey = userId + ":" + cleanTestId;
  const stableKeyHash = crypto.createHash("sha256").update(stableUserTestKey).digest("hex");
  const firstAttemptLockRef = db.collection("Ranking_Attempt_Locks").doc(stableKeyHash);
  const xpTransactionRef = db.collection("XP_Transactions").doc(stableKeyHash);
  const examEntryRef = db.collection("Exam_Leaderboards").doc(canonicalExam)
    .collection("Entries").doc(userId);

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
    // Recompute India-local period boundaries on every transaction retry.
    const rankingNow = Date.now();
    const rankingDateKey = getIndiaDateKey(rankingNow);
    const rankingWeekId = getIndiaWeekId(rankingNow);
    const rankingPeriodIds = getRankingPeriodIds(rankingNow);
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
          isFirstQualifyingAttempt: existing.isFirstQualifyingAttempt || false,
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

    // Read a deterministic lock plus legacy attempts. The lock prevents concurrent
    // first submissions from both being counted as the first valid attempt.
    const lockSnap = countsTowardRanking ? await transaction.get(firstAttemptLockRef) : null;
    const existingAttemptsSnap = await transaction.get(
      attemptsRef.where("testId", "==", cleanTestId).limit(1)
    );
    const legacyExistingAttemptsSnap = await transaction.get(
      userRef.collection("attempts").where("testId", "==", cleanTestId).limit(1)
    );
    const isFirstAttempt = existingAttemptsSnap.empty && legacyExistingAttemptsSnap.empty;
    const priorQualifyingAttemptsSnap = countsTowardRanking ? await transaction.get(
      attemptsRef.where("testId", "==", cleanTestId)
        .where("countsTowardRanking", "==", true)
        .where("verifiedServerSide", "==", true).limit(1)
    ) : null;
    const legacyPriorQualifyingAttemptsSnap = countsTowardRanking ? await transaction.get(
      userRef.collection("attempts").where("testId", "==", cleanTestId)
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
      updates.maxScore = Math.round(evalResult.finalScore);
    }

    // Award ranking points only on FIRST valid attempt on QUALIFYING OFFICIAL tests
    const examScoreKey = `cumulativeScore_${canonicalExam}`;
    const examQCountKey = `qualifyingTestsCount_${canonicalExam}`;
    // Never seed v2 ranking with legacy raw-mark aggregates. Only the v2 materialized
    // entry is a valid base; the admin backfill rebuilds legacy attempts into this schema.
    const v2RankData = existingV2RankSnap.exists ? existingV2RankSnap.data() : {};
    const currentExamScore = Number(v2RankData.score || 0);
    const currentExamQCount = Number(v2RankData.qualifyingTestsCount || 0);
    const currentTotalScore = Number(userData.totalCumulativeScoreV2 || 0);
    let newExamScore = currentExamScore;

    // Normalize to a common 10,000-point full-test scale before applying test weight.
    const weightedScore = calculateWeightedRankingScore(
      evalResult.finalScore, evalResult.maxScore, rankingWeight
    );

    if (isFirstQualifyingAttempt) {
      newExamScore = currentExamScore + weightedScore;
      updates[examQCountKey] = currentExamQCount + 1;
      updates.qualifyingTestsCount = (userData.qualifyingTestsCount || 0) + 1;
      updates[examScoreKey] = newExamScore;
      updates.totalCumulativeScoreV2 = currentTotalScore + weightedScore;
    }

    // All transaction reads must happen before writes.
    const xpTransactionSnap = await transaction.get(xpTransactionRef);
    const shouldAwardXp = isFirstAttempt && !xpTransactionSnap.exists && isPubliclyAvailable;
    const xpAward = shouldAwardXp ? calculateXpAward(evalResult.correctCount) : 0;
    const currentXpTotal = Number(userData.xpTotal || 0);
    const profileIsPublic = String(userData.profileVisibility || "PUBLIC").toUpperCase() !== "PRIVATE";
    const userIsBanned = userData.isBanned === true;
    const currentWeekXp = userData.xpWeekId === rankingWeekId ? Number(userData.xpThisWeek || 0) : 0;
    const previousActivityDate = String(userData.lastActivityDateIST || "");
    const yesterdayDateKey = getIndiaDateKey(rankingNow - 86400000);
    const currentStreak = previousActivityDate === rankingDateKey
      ? Number(userData.currentStreak || 0)
      : (previousActivityDate === yesterdayDateKey ? Number(userData.currentStreak || 0) + 1 : 1);
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

    if (shouldAwardXp) {
      updates.xpTotal = newXpTotal;
      updates.xpThisWeek = newWeekXp;
      updates.xpWeekId = rankingWeekId;
      updates.currentStreak = currentStreak;
      updates.longestStreak = Math.max(Number(userData.longestStreak || 0), currentStreak);
      updates.lastActivityDateIST = rankingDateKey;
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

    const qualifyingTests = updates[examQCountKey] !== undefined
      ? updates[examQCountKey]
      : currentExamQCount;

    const isEligible = qualifyingTests >= RANKING_CONFIG.minQualifyingTests && !userIsBanned;
    const competitiveScore = updates[examScoreKey] !== undefined ? updates[examScoreKey] : currentExamScore;

    if (isFirstQualifyingAttempt) {
      const periodBase = {
        userId,
        displayName: profileIsPublic ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: profileIsPublic ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
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
      rankingWeight: countsTowardRanking ? rankingWeight : 0,
      normalizedRankingPoints: isFirstQualifyingAttempt ? weightedScore : 0,
      isFirstQualifyingAttempt,
      xpAwarded: shouldAwardXp ? xpAward : 0,
      verifiedServerSide: true,
      authoritativeScore: evalResult.finalScore,
      submittedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    transaction.set(attemptDocRef, attemptRecord, { merge: true });

    // Mirror to legacy lowercase attempts subcollection for backwards compatibility
    const legacyAttemptRef = userRef.collection("attempts").doc(attemptDocId);
    transaction.set(legacyAttemptRef, attemptRecord, { merge: true });

    // Atomically maintain per-test leaderboard entry for qualifying first attempts
    if (isFirstQualifyingAttempt) {
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
    if (isFirstQualifyingAttempt) {
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
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        rankingWeightVersion: RANKING_CONFIG.version,
        profileVisibility: userData.profileVisibility || "PUBLIC",
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

    if (isFirstQualifyingAttempt) {
      const publicProfile = profileIsPublic;
      transaction.set(examEntryRef, {
        userId,
        displayName: publicProfile ? (userData.displayName || userData.name || "Student") : "Private Student",
        profileVisibility: publicProfile ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
        targetExam: canonicalExam,
        score: competitiveScore,
        maxSingleScore: Math.round(evalResult.finalScore),
        qualifyingTestsCount: qualifyingTests,
        isEligibleForRanking: isEligible,
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        rankingWeightVersion: RANKING_CONFIG.version,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });

      transaction.set(db.collection("User_Ranks").doc(userId)
        .collection("Exams").doc(canonicalExam), {
          userId, targetExam: canonicalExam, score: competitiveScore,
          qualifyingTestsCount: qualifyingTests, isEligibleForRanking: isEligible, isBanned: userIsBanned,
          scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
      rankingWeight: countsTowardRanking ? rankingWeight : 0,
      normalizedRankingPoints: isFirstQualifyingAttempt ? weightedScore : 0,
      xpAwarded: shouldAwardXp ? xpAward : 0,
      xpTotal: newXpTotal,
      xpThisWeek: newWeekXp,
      xpLeague: getLeagueForXp(newXpTotal),
      currentStreak: shouldAwardXp ? currentStreak : Number(userData.currentStreak || 0),
      rankingPeriodIds: isFirstQualifyingAttempt ? rankingPeriodIds : null,
    };
  });
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
    const beforeVisibility = String(before.profileVisibility || "PUBLIC").toUpperCase();
    const afterVisibility = String(after.profileVisibility || "PUBLIC").toUpperCase();
    const beforeName = before.displayName || before.name || "Student";
    const afterName = after.displayName || after.name || "Student";
    const beforeBanned = before.isBanned === true;
    const afterBanned = after.isBanned === true;
    if (beforeVisibility === afterVisibility && beforeName === afterName && beforeBanned === afterBanned) return null;

    const isPublic = afterVisibility !== "PRIVATE";
    const displayName = isPublic ? afterName : "Private Student";
    const entrySnapshot = await db.collectionGroup("Entries")
      .where("userId", "==", context.params.userId).get();
    const allTimeSnapshot = await db.collection("XP_Leaderboard_AllTime")
      .doc("Entries").collection("Students")
      .where("userId", "==", context.params.userId).get();
    const docs = [...entrySnapshot.docs, ...allTimeSnapshot.docs];
    for (let offset = 0; offset < docs.length; offset += 450) {
      const batch = db.batch();
      docs.slice(offset, offset + 450).forEach(doc => {
        const row = doc.data();
        const update = {
          displayName,
          profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
          isBanned: afterBanned,
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
  [...upperSnap.docs, ...lowerSnap.docs].forEach(doc => byAttemptId.set(doc.id, doc.data()));
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
    if (!Number.isFinite(rawScore) || !Number.isFinite(maxScore) || maxScore <= 0) continue;
    seenTests.add(testId);

    let testData = {};
    let testSnap = await db.collection("Tests_Data").doc(testId).get();
    if (!testSnap.exists) testSnap = await db.collection("Tests").doc(testId).get();
    if (testSnap.exists) testData = testSnap.data() || {};

    const examId = normalizeExamKey(attempt.targetExam || testData.targetExam || testData.subject || "UNKNOWN");
    if (examId === "UNKNOWN" || examId === "ALL") continue;
    const storedWeight = Number(attempt.rankingWeight);
    const weight = storedWeight === 1 || storedWeight === 0.2 ? storedWeight : resolveRankingWeight(testData);
    const points = calculateWeightedRankingScore(rawScore, maxScore, weight);
    const aggregate = exams.get(examId) || { score: 0, count: 0, maxSingleScore: 0 };
    aggregate.score += points;
    aggregate.count += 1;
    aggregate.maxSingleScore = Math.max(aggregate.maxSingleScore, rawScore);
    exams.set(examId, aggregate);

    const timestamp = attemptTime(attempt) || Date.now();
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

  const userData = userSnap.data();
  const isPublic = String(userData.profileVisibility || "PUBLIC").toUpperCase() !== "PRIVATE";
  const displayName = isPublic ? (userData.displayName || userData.name || "Student") : "Private Student";
  const userIsBanned = userData.isBanned === true;
  const userUpdates = { rankingBackfillVersion: RANKING_CONFIG.version,
    rankingBackfilledAt: admin.firestore.FieldValue.serverTimestamp() };
  let totalCumulativeScore = 0;
  const batch = db.batch();

  for (const [examId, aggregate] of exams.entries()) {
    const eligible = aggregate.count >= RANKING_CONFIG.minQualifyingTests;
    userUpdates["cumulativeScore_" + examId] = aggregate.score;
    userUpdates["qualifyingTestsCount_" + examId] = aggregate.count;
    totalCumulativeScore += aggregate.score;

    batch.set(db.collection("Exam_Leaderboards").doc(examId).collection("Entries").doc(userId), {
      userId, displayName, profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
      isBanned: userIsBanned,
      targetExam: examId, score: aggregate.score, maxSingleScore: aggregate.maxSingleScore,
      qualifyingTestsCount: aggregate.count, isEligibleForRanking: eligible && !userIsBanned,
      scoreBasis: "NORMALIZED_WEIGHTED_10000_V2", rankingWeightVersion: RANKING_CONFIG.version,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(), backfilled: true
    }, { merge: true });

    batch.set(db.collection("User_Ranks").doc(userId).collection("Exams").doc(examId), {
      userId, targetExam: examId, score: aggregate.score,
      qualifyingTestsCount: aggregate.count, isEligibleForRanking: eligible && !userIsBanned,
      isBanned: userIsBanned,
      scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(), backfilled: true
    }, { merge: true });
  }
  userUpdates.totalCumulativeScoreV2 = totalCumulativeScore;

  for (const period of periods.values()) {
    const eligible = period.count >= RANKING_CONFIG.minQualifyingTests;
    batch.set(db.collection("Ranking_Periods").doc(period.examId)
      .collection("Periods").doc(period.periodId).collection("Entries").doc(userId), {
        userId, displayName, profileVisibility: isPublic ? "PUBLIC" : "PRIVATE",
        isBanned: userIsBanned,
        targetExam: period.examId, rankingScore: period.score,
        qualifyingTestsCount: period.count, isEligibleForRanking: eligible && !userIsBanned,
        periodType: period.periodType, periodId: period.periodId,
        scoreBasis: "NORMALIZED_WEIGHTED_10000_V2",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(), backfilled: true
      }, { merge: true });
  }

  batch.update(userRef, userUpdates);
  await batch.commit();
  return {
    userId, migrated: true, examsMigrated: exams.size,
    qualifyingAttempts: [...exams.values()].reduce((sum, item) => sum + item.count, 0),
    periodsMigrated: periods.size, totalCumulativeScore
  };
}

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
  const limit = Math.min(20, Math.max(1, Number(input.limit) || 10));
  const migrationStateRef = db.collection("Config").doc("Ranking_Migration_State");
  const migrationStateSnap = await migrationStateRef.get();
  const savedCursor = migrationStateSnap.exists ? migrationStateSnap.data().lastProcessedUid : null;
  const effectiveCursor = input.cursor || savedCursor || null;
  let query = db.collection("Users_Data").orderBy(admin.firestore.FieldPath.documentId()).limit(limit);
  if (effectiveCursor) query = query.startAfter(String(effectiveCursor));
  const snapshot = await query.get();
  const results = [];
  for (const doc of snapshot.docs) {
    results.push(await rebuildRankingV2ForUser(doc.id));
  }
  const nextCursor = snapshot.size === limit ? snapshot.docs[snapshot.docs.length - 1].id : null;
  const hasMore = snapshot.size === limit;
  await migrationStateRef.set({
    lastProcessedUid: nextCursor || effectiveCursor || null,
    hasMore,
    completed: !hasMore,
    lastBatchProcessed: snapshot.size,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  return {
    success: true, processed: snapshot.size,
    nextCursor, hasMore, results
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

  if (qualifyingCount >= RANKING_CONFIG.minQualifyingTests) {
    const materializedEntry = await db.collection("Exam_Leaderboards").doc(canonicalExam)
      .collection("Entries").doc(userId).get();
    if (!materializedEntry.exists) {
      return {
        isEligible: false, status: "Ranking Migration Pending",
        rank: 0, rankString: "Pending", percentile: 0.0,
        competitiveScore, qualifyingTestsCount: qualifyingCount,
        minQualifyingTests: RANKING_CONFIG.minQualifyingTests,
        totalEligible: 0, isOfficialAir: false, isOfficialAkAir: false,
        rankingScope: "AK_TEST_SERIES",
        message: "Your previous qualifying attempts are waiting for the v2 ranking migration. Your scores have not been discarded.",
        targetExam: canonicalExam
      };
    }
  }

  // Query eligible participant population for this specific exam
  const eligibleQuery = db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
    .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false);

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
  const higherQuery = db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
    .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)
    .where("score", ">", competitiveScore);

  const higherSnap = await higherQuery.count().get();
  const higherCount = higherSnap.data().count;
  const rank = higherCount + 1;

  // Standard Competition Percentile Formula:
  // P = ((N - rank + 1) / N) * 100
  const equalSnap = await db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
    .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)
    .where("score", "==", competitiveScore)
    .count().get();
  const equalCount = equalSnap.data().count;
  const belowCount = Math.max(0, totalEligible - higherCount - equalCount);
  const percentile = calculatePercentileFromCounts(belowCount, equalCount, totalEligible);

  const rankingStatus = getRankingStatus(totalEligible);
  const isOfficialAir = rankingStatus.isOfficialAkAir; // AK platform AIR, never NTA national AIR

  // Next target motivation
  let nextTarget = null;
  if (rank > 1) {
    const higherDocsSnap = await db.collection("Exam_Leaderboards").doc(canonicalExam).collection("Entries")
        .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)
      .where("score", ">", competitiveScore)
      .orderBy("score", "asc")
      .limit(1)
      .get();
    if (!higherDocsSnap.empty) {
      const targetDoc = higherDocsSnap.docs[0].data();
      const targetScore = targetDoc.score || 0;
      const ptsAway = Math.max(0, targetScore - competitiveScore);
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
    status: rankingStatus.status,
    rankLabel: rankingStatus.rankLabel,
    isOfficialAir,
    isOfficialAkAir: rankingStatus.isOfficialAkAir,
    rankingScope: "AK_TEST_SERIES",
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
 * Public exam leaderboard. Private students retain their competitive position but
 * their identity is replaced with a neutral label; no email or private profile data is returned.
 */
exports.getExamLeaderboard = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  const examId = normalizeExamKey((data || {}).targetExam || "NEET");
  const limit = Math.min(100, Math.max(1, Number((data || {}).limit) || 100));
  const snapshot = await db.collection("Exam_Leaderboards").doc(examId)
    .collection("Entries").where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)
    .orderBy("score", "desc").limit(limit).get();
  const totalSnap = await db.collection("Exam_Leaderboards").doc(examId)
    .collection("Entries").where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false).count().get();
  const totalEligible = totalSnap.data().count;
  const airStatus = getRankingStatus(totalEligible);
  const rows = snapshot.docs.map(doc => doc.data());
  let previousScore = null;
  let competitionRank = 0;
  const entries = rows.map((row, index) => {
    const score = Number(row.score || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const isPrivate = String(row.profileVisibility || "PUBLIC").toUpperCase() === "PRIVATE";
    return {
      rank: competitionRank,
      userId: isPrivate ? null : row.userId,
      displayName: isPrivate ? "Private Student" : (row.displayName || "Student"),
      score,
      qualifyingTestsCount: Number(row.qualifyingTestsCount || 0),
      profileVisibility: isPrivate ? "PRIVATE" : "PUBLIC"
    };
  });
  return { success: true, targetExam: examId, rankingScope: "AK_TEST_SERIES",
    scoreBasis: "NORMALIZED_WEIGHTED_10000_V2", totalEligible,
    status: airStatus.status, isOfficialAkAir: airStatus.isOfficialAkAir, entries };
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
  const examId = normalizeExamKey(input.targetExam || "NEET");
  const periodType = String(input.periodType || "HALF_MONTH").toUpperCase();
  if (!["HALF_MONTH", "MONTH"].includes(periodType)) {
    throw new functions.https.HttpsError("invalid-argument", "periodType must be HALF_MONTH or MONTH.");
  }
  const currentPeriods = getRankingPeriodIds(Date.now());
  const periodId = String(input.periodId || (periodType === "MONTH" ? currentPeriods.monthId : currentPeriods.halfMonthId));
  if (!/^\d{4}-\d{2}(-H[12])?$/.test(periodId) ||
      (periodType === "HALF_MONTH" && !/-H[12]$/.test(periodId)) ||
      (periodType === "MONTH" && /-H[12]$/.test(periodId))) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid ranking period identifier.");
  }
  const limit = Math.min(100, Math.max(1, Number(input.limit) || 100));
  const snapshot = await db.collection("Ranking_Periods").doc(examId)
    .collection("Periods").doc(periodId).collection("Entries")
    .where("isEligibleForRanking", "==", true)
    .where("isBanned", "==", false)
    .orderBy("rankingScore", "desc").limit(limit).get();
  let previousScore = null;
  let competitionRank = 0;
  const entries = snapshot.docs.map((doc, index) => {
    const row = doc.data();
    const score = Number(row.rankingScore || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const isPrivate = String(row.profileVisibility || "PUBLIC").toUpperCase() === "PRIVATE";
    return {
      rank: competitionRank,
      userId: isPrivate ? null : (row.userId || doc.id),
      displayName: isPrivate ? "Private Student" : (row.displayName || "Student"),
      score,
      qualifyingTestsCount: Number(row.qualifyingTestsCount || 0)
    };
  });
  const thirdPlaceRank = entries.length >= 3 ? entries[2].rank : (entries.length ? entries[entries.length - 1].rank : 0);
  return {
    success: true, targetExam: examId, periodType, periodId,
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
  const limit = Math.min(100, Math.max(1, Number((data || {}).limit) || 100));
  let query;
  if (mode === "ALL_TIME") {
    query = db.collection("XP_Leaderboard_AllTime").doc("Entries")
      .collection("Students").where("isBanned", "==", false).orderBy("xpTotal", "desc").limit(limit);
  } else if (mode === "WEEKLY") {
    const weekId = String((data || {}).weekId || getIndiaWeekId(Date.now()));
    query = db.collection("XP_Leaderboard_Weekly").doc(weekId)
      .collection("Entries").where("isBanned", "==", false).orderBy("xp", "desc").limit(limit);
  } else {
    throw new functions.https.HttpsError("invalid-argument", "mode must be WEEKLY or ALL_TIME.");
  }
  const snapshot = await query.get();
  let previousScore = null;
  let competitionRank = 0;
  const entries = snapshot.docs.map((doc, index) => {
    const row = doc.data();
    const score = Number(mode === "ALL_TIME" ? row.xpTotal || 0 : row.xp || 0);
    if (previousScore === null || score !== previousScore) competitionRank = index + 1;
    previousScore = score;
    const isPrivate = String(row.profileVisibility || "PUBLIC").toUpperCase() === "PRIVATE";
    return {
      rank: competitionRank, userId: isPrivate ? null : (row.userId || doc.id),
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

