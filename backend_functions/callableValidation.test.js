const { test } = require("node:test");
const assert = require("node:assert");
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
} = require("./examScoring");
const { validateSetAdminRoleAuth, assertActiveUserStatus } = require("./authHelper");

// Simulated logic mirroring submitTestAttemptServerSide verification rules
function validateCallableSubmission(
  data,
  context,
  testDocData,
  privateKeyData = null,
  existingAttemptData = null,
  existingCandidates = null,
  userData = { isBanned: false }
) {
  // 1. Authentication check
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated to submit test attempts.");
    err.code = "unauthenticated";
    throw err;
  }

  const userId = context.auth.uid;
  const { testId, answers, timeSpentSeconds, clientTimestamp, attemptSessionToken } = data || {};

  // 2. Argument validation
  if (!testId || typeof testId !== "string" || testId.trim().length === 0) {
    const err = new Error("Missing or invalid testId parameter.");
    err.code = "invalid-argument";
    throw err;
  }

  if (!attemptSessionToken || typeof attemptSessionToken !== "string" || attemptSessionToken.trim().length === 0) {
    const err = new Error("Missing or invalid attemptSessionToken.");
    err.code = "invalid-argument";
    throw err;
  }

  if (!answers || typeof answers !== "object" || Array.isArray(answers)) {
    const err = new Error("Answers payload must be a valid key-value object.");
    err.code = "invalid-argument";
    throw err;
  }

  const cleanTimeSpent = Number(timeSpentSeconds);
  if (isNaN(cleanTimeSpent) || cleanTimeSpent < 0 || cleanTimeSpent > 86400) {
    const err = new Error("Invalid timeSpentSeconds.");
    err.code = "invalid-argument";
    throw err;
  }

  // 2.5 Authoritative Account Status & Ban Check (derived from context.auth.uid)
  assertActiveUserStatus(userData, userId);

  if (!testDocData) {
    const err = new Error("Test document not found.");
    err.code = "not-found";
    throw err;
  }

  // 3. Authorization & strict publication policy
  const isCallerAdmin = Boolean(context.auth.token && context.auth.token.admin === true);
  const isCreator = (testDocData.creatorId && testDocData.creatorId === userId) ||
                    (testDocData.uploaderId && testDocData.uploaderId === userId);

  const testStatus = String(testDocData.status || "").trim();
  const isApprovedOrPublished = testStatus === "APPROVED" || testStatus === "Published";
  const isExplicitlyRestricted = testStatus === "Under Review" || testStatus === "REJECTED" || testStatus === "DRAFT";
  const hasPublicFlag = testDocData.isPublic === true || testDocData.isOfficial === true;

  // A public flag alone MUST NOT allow a pending or rejected test to be treated as approved
  const isPubliclyAvailable = isApprovedOrPublished && !isExplicitlyRestricted && hasPublicFlag;

  if (!isCallerAdmin && !isCreator && !isPubliclyAvailable) {
    const err = new Error("Cannot submit an attempt for an unpublished, pending moderation, or restricted test.");
    err.code = "permission-denied";
    throw err;
  }

  const countsTowardRanking = (testDocData.countsTowardRanking === true || (testDocData.countsTowardRanking !== false && testDocData.isOfficial === true)) &&
                              (testDocData.isOfficial === true || testDocData.isPublic === true);

  // 4. Questions & answer key loading
  let questions = [];
  if (testDocData.questionsJson) {
    try {
      questions = JSON.parse(testDocData.questionsJson);
    } catch (e) {
      questions = [];
    }
  }

  const validated = validateAndMergeAnswerKeys(testDocData, questions, privateKeyData);
  questions = validated.questions;
  const solutionKeys = validated.solutionKeys;

  if (questions.length === 0) {
    const err = new Error("Test contains no questions to evaluate.");
    err.code = "failed-precondition";
    throw err;
  }

  // Validate question IDs in answer payload
  const validQuestionIds = new Set(questions.map(q => String(q.id)));
  for (const qId of Object.keys(answers)) {
    if (!validQuestionIds.has(String(qId))) {
      const err = new Error(`Unexpected question identifier "${qId}" not present in official test questions.`);
      err.code = "invalid-argument";
      throw err;
    }
  }

  // 5. Idempotency handling
  if (existingAttemptData) {
    if (existingAttemptData.userId !== userId || existingAttemptData.testId !== testId) {
      const err = new Error("Attempt ID belongs to a different test or user.");
      err.code = "already-exists";
      throw err;
    }

    const currentAnswersStr = typeof answers === "string" ? answers : JSON.stringify(answers);
    const storedAnswersStr = typeof existingAttemptData.answersJson === "string"
      ? existingAttemptData.answersJson
      : JSON.stringify(existingAttemptData.answersJson);

    if (currentAnswersStr === storedAnswersStr) {
      return {
        success: true,
        verifiedServerSide: true,
        attemptId: attemptSessionToken,
        score: existingAttemptData.score,
        isRetry: true
      };
    } else {
      const err = new Error("Conflicting attempt submission with different answers for existing attempt ID.");
      err.code = "already-exists";
      throw err;
    }
  }

  // 6. Authoritative evaluation
  const targetExam = testDocData.targetExam || "NEET";
  const evalResult = evaluateTestAttempt(targetExam, questions, answers, cleanTimeSpent);



  // Population percentile calculation
  let testRank = 0;
  let testTotalCandidates = 0;
  let attemptPercentile = 0.0;

  if (countsTowardRanking) {
    const candidates = Array.isArray(existingCandidates) ? existingCandidates : [];
    const higherCount = candidates.filter(c => c.score > evalResult.finalScore).length;
    testRank = higherCount + 1;
    testTotalCandidates = candidates.length + 1;
    attemptPercentile = calculatePopulationPercentile(testRank, testTotalCandidates);
  }

  return {
    success: true,
    verifiedServerSide: true,
    attemptId: attemptSessionToken,
    score: Math.round(evalResult.finalScore),
    maxScore: Math.round(evalResult.maxScore),
    percentile: attemptPercentile,
    testRank,
    testTotalCandidates,
    countsTowardRanking,
    isRetry: false
  };
}

