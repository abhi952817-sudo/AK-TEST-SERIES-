const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  validateCommunityMcqCreate,
  validateCommunityMcqAttempt,
  splitLegacySharedQuestions,
  resolveCommunityExamSubject,
} = require("./communityValidation");

const validCreate = {
  title: "Cell biology",
  question: "Which organelle produces ATP?",
  subject: "Botany",
  targetExam: "NEET UG",
  options: ["Nucleus", "Mitochondrion", "Ribosome", "Golgi body"],
  correctOptionIndex: 1,
  requestId: "stable-client-request-1",
};

test("MCQ creation accepts exactly four options and one correct index", () => {
  const result = validateCommunityMcqCreate(validCreate);
  assert.equal(result.options.length, 4);
  assert.equal(result.correctOptionIndex, 1);
  assert.equal(result.examId, "neet_ug");
});

test("MCQ creation accepts exactly five options", () => {
  const result = validateCommunityMcqCreate({
    ...validCreate,
    options: [...validCreate.options, "Lysosome"],
    correctOptionIndex: 4,
  });
  assert.equal(result.options.length, 5);
});

test("MCQ creation rejects multi-question/blank content, General subject and missing exam", () => {
  for (const payload of [
    { ...validCreate, question: "" },
    { ...validCreate, subject: "General" },
    { ...validCreate, targetExam: "" },
  ]) assert.throws(() => validateCommunityMcqCreate(payload), { code: "invalid-argument" });
});

test("MCQ creation rejects 3/6 options, duplicate options and invalid answer index", () => {
  for (const payload of [
    { ...validCreate, options: ["A", "B", "C"] },
    { ...validCreate, options: ["A", "B", "C", "D", "E", "F"] },
    { ...validCreate, options: ["A", "B", "C", "a"] },
    { ...validCreate, correctOptionIndex: 4 },
  ]) assert.throws(() => validateCommunityMcqCreate(payload), { code: "invalid-argument" });
});

test("MCQ creation rejects arbitrary image hosts and missing idempotency key", () => {
  assert.throws(() => validateCommunityMcqCreate({ ...validCreate, imageUrl: "https://evil.example/image.png" }), { code: "invalid-argument" });
  assert.throws(() => validateCommunityMcqCreate({ ...validCreate, requestId: "" }), { code: "invalid-argument" });
});

test("MCQ attempt requires a post ID and an integer option index", () => {
  assert.deepEqual(validateCommunityMcqAttempt({ postId: "post-1", optionIndex: 3 }), { postId: "post-1", optionIndex: 3 });
  for (const payload of [
    { postId: "", optionIndex: 0 },
    { postId: "post-1", optionIndex: 0.5 },
    { postId: "post-1", optionIndex: 5 },
  ]) assert.throws(() => validateCommunityMcqAttempt(payload), { code: "invalid-argument" });
});


test("Legacy shared-test migration separates secrets from the public question projection", () => {
  const { publicQuestions, privateKeys } = splitLegacySharedQuestions([
    {
      id: "q1",
      questionText: "Question?",
      optionA: "A",
      optionB: "B",
      correctAnswer: "B",
      explanation: "Private explanation",
      explanationHi: "निजी व्याख्या",
      subject: "Biology",
      unknownPrivateField: "must not leak"
    }
  ], "legacy-test");

  assert.equal(publicQuestions.length, 1);
  assert.equal(publicQuestions[0].questionText, "Question?");
  assert.equal(publicQuestions[0].testId, "legacy-test");
  assert.equal(Object.hasOwn(publicQuestions[0], "correctAnswer"), false);
  assert.equal(Object.hasOwn(publicQuestions[0], "explanation"), false);
  assert.equal(Object.hasOwn(publicQuestions[0], "unknownPrivateField"), false);
  assert.equal(privateKeys[0].correctAnswer, "B");
  assert.equal(privateKeys[0].explanation, "Private explanation");
});

test("Legacy shared-test migration fails closed for missing answers, duplicate IDs and oversized tests", () => {
  assert.throws(() => splitLegacySharedQuestions([{ id: "q1", questionText: "No answer" }], "test"), { code: "failed-precondition" });
  assert.throws(() => splitLegacySharedQuestions([
    { id: "q1", questionText: "Q1", correctAnswer: "A" },
    { id: "q1", questionText: "Q2", correctAnswer: "B" }
  ], "test"), { code: "failed-precondition" });
  assert.throws(() => splitLegacySharedQuestions(Array.from({ length: 201 }, (_, i) => ({
    id: `q${i}`, questionText: "Q", correctAnswer: "A"
  })), "test"), { code: "failed-precondition" });
});


test("Community subject registry resolves canonical names and rejects unknown exam/subject pairs", () => {
  assert.deepEqual(resolveCommunityExamSubject("NEET", "Botany"), {
    examId: "neet_ug",
    examName: "NEET UG",
    subject: "Botany"
  });
  assert.throws(() => resolveCommunityExamSubject("Unknown Exam", "Physics"), { code: "invalid-argument" });
  assert.throws(() => resolveCommunityExamSubject("NEET UG", "Mathematics"), { code: "invalid-argument" });
});
