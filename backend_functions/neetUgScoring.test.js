"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { validateAndMergeAnswerKeys } = require("./examScoring");
const {
  NEET_UG_2026_POLICY,
  createNeetScoringFingerprint,
  evaluateNeetUgAttempt
} = require("./neetUgScoring");

function testData(overrides = {}) {
  return {
    examId: "neet_ug",
    examYear: "2026",
    scoringVersion: "neet-ug-2026-v1",
    stageId: "neet_main",
    paperId: "neet_paper_1",
    testType: "Chapter Test",
    ...overrides
  };
}

function question(id, subject = "Physics", overrides = {}) {
  return { id, subject, questionType: "MCQ_SINGLE", correctAnswer: "A", ...overrides };
}

test("NEET policy is pinned to the official 2026 identity and +4/-1/0 scheme", () => {
  assert.equal(NEET_UG_2026_POLICY.positiveMarks, 4);
  assert.equal(NEET_UG_2026_POLICY.negativeMarks, 1);
  assert.equal(NEET_UG_2026_POLICY.unansweredMarks, 0);
  assert.equal(NEET_UG_2026_POLICY.fullLengthQuestionCount, 180);
  assert.equal(NEET_UG_2026_POLICY.maxMarks, 720);
});

test("NEET scoring calculates correct, incorrect and unanswered answers", () => {
  const result = evaluateNeetUgAttempt(
    testData({ questionsCount: 3 }),
    [question("q1"), question("q2"), question("q3")],
    { q1: { ans: "A" }, q2: { ans: "B" }, q3: { ans: "" } },
    120
  );
  assert.equal(result.correctCount, 1);
  assert.equal(result.wrongCount, 1);
  assert.equal(result.unansweredCount, 1);
  assert.equal(result.positiveMarksEarned, 4);
  assert.equal(result.negativeMarksPenalty, 1);
  assert.equal(result.rawScore, 3);
  assert.equal(result.finalScore, 3);
  assert.equal(result.maxScore, 12);
  assert.equal(result.accuracyPercent, 50);
});

test("NEET supports multiple accepted correct options from trusted answer keys", () => {
  const result = evaluateNeetUgAttempt(
    testData(),
    [question("q1", "Chemistry", { correctAnswer: "A,C", scoringStatus: "MULTIPLE_CORRECT" })],
    { q1: "C" }
  );
  assert.equal(result.correctCount, 1);
  assert.equal(result.finalScore, 4);
});

test("NEET dropped questions award +4 regardless of attempted status", () => {
  const result = evaluateNeetUgAttempt(
    testData(),
    [question("q1", "Biology", { correctAnswer: "", scoringStatus: "DROPPED" })],
    {}
  );
  assert.equal(result.bonusCount, 1);
  assert.equal(result.unansweredCount, 0);
  assert.equal(result.finalScore, 4);
  assert.equal(result.subjectBreakdown.biology.bonus, 1);
});

test("NEET all-options-correct questions award +4 only when attempted", () => {
  const attempted = evaluateNeetUgAttempt(
    testData(),
    [question("q1", "Physics", { correctAnswer: "", scoringStatus: "ALL_OPTIONS_CORRECT" })],
    { q1: "D" }
  );
  const skipped = evaluateNeetUgAttempt(
    testData(),
    [question("q1", "Physics", { correctAnswer: "", scoringStatus: "ALL_OPTIONS_CORRECT" })],
    {}
  );
  assert.equal(attempted.finalScore, 4);
  assert.equal(skipped.finalScore, 0);
  assert.equal(skipped.unansweredCount, 1);
});

test("NEET score follows the official 4R-W formula even when raw score is negative", () => {
  const result = evaluateNeetUgAttempt(
    testData(),
    [question("q1"), question("q2")],
    { q1: "B", q2: "C" }
  );
  assert.equal(result.rawScore, -2);
  assert.equal(result.finalScore, -2);
});

