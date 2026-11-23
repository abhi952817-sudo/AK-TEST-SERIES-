"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { evaluateQuestionLevelAttempt, validateQuestion } = require("./questionLevelScoring");

const multi = {
  id: "multi_1",
  strategyId: "MULTI_SELECT_PARTIAL_TABLE",
  maxMarks: 4,
  rubric: {
    schemaVersion: 1,
    unmatchedMarks: -2,
    unansweredMarks: 0,
    rules: [
      { response: "A,B", awardedMarks: 4, outcome: "CORRECT" },
      { response: "A", awardedMarks: 2, outcome: "PARTIAL" },
      { response: "A,C", awardedMarks: 1, outcome: "PARTIAL" },
      { response: "C,D", awardedMarks: -2, outcome: "WRONG" }
    ]
  }
};

test("multi-select table awards exact partial marks independent of selection order", () => {
  const result = evaluateQuestionLevelAttempt([multi], { multi_1: "B,A" });
  assert.equal(result.correctCount, 1);
  assert.equal(result.partialCount, 0);
  assert.equal(result.positiveMarksEarned, 4);
  assert.equal(result.finalScore, 4);
});

test("multi-select rubric can award partial credit for exact response combinations", () => {
  const result = evaluateQuestionLevelAttempt([multi], { multi_1: "C,A" });
  assert.equal(result.partialCount, 1);
  assert.equal(result.positiveMarksEarned, 1);
  assert.equal(result.finalScore, 1);
});

test("unmatched multi-select response uses only the explicit unmatched mark", () => {
  const result = evaluateQuestionLevelAttempt([multi], { multi_1: "B,D" });
  assert.equal(result.wrongCount, 1);
  assert.equal(result.negativeMarksPenalty, 2);
  assert.equal(result.finalScore, 0);
});

test("binary marks remain available without changing their established behavior", () => {
  const questions = [{
    id: "q1", strategyId: "BINARY_MARKS", maxMarks: 4,
    positiveMarks: 4, negativeMarks: 1, correctAnswer: "C"
  }];
  const result = evaluateQuestionLevelAttempt(questions, { q1: "C" });
  assert.equal(result.correctCount, 1);
  assert.equal(result.finalScore, 4);
});

test("numeric tolerance uses explicit tolerance and finite numeric values", () => {
  const questions = [{
    id: "numeric", strategyId: "NUMERIC_TOLERANCE", maxMarks: 4,
    positiveMarks: 4, negativeMarks: 1, correctAnswer: "12.5",
    rubric: { tolerance: 0.01 }
  }];
  assert.equal(evaluateQuestionLevelAttempt(questions, { numeric: "12.509" }).finalScore, 4);
  assert.equal(evaluateQuestionLevelAttempt(questions, { numeric: "12.52" }).finalScore, 0);
  assert.throws(() => evaluateQuestionLevelAttempt(questions, { numeric: "NaN" }), { code: "failed-precondition" });
});

test("missing rubric, duplicate response rules, and unsupported strategy fail closed", () => {
  assert.throws(() => validateQuestion({ id: "x", strategyId: "MULTI_SELECT_PARTIAL_TABLE", maxMarks: 4 }), { code: "failed-precondition" });
  assert.throws(() => validateQuestion({ ...multi, rubric: { ...multi.rubric, rules: [...multi.rubric.rules, { response: "B,A", awardedMarks: 2, outcome: "PARTIAL" }] } }), { code: "failed-precondition" });
  assert.throws(() => validateQuestion({ id: "x", strategyId: "GUESS_MARKS", maxMarks: 4 }), { code: "failed-precondition" });
});

test("duplicate question IDs and malformed multi-select answers fail closed", () => {
  assert.throws(() => evaluateQuestionLevelAttempt([multi, multi], { multi_1: "A" }), { code: "failed-precondition" });
  assert.throws(() => evaluateQuestionLevelAttempt([multi], { multi_1: "A,A" }), { code: "failed-precondition" });
});

test("unanswered questions receive the explicit unanswered value and do not count as attempted", () => {
  const question = { ...multi, rubric: { ...multi.rubric, unansweredMarks: 0.5 } };
  const result = evaluateQuestionLevelAttempt([question], {});
  assert.equal(result.unansweredCount, 1);
  assert.equal(result.attemptedCount, 0);
  assert.equal(result.finalScore, 0.5);
});
