"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  createQuestionLevelTestFingerprint,
  buildApprovedQuestionLevelQuestions
} = require("./questionLevelScoringIntegration");

const sourceTest = {
  examId: "neet_ug", examYear: 2026, scoringVersion: "neet-2026-v1",
  stageId: "ug", paperId: "paper_1"
};
const sourceQuestions = [{
  id: "q1", subjectId: "biology", sectionId: "section_a", questionType: "MCQ_SINGLE",
  correctAnswer: "A"
}];
const fingerprint = createQuestionLevelTestFingerprint("test_1", sourceTest, sourceQuestions);
const registry = {
  schemaVersion: 1, status: "ACTIVE",
  tests: [{
    testId: "test_1", status: "APPROVED", testFingerprint: fingerprint,
    approvedBy: "primary-admin", approvedAt: "2026-11-28T00:00:00.000Z",
    source: { url: "https://example.gov/bulletin.pdf", title: "Official test rubric" },
    rubricVersion: "v1",
    questions: [{
      id: "q1", strategyId: "BINARY_MARKS", maxMarks: 4,
      positiveMarks: 4, negativeMarks: 1
    }]
  }]
};

test("builds question-level rules only from an exact approved fingerprint binding", () => {
  const result = buildApprovedQuestionLevelQuestions(registry, "test_1", fingerprint, sourceQuestions);
  assert.equal(result[0].strategyId, "BINARY_MARKS");
  assert.equal(result[0].correctAnswer, "A");
});

test("rejects missing, duplicate, unapproved, and stale rubric bindings", () => {
  assert.throws(() => buildApprovedQuestionLevelQuestions(null, "test_1", fingerprint, sourceQuestions));
  assert.throws(() => buildApprovedQuestionLevelQuestions(registry, "missing", fingerprint, sourceQuestions));
  const stale = structuredClone(registry);
  stale.tests[0].testFingerprint = "stale";
  assert.throws(() => buildApprovedQuestionLevelQuestions(stale, "test_1", fingerprint, sourceQuestions));
  const duplicate = structuredClone(registry);
  duplicate.tests.push(duplicate.tests[0]);
  assert.throws(() => buildApprovedQuestionLevelQuestions(duplicate, "test_1", fingerprint, sourceQuestions));
});

test("rejects incomplete question coverage and missing trusted answer keys", () => {
  const incomplete = structuredClone(registry);
  incomplete.tests[0].questions = [];
  assert.throws(() => buildApprovedQuestionLevelQuestions(incomplete, "test_1", fingerprint, sourceQuestions));
  const noKey = structuredClone(registry);
  assert.throws(() => buildApprovedQuestionLevelQuestions(noKey, "test_1", fingerprint, [{ ...sourceQuestions[0], correctAnswer: "" }]));
});
