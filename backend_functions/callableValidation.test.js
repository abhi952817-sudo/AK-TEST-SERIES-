const { test } = require("node:test");
const assert = require("node:assert");
const {
  evaluateTestAttempt,
  calculatePopulationPercentile,
  validateAndMergeAnswerKeys,
  getExamEligibility,
} = require("./examScoring");

// Simulated logic mirroring submitTestAttemptServerSide verification rules
function validateCallableSubmission(data, context, testDocData, privateKeyData = null, existingAttemptData = null, existingCandidates = null) {
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
    solutionKeys,
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
  assert.strictEqual(result.solutionKeys.q1.correctAnswer, "A");
  assert.strictEqual(result.solutionKeys.q1.explanation, "c = 3x10^8 m/s in vacuum");
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

// Validation logic mirroring setAdminRole authorization rules
function validateSetAdminRoleAuth(context) {
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated to manage administrator roles.");
    err.code = "unauthenticated";
    throw err;
  }

  const isCallerAdmin = context.auth.token && context.auth.token.admin === true;
  const isOwnerEmail = context.auth.token && context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true;

  if (!isCallerAdmin && !isOwnerEmail) {
    const err = new Error("Only authorized super administrators with verified credentials can assign admin roles.");
    err.code = "permission-denied";
    throw err;
  }
  return true;
}

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