test("full-length NEET paper enforces 180 questions and 45/45/90 subject distribution", () => {
  const questions = [
    ...Array.from({ length: 45 }, (_, i) => question("p" + i, "Physics")),
    ...Array.from({ length: 45 }, (_, i) => question("c" + i, "Chemistry")),
    ...Array.from({ length: 90 }, (_, i) => question("b" + i, "Biology"))
  ];
  const answers = Object.fromEntries(questions.map(q => [q.id, "A"]));
  const result = evaluateNeetUgAttempt(
    testData({ testType: "Full Syllabus", durationMinutes: 180, questionsCount: 180, totalMarks: 720 }),
    questions,
    answers,
    10800
  );
  assert.equal(result.totalQuestions, 180);
  assert.equal(result.maxScore, 720);
  assert.equal(result.finalScore, 720);
  assert.equal(result.subjectBreakdown.physics.total, 45);
  assert.equal(result.subjectBreakdown.chemistry.total, 45);
  assert.equal(result.subjectBreakdown.biology.total, 90);
});

test("full-length NEET rejects incorrect question counts and subject distribution", () => {
  assert.throws(() => evaluateNeetUgAttempt(
    testData({ testType: "Full Syllabus" }),
    Array.from({ length: 179 }, (_, i) => question("q" + i)),
    {}
  ), /exactly 180/);
});

test("NEET rejects missing or unsupported policy identity and unsupported question types", () => {
  assert.throws(() => evaluateNeetUgAttempt(testData({ examYear: "2025" }), [question("q1")], {}), /only the explicitly verified 2026 policy/);
  assert.throws(() => evaluateNeetUgAttempt(testData({ scoringVersion: "v2" }), [question("q1")], {}), /scoringVersion/);
  assert.throws(() => evaluateNeetUgAttempt(testData(), [question("q1", "Physics", { questionType: "NUMERICAL" })], { q1: "4" }), /single-correct MCQs/);
});

test("NEET rejects answer IDs that do not belong to the official question list", () => {
  assert.throws(() => evaluateNeetUgAttempt(testData(), [question("q1")], { other: "A" }), /unknown question IDs/);
});

const sharedGolden = JSON.parse(fs.readFileSync(
  path.join(__dirname, "../app/src/test/resources/scoring/neet_ug_2026_golden.json"),
  "utf8"
));

test("shared Kotlin/Node golden fixtures produce the same NEET 2026 scores", () => {
  for (const fixture of sharedGolden.cases) {
    const result = evaluateNeetUgAttempt(
      testData({ questionsCount: fixture.questions.length }),
      fixture.questions,
      fixture.answers
    );
    for (const [field, expected] of Object.entries(fixture.expected)) {
      assert.equal(result[field], expected, fixture.id + " -> " + field);
    }
  }
});

test("NEET scoring fingerprint is order-independent and changes with trusted answer keys", () => {
  const data = testData();
  const one = question("q1", "Physics", { correctAnswer: "A" });
  const two = question("q2", "Chemistry", { correctAnswer: "B" });
  const original = createNeetScoringFingerprint(data, [one, two]);
  const reordered = createNeetScoringFingerprint(data, [two, one]);
  const changedKey = createNeetScoringFingerprint(data, [one, { ...two, correctAnswer: "C" }]);
  assert.equal(original, reordered);
  assert.notEqual(original, changedKey);
});

test("protected answer-key exceptions survive server merge and drive NEET scoring", () => {
  const sourceQuestions = [{
    id: "dropped_q",
    subject: "Biology",
    questionType: "MCQ_SINGLE"
  }];
  const merged = validateAndMergeAnswerKeys(
    { countsTowardRanking: true, isOfficial: true, isPublic: true },
    sourceQuestions,
    { questions: [{ id: "dropped_q", correctAnswer: "", scoringStatus: "DROPPED" }] }
  );
  assert.equal(merged.questions[0].scoringStatus, "DROPPED");
  const result = evaluateNeetUgAttempt(testData(), merged.questions, {});
  assert.equal(result.bonusCount, 1);
  assert.equal(result.finalScore, 4);
});

test("unknown NEET answer-key exception statuses fail closed", () => {
  assert.throws(() => evaluateNeetUgAttempt(
    testData(),
    [question("q1", "Physics", { scoringStatus: "UNREVIEWED_BONUS" })],
    { q1: "A" }
  ), /Unsupported NEET answer-key status/);
});