// =========================================================================
// UNIT TESTS: Callable Security & Input Validation
// =========================================================================

test("Callable rejects unauthenticated calls", () => {
  assert.throws(
    () => validateCallableSubmission({ testId: "t1" }, null, {}),
    { code: "unauthenticated" }
  );
  assert.throws(
    () => validateCallableSubmission({ testId: "t1" }, { auth: null }, {}),
    { code: "unauthenticated" }
  );
});

test("Callable rejects missing attemptSessionToken", () => {
  const context = { auth: { uid: "user_1" } };
  assert.throws(
    () => validateCallableSubmission({ testId: "t1", answers: {} }, context, {}),
    { code: "invalid-argument" }
  );
});

test("Callable rejects malformed answers payload", () => {
  const context = { auth: { uid: "user_1" } };
  assert.throws(
    () => validateCallableSubmission({ testId: "t1", attemptSessionToken: "att_1", answers: "not_an_object" }, context, {}),
    { code: "invalid-argument" }
  );
  assert.throws(
    () => validateCallableSubmission({ testId: "t1", attemptSessionToken: "att_1", answers: [1, 2, 3] }, context, {}),
    { code: "invalid-argument" }
  );
});

test("Callable rejects unpublished or rejected tests even if isPublic=true", () => {
  const context = { auth: { uid: "student_alice" } };
  
  // Rejected test
  const rejectedTest = {
    id: "test_rejected",
    status: "REJECTED",
    isPublic: true,
    creatorId: "someone_else"
  };
  assert.throws(
    () => validateCallableSubmission({ testId: "test_rejected", attemptSessionToken: "att_1", answers: {}, timeSpentSeconds: 10 }, context, rejectedTest),
    { code: "permission-denied" }
  );

  // Under Review test
  const underReviewTest = {
    id: "test_review",
    status: "Under Review",
    isPublic: true,
    creatorId: "someone_else"
  };
  assert.throws(
    () => validateCallableSubmission({ testId: "test_review", attemptSessionToken: "att_1", answers: {}, timeSpentSeconds: 10 }, context, underReviewTest),
    { code: "permission-denied" }
  );
});

test("Authoritative evaluation uses protected private answer keys", () => {
  const context = { auth: { uid: "student_alice" } };
  
  // Public test doc has NO correctAnswer or explanation
  const publicTestDoc = {
    id: "neet_mock_1",
    status: "Published",
    isPublic: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([
      { id: "q1", questionText: "Speed of light?", optionA: "3x10^8 m/s", optionB: "0" },
      { id: "q2", questionText: "Unit of Force?", optionA: "Newton", optionB: "Joule" }
    ])
  };

  // Private grading doc contains authoritative answer keys
  const privateKeyData = {
    questions: [
      { id: "q1", correctAnswer: "A", explanation: "c = 3x10^8 m/s in vacuum" },
      { id: "q2", correctAnswer: "A", explanation: "SI unit of force is Newton" }
    ]
  };

  const userAnswers = {
    q1: { ans: "A" },
    q2: { ans: "B" } // wrong
  };

  const result = validateCallableSubmission(
    { testId: "neet_mock_1", attemptSessionToken: "att_session_100", answers: userAnswers, timeSpentSeconds: 60 },
    context,
    publicTestDoc,
    privateKeyData
  );

  assert.strictEqual(result.verifiedServerSide, true);
  assert.strictEqual(result.score, 3); // +4 for q1, -1 for q2
  assert.strictEqual(result.maxScore, 8);
  assert.strictEqual(result.solutionKeys, undefined, "Solution keys must NEVER be returned in submitTestAttemptServerSide response");
});

