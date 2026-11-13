const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  validateCommunityMcqCreate,
  validateCommunityMcqAttempt,
} = require("./communityValidation");

const validCreate = {
  title: "Cell biology",
  question: "Which organelle produces ATP?",
  subject: "Biology",
  targetExam: "NEET UG",
  options: ["Nucleus", "Mitochondrion", "Ribosome", "Golgi body"],
  correctOptionIndex: 1,
  requestId: "stable-client-request-1",
};

test("MCQ creation accepts exactly four options and one correct index", () => {
  const result = validateCommunityMcqCreate(validCreate);
  assert.equal(result.options.length, 4);
  assert.equal(result.correctOptionIndex, 1);
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
