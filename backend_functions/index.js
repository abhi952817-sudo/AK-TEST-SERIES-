const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// The Android app is configured against this named Firestore database.
// Never let privileged Cloud Functions silently operate on the unrelated (default) database.
const FIRESTORE_DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID || "ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf";
const db = admin.firestore(FIRESTORE_DATABASE_ID);
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
  normalizeExamKey,
} = require("./examScoring");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");
const crypto = require("node:crypto");
const { validateCommunityShareSource } = require("./communityShare");


exports.normalizeExamKey = normalizeExamKey;

/**
 * Server-authoritative Community test publishing.
 * The client sends only a source test ID and the subject selected in the verification UI.
 * Questions, ownership, exam policy, scoring, and answer-key separation are re-read and
 * validated from server-owned Firestore records before a single atomic publication batch.
 */
exports.shareTestToCommunity = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in before sharing a test.");
  }

  const uid = context.auth.uid;
  const testId = typeof data?.testId === "string" ? data.testId.trim() : "";
  const selectedSubject = typeof data?.selectedSubject === "string" ? data.selectedSubject.trim() : "";

  if (!testId || testId.length > 128 || testId.includes("/")) {
    throw new functions.https.HttpsError("invalid-argument", "A valid source test ID is required.");
  }
  if (!selectedSubject || selectedSubject.length > 100) {
    throw new functions.https.HttpsError("invalid-argument", "Select a valid subject in the test verification card.");
  }

  const userRef = db.collection("Users_Data").doc(uid);
  const sourceRef = db.collection("Tests_Data").doc(testId);
  const registryRef = db.collection("Config").doc("community_exam_registry");

  let userSnap;
  let sourceSnap;
  let registrySnap;
  try {
    [userSnap, sourceSnap, registrySnap] = await Promise.all([
      userRef.get(),
      sourceRef.get(),
      registryRef.get()
    ]);
  } catch (error) {
    console.error("Community share source read failed:", error);
    throw new functions.https.HttpsError("unavailable", "The source test could not be verified. Try again later.");
  }

  if (!userSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Your user profile could not be verified.");
  }
  try {
    assertActiveUserStatus(userSnap.data(), uid);
  } catch (error) {
    throw new functions.https.HttpsError(error.code || "permission-denied", error.message);
  }
  if (!sourceSnap.exists) {
    throw new functions.https.HttpsError("not-found", "The original test is not available in the verified Tests_Data collection.");
  }
  if (!registrySnap.exists) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "The server-side Community exam registry has not been configured by an administrator. No test was published."
    );
  }

  let validated;
  try {
    validated = validateCommunityShareSource({
      testId,
      ownerUid: uid,
      testData: sourceSnap.data(),
      registry: registrySnap.data(),
      selectedSubject
    });
  } catch (error) {
    throw new functions.https.HttpsError(
      error.code || "failed-precondition",
      error.message || "The source test did not pass Community verification."
    );
  }

  const sourceData = sourceSnap.data();
  const authorName = String(
    userSnap.get("displayName") ||
    userSnap.get("name") ||
    context.auth.token.name ||
    (context.auth.token.email ? context.auth.token.email.split("@")[0] : "") ||
    "Student"
  ).trim().slice(0, 80);

  const sourceFingerprint = crypto.createHash("sha256").update(JSON.stringify({
    testId,
    examId: validated.examId,
    examYear: validated.examYear,
    stageId: validated.stageId,
    paperId: validated.paperId,
    scoringVersion: validated.scoringVersion,
    registryVersion: registrySnap.get("version"),
    subject: validated.subject,
    title: validated.title,
    description: validated.description,
    classLevel: validated.classLevel,
    chapter: validated.chapter,
    topic: validated.topic,
    difficulty: validated.difficulty,
    durationMinutes: validated.durationMinutes,
    totalMarks: validated.totalMarks,
    questions: validated.publicQuestions,
    privateKeys: validated.privateKeys
  })).digest("hex");

  const sharedRef = db.collection("Shared_Community_Tests").doc(testId);
  const postId = "TEST_SHARE_" + uid + "_" + testId;
  const postRef = db.collection("Community_Posts").doc(postId);

  let existingShared;
  let existingPost;
  try {
    [existingShared, existingPost] = await Promise.all([sharedRef.get(), postRef.get()]);
  } catch (error) {
    throw new functions.https.HttpsError("unavailable", "Existing Community share state could not be checked.");
  }

  if (existingShared.exists && existingShared.get("creatorId") !== uid) {
    throw new functions.https.HttpsError("already-exists", "A different owner has already shared this test ID.");
  }
  if (existingShared.exists && existingShared.get("sourceFingerprint") !== sourceFingerprint) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "The source test changed after its Community publication. Ask an administrator to review the existing share before republishing."
    );
  }
  if (existingPost.exists && existingPost.get("creatorId") !== uid) {
    throw new functions.https.HttpsError("already-exists", "The Community post ID is already owned by another account.");
  }

  const now = admin.firestore.FieldValue.serverTimestamp();
  const sharedData = {
    testId,
    title: validated.title,
    description: validated.description,
    targetExam: validated.examName,
    examId: validated.examId,
    examYear: Number(validated.examYear),
    stageId: validated.stageId,
    paperId: validated.paperId,
    scoringVersion: validated.scoringVersion,
    subject: validated.subject,
    classLevel: validated.classLevel,
    chapter: validated.chapter,
    topic: validated.topic,
    difficulty: validated.difficulty,
    questionCount: validated.questionCount,
    questionsCount: validated.questionCount,
    durationMinutes: validated.durationMinutes,
    totalMarks: validated.totalMarks,
    source: String(sourceData.institute || sourceData.source || "Student-created test").slice(0, 120),
    creatorId: uid,
    uploaderId: uid,
    creatorDisplayName: authorName,
    creatorName: authorName,
    answerKeySeparated: true,
    isPublic: true,
    isOfficial: false,
    countsTowardRanking: false,
    status: "Published",
    sharedAt: now,
    updatedAt: now,
    version: 1,
    sharingState: "COMMUNITY_SHARED",
    sourceFingerprint,
    registryVersion: registrySnap.get("version")
  };

  const postData = {
    id: postId,
    authorName,
    authorSubtitle: validated.examName + " Aspirant",
    isOfficial: false,
    isMonitor: false,
    timeAgo: "Just now",
    title: "Shared Test: " + validated.title,
    content: validated.description || ("Verified " + validated.examName + " test with " +
      validated.questionCount + " questions."),
    isPinned: false,
    upvotes: 0,
    comments: 0,
    views: "0",
    subject: validated.subject,
    targetExam: validated.examName,
    imageUrl: null,
    isBookmarked: false,
    timestamp: now,
    postType: "TEST_SHARE",
    testId,
    creatorId: uid,
    testTitle: validated.title,
    questionCount: validated.questionCount,
    durationMinutes: validated.durationMinutes,
    chapter: validated.chapter,
    difficulty: validated.difficulty,
    classLevel: validated.classLevel,
    totalMarks: validated.totalMarks,
    topic: validated.topic
  };

  // Parent + safe public question projections + private answer keys + feed card:
  // one batch, with no client-controlled question or grading data.
  const batch = db.batch();
  batch.set(sharedRef, sharedData);
  for (const question of validated.publicQuestions) {
    batch.set(sharedRef.collection("questions").doc(question.id), question);
  }
  batch.set(sharedRef.collection("private_keys").doc("answerKey"), {
    testId,
    questions: validated.privateKeys,
    updatedAt: now
  });
  batch.set(postRef, postData);
  await batch.commit();

  return {
    success: true,
    verifiedServerSide: true,
    alreadyShared: existingShared.exists,
    testId,
    postId,
    subject: validated.subject,
    questionCount: validated.questionCount
  };
});


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
  // Prefer the verified Community projection when the same source test has been shared.
  let testDoc = await db.collection("Shared_Community_Tests").doc(cleanTestId).get();
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests_Data").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("LiveTests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Test document not found."
    );
  }

  const testData = testDoc.data();
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
  // Null means ranking is unavailable or not applicable; never fabricate rank 0.
  let testTotalCandidates = null;
  let testRank = null;
  let attemptPercentile = null;

  if (countsTowardRanking) {
    try {
      const testEntriesRef = db.collection("Test_Leaderboards").doc(cleanTestId).collection("Entries");
      const higherSnap = await testEntriesRef.where("score", ">", evalResult.finalScore).count().get();
      const totalSnap = await testEntriesRef.count().get();
      const rankingSnapshot = createRankingSnapshot(
        higherSnap.data().count,
        totalSnap.data().count
      );
      if (rankingSnapshot) {
        testRank = rankingSnapshot.testRank;
        testTotalCandidates = rankingSnapshot.testTotalCandidates;
        attemptPercentile = rankingSnapshot.percentile;
      }
    } catch (e) {
      // Preserve unavailable ranking explicitly. The score/attempt can still be saved.
      console.error("Per-test ranking unavailable; storing null rank fields:", e);
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
          percentile: existing.percentile ?? null,
          testRank: existing.testRank ?? null,
          testTotalCandidates: existing.testTotalCandidates ?? null,
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
  // Use the exact shared projection and its private key vault for Community attempts.
  let testDoc = await db.collection("Shared_Community_Tests").doc(cleanTestId).get();
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests_Data").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("Tests").doc(cleanTestId).get();
  }
  if (!testDoc.exists) {
    testDoc = await db.collection("LiveTests").doc(cleanTestId).get();
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