test("Callable rejects unexpected question IDs not in test questions", () => {
  const context = { auth: { uid: "student_alice" } };
  const testDoc = {
    id: "test_real",
    status: "Published",
    isPublic: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", correctAnswer: "A" }])
  };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "test_real", attemptSessionToken: "att_1", answers: { q1: "A", fake_injected_q99: "A" }, timeSpentSeconds: 10 },
      context,
      testDoc
    ),
    { code: "invalid-argument" }
  );
});

test("Callable idempotency: Identical retry returns canonical result without duplicate processing", () => {
  const context = { auth: { uid: "student_alice" } };
  const testDoc = {
    id: "test_real",
    status: "Published",
    isPublic: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", correctAnswer: "A" }])
  };

  const existingAttempt = {
    userId: "student_alice",
    testId: "test_real",
    score: 4,
    answersJson: JSON.stringify({ q1: { ans: "A" } })
  };

  const retryResult = validateCallableSubmission(
    { testId: "test_real", attemptSessionToken: "att_1", answers: { q1: { ans: "A" } }, timeSpentSeconds: 10 },
    context,
    testDoc,
    null,
    existingAttempt
  );

  assert.strictEqual(retryResult.isRetry, true);
  assert.strictEqual(retryResult.score, 4);
});

test("Callable idempotency: Conflicting reuse of attempt ID is rejected", () => {
  const context = { auth: { uid: "student_alice" } };
  const testDoc = {
    id: "test_real",
    status: "Published",
    isPublic: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", correctAnswer: "A" }])
  };

  const existingAttempt = {
    userId: "student_alice",
    testId: "test_real",
    score: 4,
    answersJson: JSON.stringify({ q1: { ans: "A" } })
  };

  // Malicious attempt to resubmit different answers under the same attempt identity
  assert.throws(
    () => validateCallableSubmission(
      { testId: "test_real", attemptSessionToken: "att_1", answers: { q1: { ans: "B" } }, timeSpentSeconds: 10 },
      context,
      testDoc,
      null,
      existingAttempt
    ),
    { code: "already-exists" }
  );
});

test("Non-qualifying practice test yields percentile: 0.0 and countsTowardRanking: false", () => {
  const context = { auth: { uid: "student_alice" } };
  const practiceTest = {
    id: "custom_practice_1",
    status: "Published",
    isPublic: true,
    isOfficial: false,
    countsTowardRanking: false,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", correctAnswer: "A" }])
  };

  const result = validateCallableSubmission(
    { testId: "custom_practice_1", attemptSessionToken: "att_practice_1", answers: { q1: { ans: "A" } }, timeSpentSeconds: 20 },
    context,
    practiceTest
  );

  assert.strictEqual(result.verifiedServerSide, true);
  assert.strictEqual(result.countsTowardRanking, false);
  assert.strictEqual(result.percentile, 0.0);
  assert.strictEqual(result.testRank, 0);
  assert.strictEqual(result.testTotalCandidates, 0);
});

test("Official ranking test calculates population percentile against candidate cohort", () => {
  const context = { auth: { uid: "student_bob" } };
  const officialTest = {
    id: "official_neet_major_1",
    status: "Published",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([
      { id: "q1", correctAnswer: "A" },
      { id: "q2", correctAnswer: "B" },
      { id: "q3", correctAnswer: "C" },
      { id: "q4", correctAnswer: "D" }
    ])
  };

  const privateKeyData = {
    questions: [
      { id: "q1", correctAnswer: "A" },
      { id: "q2", correctAnswer: "B" },
      { id: "q3", correctAnswer: "C" },
      { id: "q4", correctAnswer: "D" }
    ]
  };

  const existingCandidates = [
    { userId: "c1", score: 16 },
    { userId: "c2", score: 11 },
    { userId: "c3", score: 6 },
    { userId: "c4", score: 0 }
  ];

  // Bob gets 3 correct, 1 wrong -> score: 11
  // Higher scores: c1 (16) -> 1 candidate higher
  // Total candidates with Bob: 4 + 1 = 5
  // Bob's rank: 1 + 1 = 2
  // Population percentile: ((5 - 2 + 1) / 5) * 100 = (4 / 5) * 100 = 80.0%
  const bobResult = validateCallableSubmission(
    {
      testId: "official_neet_major_1",
      attemptSessionToken: "att_bob_1",
      answers: { q1: { ans: "A" }, q2: { ans: "B" }, q3: { ans: "C" }, q4: { ans: "A" } }, // 3 correct, 1 wrong
      timeSpentSeconds: 120
    },
    context,
    officialTest,
    privateKeyData,
    null,
    existingCandidates
  );

  assert.strictEqual(bobResult.verifiedServerSide, true);
  assert.strictEqual(bobResult.countsTowardRanking, true);
  assert.strictEqual(bobResult.score, 11);
  assert.strictEqual(bobResult.testRank, 2);
  assert.strictEqual(bobResult.testTotalCandidates, 5);
  assert.strictEqual(bobResult.percentile, 80.0);
});

test("Official ranking test: fails closed if private answer key is missing", () => {
  const context = { auth: { uid: "student_alice" } };
  const officialTest = {
    id: "neet_ranking_test",
    status: "Published",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Question 1" }])
  };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_ranking_test", attemptSessionToken: "att_fail_1", answers: { q1: "A" }, timeSpentSeconds: 10 },
      context,
      officialTest,
      null // missing privateKeyData
    ),
    { code: "failed-precondition" }
  );
});

test("Official ranking test: fails closed if private answer key is incomplete (missing a question ID)", () => {
  const context = { auth: { uid: "student_alice" } };
  const officialTest = {
    id: "neet_ranking_test",
    status: "Published",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([
      { id: "q1", questionText: "Question 1" },
      { id: "q2", questionText: "Question 2" }
    ])
  };

  const incompleteKeys = {
    questions: [
      { id: "q1", correctAnswer: "A" }
      // q2 is missing!
    ]
  };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_ranking_test", attemptSessionToken: "att_fail_2", answers: { q1: "A", q2: "B" }, timeSpentSeconds: 20 },
      context,
      officialTest,
      incompleteKeys
    ),
    { code: "failed-precondition" }
  );
});

test("Official ranking test: rejects public correctAnswer fallback when private key is missing", () => {
  const context = { auth: { uid: "student_alice" } };
  const testWithPublicAnswer = {
    id: "neet_with_public_key",
    status: "Published",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    // Attacker or unmigrated test has public correctAnswer
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Question 1", correctAnswer: "A" }])
  };

  // Submission without trusted private answer key document must throw
  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_with_public_key", attemptSessionToken: "att_fail_3", answers: { q1: "A" }, timeSpentSeconds: 10 },
      context,
      testWithPublicAnswer,
      null
    ),
    { code: "failed-precondition" }
  );
});

test("Exam-specific qualifying counts: NEET attempts do not unlock JEE Main eligibility", () => {
  // User with 5 qualifying NEET attempts but 0 JEE Main attempts
  const userWithNeetAttempts = {
    uid: "student_multi_1",
    targetExam: "NEET",
    qualifyingTestsCount: 5, // legacy/global total
    qualifyingTestsCount_NEET: 5,
    cumulativeScore_NEET: 2500,
    qualifyingTestsCount_JEE_MAIN: 0,
    cumulativeScore_JEE_MAIN: 0
  };

  const jeeEligibility = getExamEligibility(userWithNeetAttempts, "JEE_MAIN");
  assert.strictEqual(jeeEligibility.qualifyingCount, 0, "JEE Main qualifying count must be 0");
  assert.strictEqual(jeeEligibility.isEligible, false, "User must NOT be eligible for JEE Main ranking");
  assert.strictEqual(jeeEligibility.competitiveScore, 0, "JEE Main score must be 0");

  const neetEligibility = getExamEligibility(userWithNeetAttempts, "NEET");
  assert.strictEqual(neetEligibility.qualifyingCount, 5, "NEET qualifying count must be 5");
  assert.strictEqual(neetEligibility.isEligible, true, "User must be eligible for NEET ranking");
  assert.strictEqual(neetEligibility.competitiveScore, 2500, "NEET score must be 2500");
});

test("Exam-specific qualifying counts: JEE Main attempts unlock JEE Main independently", () => {
  const userWithJeeAttempts = {
    uid: "student_multi_2",
    targetExam: "JEE_MAIN",
    qualifyingTestsCount: 2,
    qualifyingTestsCount_JEE_MAIN: 2,
    cumulativeScore_JEE_MAIN: 480
    // No NEET fields at all
  };

  const jeeEligibility = getExamEligibility(userWithJeeAttempts, "JEE_MAIN");
  assert.strictEqual(jeeEligibility.qualifyingCount, 2);
  assert.strictEqual(jeeEligibility.isEligible, true);
  assert.strictEqual(jeeEligibility.competitiveScore, 480);

  const neetEligibility = getExamEligibility(userWithJeeAttempts, "NEET");
  assert.strictEqual(neetEligibility.qualifyingCount, 0);
  assert.strictEqual(neetEligibility.isEligible, false);
  assert.strictEqual(neetEligibility.competitiveScore, 0);
});


test("setAdminRole: Unauthenticated calls are rejected with unauthenticated", () => {
  assert.throws(
    () => validateSetAdminRoleAuth(null),
    (err) => err.code === "unauthenticated"
  );
  assert.throws(
    () => validateSetAdminRoleAuth({ auth: null }),
    (err) => err.code === "unauthenticated"
  );
});

test("setAdminRole: Normal student is rejected with permission-denied", () => {
  const normalStudentContext = {
    auth: {
      uid: "student_456",
      token: {
        email: "student@example.com",
        email_verified: true,
        admin: false
      }
    }
  };
  assert.throws(
    () => validateSetAdminRoleAuth(normalStudentContext),
    (err) => err.code === "permission-denied"
  );
});

test("setAdminRole: Caller with unverified owner email is rejected with permission-denied", () => {
  const unverifiedAttackerContext = {
    auth: {
      uid: "attacker_789",
      token: {
        email: "abhi952817@gmail.com",
        email_verified: false,
        admin: false
      }
    }
  };
  assert.throws(
    () => validateSetAdminRoleAuth(unverifiedAttackerContext),
    (err) => err.code === "permission-denied"
  );
});

test("setAdminRole: Verified owner or admin claim is authorized", () => {
  const verifiedOwnerContext = {
    auth: {
      uid: "owner_123",
      token: {
        email: "abhi952817@gmail.com",
        email_verified: true
      }
    }
  };
  assert.strictEqual(validateSetAdminRoleAuth(verifiedOwnerContext), true);

  const adminClaimContext = {
    auth: {
      uid: "admin_123",
      token: {
        email: "staff@example.com",
        admin: true
      }
    }
  };
  assert.strictEqual(validateSetAdminRoleAuth(adminClaimContext), true);
});

// =========================================================================
// PHASE 7.6: SERVER-AUTHORITATIVE USER BAN ENFORCEMENT TESTS
// =========================================================================

test("Ban enforcement: Active non-banned student can submit valid test attempt", () => {
  const context = { auth: { uid: "active_student_1" } };
  const testDoc = {
    id: "neet_mock_active",
    status: "APPROVED",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const privateKeys = { questions: [{ id: "q1", correctAnswer: "A" }] };
  const activeUserData = { uid: "active_student_1", isBanned: false, name: "Active Student" };

  const res = validateCallableSubmission(
    { testId: "neet_mock_active", attemptSessionToken: "att_act_1", answers: { q1: "A" }, timeSpentSeconds: 30 },
    context,
    testDoc,
    privateKeys,
    null,
    [],
    activeUserData
  );

  assert.strictEqual(res.success, true);
  assert.strictEqual(res.verifiedServerSide, true);
  assert.strictEqual(res.score, 4);
});

test("Ban enforcement: Banned student (isBanned: true) is rejected with permission-denied before scoring", () => {
  const context = { auth: { uid: "banned_student_1" } };
  const testDoc = {
    id: "neet_mock_banned",
    status: "APPROVED",
    isPublic: true,
    isOfficial: true,
    countsTowardRanking: true,
    targetExam: "NEET UG",
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const privateKeys = { questions: [{ id: "q1", correctAnswer: "A" }] };
  const bannedUserData = { uid: "banned_student_1", isBanned: true, name: "Banned Student" };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_mock_banned", attemptSessionToken: "att_ban_1", answers: { q1: "A" }, timeSpentSeconds: 30 },
      context,
      testDoc,
      privateKeys,
      null,
      [],
      bannedUserData
    ),
    (err) => err.code === "permission-denied"
  );
});

test("Ban enforcement: Missing user profile fails closed with not-found", () => {
  const context = { auth: { uid: "ghost_student_1" } };
  const testDoc = {
    id: "neet_mock_ghost",
    status: "APPROVED",
    isPublic: true,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_mock_ghost", attemptSessionToken: "att_ghost_1", answers: { q1: "A" }, timeSpentSeconds: 30 },
      context,
      testDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      null,
      [],
      null // No user record exists
    ),
    (err) => err.code === "not-found"
  );
});

test("Ban enforcement: Identical retry by banned student is rejected with permission-denied", () => {
  const context = { auth: { uid: "banned_student_retry" } };
  const testDoc = {
    id: "neet_mock_retry",
    status: "APPROVED",
    isPublic: true,
    isOfficial: true,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const privateKeys = { questions: [{ id: "q1", correctAnswer: "A" }] };
  const existingAttempt = {
    attemptId: "att_prev_1",
    userId: "banned_student_retry",
    testId: "neet_mock_retry",
    answersJson: JSON.stringify({ q1: "A" }),
    score: 4
  };
  const bannedUserData = { uid: "banned_student_retry", isBanned: true };

  // Even though answers match existing attempt, banned status MUST reject before returning cached result
  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_mock_retry", attemptSessionToken: "att_prev_1", answers: { q1: "A" }, timeSpentSeconds: 30 },
      context,
      testDoc,
      privateKeys,
      existingAttempt,
      [],
      bannedUserData
    ),
    (err) => err.code === "permission-denied"
  );
});

test("Ban enforcement: Conflicting retry by banned student is rejected with permission-denied", () => {
  const context = { auth: { uid: "banned_student_conflict" } };
  const testDoc = {
    id: "neet_mock_conflict",
    status: "APPROVED",
    isPublic: true,
    isOfficial: true,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const existingAttempt = {
    attemptId: "att_conf_1",
    userId: "banned_student_conflict",
    testId: "neet_mock_conflict",
    answersJson: JSON.stringify({ q1: "A" }),
    score: 4
  };
  const bannedUserData = { uid: "banned_student_conflict", isBanned: true };

  assert.throws(
    () => validateCallableSubmission(
      { testId: "neet_mock_conflict", attemptSessionToken: "att_conf_1", answers: { q1: "B" }, timeSpentSeconds: 30 },
      context,
      testDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      existingAttempt,
      [],
      bannedUserData
    ),
    (err) => err.code === "permission-denied"
  );
});

test("Ban enforcement: Client-supplied payload cannot forge active ban status", () => {
  const context = { auth: { uid: "sneaky_banned_student" } };
  const testDoc = {
    id: "neet_mock_forge",
    status: "APPROVED",
    isPublic: true,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  // Attacker sends isBanned: false in request payload, but authoritative server record is isBanned: true
  const authoritativeUserData = { uid: "sneaky_banned_student", isBanned: true };

  assert.throws(
    () => validateCallableSubmission(
      {
        testId: "neet_mock_forge",
        attemptSessionToken: "att_forge_1",
        answers: { q1: "A" },
        timeSpentSeconds: 30,
        isBanned: false, // Injected client forgery
        role: "admin"
      },
      context,
      testDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      null,
      [],
      authoritativeUserData
    ),
    (err) => err.code === "permission-denied"
  );
});

test("assertActiveUserStatus helper behavior", () => {
  // Active user
  assert.strictEqual(assertActiveUserStatus({ uid: "u1", isBanned: false }, "u1"), true);
  // Default isBanned omitted (falsy)
  assert.strictEqual(assertActiveUserStatus({ uid: "u2" }, "u2"), true);

  // Explicitly banned
  assert.throws(
    () => assertActiveUserStatus({ uid: "u3", isBanned: true }, "u3"),
    (err) => err.code === "permission-denied"
  );

  // Missing or null data
  assert.throws(
    () => assertActiveUserStatus(null, "u4"),
    (err) => err.code === "not-found"
  );
  assert.throws(
    () => assertActiveUserStatus(undefined, "u5"),
    (err) => err.code === "not-found"
  );
});

// =========================================================================
// REMEDIATION F-016: Authorized Solution Retrieval & Key Protection Tests
// =========================================================================

function validateGetSolutions(
  data,
  context,
  testDocData,
  privateKeyData = null,
  userAttemptData = null,
  userData = { isBanned: false }
) {
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated to retrieve test solutions.");
    err.code = "unauthenticated";
    throw err;
  }

  const userId = context.auth.uid;
  const { testId, attemptSessionToken } = data || {};

  if (!testId || typeof testId !== "string" || testId.trim().length === 0) {
    const err = new Error("Missing or invalid testId parameter.");
    err.code = "invalid-argument";
    throw err;
  }

  if (!attemptSessionToken || typeof attemptSessionToken !== "string" || attemptSessionToken.trim().length === 0) {
    const err = new Error("Missing or invalid attemptSessionToken parameter.");
    err.code = "invalid-argument";
    throw err;
  }

  const cleanTestId = testId.trim();
  const cleanAttemptId = attemptSessionToken.trim();

  assertActiveUserStatus(userData, userId);

  if (!userAttemptData) {
    const err = new Error("Solutions can only be accessed after completing and submitting the test attempt.");
    err.code = "permission-denied";
    throw err;
  }

  if (userAttemptData.userId !== userId || userAttemptData.testId !== cleanTestId) {
    const err = new Error("Attempt does not match authenticated user or requested test.");
    err.code = "permission-denied";
    throw err;
  }

  const isCompleted = userAttemptData.verifiedServerSide === true ||
                      userAttemptData.submissionStatus === "CONFIRMED" ||
                      userAttemptData.submissionStatus === "COMPLETED" ||
                      Boolean(userAttemptData.submittedAt);
  if (!isCompleted) {
    const err = new Error("Test attempt is not completed. You must complete and submit the test before retrieving solutions.");
    err.code = "permission-denied";
    throw err;
  }

  if (!testDocData) {
    const err = new Error("Test document not found.");
    err.code = "not-found";
    throw err;
  }

  const isCallerAdmin = Boolean(
    context.auth.token && (
      context.auth.token.admin === true ||
      (context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true)
    )
  );

  if (!isCallerAdmin && testDocData.allowSolutionsReview === false) {
    const err = new Error("Detailed solutions review has been disabled for this examination.");
    err.code = "permission-denied";
    throw err;
  }

  if (!isCallerAdmin && (testDocData.isLive === true || testDocData.liveEndTime)) {
    const now = Date.now();
    const liveEndTime = typeof testDocData.liveEndTime === "number"
      ? testDocData.liveEndTime
      : (testDocData.liveEndTime?.toMillis ? testDocData.liveEndTime.toMillis() : 0);
    if (liveEndTime > 0 && now < liveEndTime) {
      const err = new Error("Solutions for live examinations are locked until the live exam window concludes.");
      err.code = "permission-denied";
      throw err;
    }
  }

  let questions = [];
  if (testDocData.questionsJson) {
    try { questions = JSON.parse(testDocData.questionsJson); } catch (e) { questions = []; }
  }

  const validated = validateAndMergeAnswerKeys(testDocData, questions, privateKeyData);
  return {
    success: true,
    testId: cleanTestId,
    attemptSessionToken: cleanAttemptId,
    solutionKeys: validated.solutionKeys,
  };
}

test("F-016: submitTestAttemptServerSide NEVER returns solutionKeys in submission response", () => {
  const context = { auth: { uid: "student_submitting" } };
  const testDoc = {
    id: "neet_mock_secure",
    status: "Published",
    isPublic: true,
    targetExam: "NEET UG",
    countsTowardRanking: true,
    questionsJson: JSON.stringify([
      { id: "q1", questionText: "Speed of light?" },
      { id: "q2", questionText: "Plank constant?" }
    ])
  };
  const privateKeyData = {
    questions: [
      { id: "q1", correctAnswer: "A", explanation: "3x10^8 m/s" },
      { id: "q2", correctAnswer: "B", explanation: "6.626x10^-34 J s" }
    ]
  };

  const response = validateCallableSubmission(
    { testId: "neet_mock_secure", attemptSessionToken: "att_101", answers: { q1: "A" }, timeSpentSeconds: 60 },
    context,
    testDoc,
    privateKeyData
  );

  assert.strictEqual(response.success, true);
  assert.strictEqual(response.verifiedServerSide, true);
  assert.strictEqual(response.score, 4);
  assert.strictEqual(response.solutionKeys, undefined, "CRITICAL: Private solutionKeys must be undefined in submission response");
  assert.strictEqual("correctAnswer" in response, false);
  assert.strictEqual("explanation" in response, false);
});

test("F-016: submitTestAttemptServerSide retry NEVER returns solutionKeys", () => {
  const context = { auth: { uid: "student_submitting" } };
  const existingAttempt = {
    userId: "student_submitting",
    testId: "neet_mock_secure",
    score: 4,
    answersJson: JSON.stringify({ q1: "A" })
  };

  const retryResponse = validateCallableSubmission(
    { testId: "neet_mock_secure", attemptSessionToken: "att_101", answers: { q1: "A" }, timeSpentSeconds: 60 },
    context,
    { id: "neet_mock_secure", status: "Published", isPublic: true, questionsJson: JSON.stringify([{ id: "q1", questionText: "Speed of light?", correctAnswer: "A" }]) },
    null,
    existingAttempt
  );

  assert.strictEqual(retryResponse.isRetry, true);
  assert.strictEqual(retryResponse.solutionKeys, undefined, "CRITICAL: Private solutionKeys must be undefined on retry response");
});

test("F-016: getTestAttemptSolutions rejects unauthenticated callers with unauthenticated", () => {
  assert.throws(
    () => validateGetSolutions({ testId: "t1", attemptSessionToken: "att_1" }, null, {}),
    { code: "unauthenticated" }
  );
  assert.throws(
    () => validateGetSolutions({ testId: "t1", attemptSessionToken: "att_1" }, { auth: null }, {}),
    { code: "unauthenticated" }
  );
});

test("F-016: getTestAttemptSolutions rejects missing or invalid arguments", () => {
  const context = { auth: { uid: "student_1" } };
  assert.throws(
    () => validateGetSolutions({ testId: "", attemptSessionToken: "att_1" }, context, {}),
    { code: "invalid-argument" }
  );
  assert.throws(
    () => validateGetSolutions({ testId: "t1", attemptSessionToken: "" }, context, {}),
    { code: "invalid-argument" }
  );
});

test("F-016: getTestAttemptSolutions rejects banned students with permission-denied", () => {
  const context = { auth: { uid: "banned_student" } };
  const bannedUser = { uid: "banned_student", isBanned: true };
  assert.throws(
    () => validateGetSolutions(
      { testId: "t1", attemptSessionToken: "att_1" },
      context,
      { id: "t1" },
      null,
      { userId: "banned_student", testId: "t1", verifiedServerSide: true },
      bannedUser
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions rejects requests without completed attempt record (prevents pre-exam cheating)", () => {
  const context = { auth: { uid: "student_cheater" } };
  const testDoc = { id: "neet_real", status: "Published", isPublic: true };
  // Cheater calls getTestAttemptSolutions before ever submitting or completing an attempt
  assert.throws(
    () => validateGetSolutions(
      { testId: "neet_real", attemptSessionToken: "random_forged_attempt" },
      context,
      testDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      null // No attempt record exists
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions rejects attempt belonging to a different user (cross-user attack)", () => {
  const context = { auth: { uid: "student_attacker" } };
  const otherUserAttempt = {
    userId: "victim_student", // Belongs to victim
    testId: "neet_real",
    verifiedServerSide: true
  };
  assert.throws(
    () => validateGetSolutions(
      { testId: "neet_real", attemptSessionToken: "victim_attempt_token" },
      context,
      { id: "neet_real" },
      null,
      otherUserAttempt
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions rejects incomplete or unsubmitted attempt", () => {
  const context = { auth: { uid: "student_mid_exam" } };
  const incompleteAttempt = {
    userId: "student_mid_exam",
    testId: "neet_real",
    verifiedServerSide: false,
    submissionStatus: "PENDING", // Still pending / in-progress
    submittedAt: null
  };
  assert.throws(
    () => validateGetSolutions(
      { testId: "neet_real", attemptSessionToken: "att_mid_1" },
      context,
      { id: "neet_real" },
      null,
      incompleteAttempt
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions rejects solutions for active live tests before window ends", () => {
  const context = { auth: { uid: "student_live" } };
  const futureEndTime = Date.now() + 3600000; // 1 hour in future
  const liveTestDoc = {
    id: "neet_all_india_live_1",
    isLive: true,
    liveEndTime: futureEndTime,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const completedAttempt = {
    userId: "student_live",
    testId: "neet_all_india_live_1",
    verifiedServerSide: true,
    submissionStatus: "CONFIRMED"
  };
  assert.throws(
    () => validateGetSolutions(
      { testId: "neet_all_india_live_1", attemptSessionToken: "att_live_1" },
      context,
      liveTestDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      completedAttempt
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions rejects solutions when allowSolutionsReview is explicitly false", () => {
  const context = { auth: { uid: "student_restricted" } };
  const restrictedTestDoc = {
    id: "neet_blind_eval",
    allowSolutionsReview: false,
    questionsJson: JSON.stringify([{ id: "q1", questionText: "Q1" }])
  };
  const completedAttempt = {
    userId: "student_restricted",
    testId: "neet_blind_eval",
    verifiedServerSide: true,
    submissionStatus: "CONFIRMED"
  };
  assert.throws(
    () => validateGetSolutions(
      { testId: "neet_blind_eval", attemptSessionToken: "att_blind_1" },
      context,
      restrictedTestDoc,
      { questions: [{ id: "q1", correctAnswer: "A" }] },
      completedAttempt
    ),
    { code: "permission-denied" }
  );
});

test("F-016: getTestAttemptSolutions successfully authorizes and returns solutionKeys when test is completed and review allowed", () => {
  const context = { auth: { uid: "student_valid" } };
  const testDoc = {
    id: "neet_chapter_1",
    status: "Published",
    isPublic: true,
    questionsJson: JSON.stringify([
      { id: "q1", questionText: "Q1 text" },
      { id: "q2", questionText: "Q2 text" }
    ])
  };
  const privateKeyData = {
    questions: [
      { id: "q1", correctAnswer: "C", explanation: "Because of Newton 3rd Law" },
      { id: "q2", correctAnswer: "A", explanation: "Conservation of energy" }
    ]
  };
  const completedAttempt = {
    userId: "student_valid",
    testId: "neet_chapter_1",
    verifiedServerSide: true,
    submissionStatus: "CONFIRMED"
  };

  const result = validateGetSolutions(
    { testId: "neet_chapter_1", attemptSessionToken: "att_valid_1" },
    context,
    testDoc,
    privateKeyData,
    completedAttempt
  );

  assert.strictEqual(result.success, true);
  assert.strictEqual(result.testId, "neet_chapter_1");
  assert.strictEqual(result.attemptSessionToken, "att_valid_1");
  assert.ok(result.solutionKeys);
  assert.strictEqual(result.solutionKeys.q1.correctAnswer, "C");
  assert.strictEqual(result.solutionKeys.q1.explanation, "Because of Newton 3rd Law");
  assert.strictEqual(result.solutionKeys.q2.correctAnswer, "A");
  assert.strictEqual(result.solutionKeys.q2.explanation, "Conservation of energy");
});


