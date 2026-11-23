"use strict";

const STRATEGIES = new Set([
  "BINARY_MARKS",
  "EXACT_RESPONSE_TABLE",
  "MULTI_SELECT_PARTIAL_TABLE",
  "NUMERIC_TOLERANCE"
]);
const NUMBER_PATTERN = /^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/;

function scoringError(message) {
  const error = new Error(message);
  error.code = "failed-precondition";
  return error;
}

function finiteNumber(value, label) {
  const number = typeof value === "number" ? value :
    (typeof value === "string" && NUMBER_PATTERN.test(value.trim()) ? Number(value) : NaN);
  if (!Number.isFinite(number)) throw scoringError(label + " must be a finite number.");
  return number;
}

function canonicalResponse(value, strategyId) {
  if (typeof value !== "string" || !value.trim()) {
    throw scoringError("An answered question must contain a non-empty response string.");
  }
  const clean = value.trim().toUpperCase();
  if (strategyId !== "MULTI_SELECT_PARTIAL_TABLE") return clean;
  const parts = clean.split(",").map(part => part.trim());
  if (parts.some(part => !part) || new Set(parts).size !== parts.length) {
    throw scoringError("Multi-select responses must contain unique, non-empty selections.");
  }
  return parts.sort().join(",");
}

function validateQuestion(question) {
  if (!question || typeof question !== "object" || Array.isArray(question)) {
    throw scoringError("Malformed question scoring input.");
  }
  if (typeof question.id !== "string" || !question.id.trim()) {
    throw scoringError("Question ID is required for scoring.");
  }
  const strategyId = String(question.strategyId || "").trim().toUpperCase();
  if (!STRATEGIES.has(strategyId)) {
    throw scoringError("Unsupported or unverified scoring strategy: " + strategyId + ".");
  }
  const maxMarks = finiteNumber(question.maxMarks, "maxMarks");
  if (maxMarks <= 0) throw scoringError("maxMarks must be greater than zero.");

  if (strategyId === "BINARY_MARKS" || strategyId === "NUMERIC_TOLERANCE") {
    const positiveMarks = finiteNumber(question.positiveMarks, "positiveMarks");
    const negativeMarks = finiteNumber(question.negativeMarks, "negativeMarks");
    if (positiveMarks <= 0 || positiveMarks > maxMarks || negativeMarks < 0 || negativeMarks > maxMarks) {
      throw scoringError("Binary marks must fit within the question's maxMarks.");
    }
    if (strategyId === "NUMERIC_TOLERANCE") {
      const tolerance = finiteNumber(question.rubric && question.rubric.tolerance, "rubric.tolerance");
      if (tolerance < 0) throw scoringError("Numeric tolerance cannot be negative.");
      const expected = finiteNumber(question.correctAnswer, "correctAnswer");
      return { ...question, strategyId, maxMarks, positiveMarks, negativeMarks, tolerance, expected };
    }
    if (typeof question.correctAnswer !== "string" || !question.correctAnswer.trim()) {
      throw scoringError("BINARY_MARKS requires a verified correctAnswer.");
    }
    return { ...question, strategyId, maxMarks, positiveMarks, negativeMarks };
  }

  const rubric = question.rubric;
  if (!rubric || rubric.schemaVersion !== 1 || !Array.isArray(rubric.rules) || rubric.rules.length === 0) {
    throw scoringError("Table-driven strategies require rubric schemaVersion 1 and at least one rule.");
  }
  const unmatchedMarks = finiteNumber(rubric.unmatchedMarks, "rubric.unmatchedMarks");
  const unansweredMarks = rubric.unansweredMarks == null ? 0 : finiteNumber(rubric.unansweredMarks, "rubric.unansweredMarks");
  if (unmatchedMarks < -maxMarks || unmatchedMarks > maxMarks ||
      unansweredMarks < -maxMarks || unansweredMarks > maxMarks) {
    throw scoringError("Rubric fallback marks must be within the question's maximum score bounds.");
  }
  const seen = new Set();
  const rules = rubric.rules.map((rule, index) => {
    const response = canonicalResponse(rule && rule.response, strategyId);
    if (seen.has(response)) throw scoringError("Rubric contains duplicate normalized response rules.");
    seen.add(response);
    const awardedMarks = finiteNumber(rule.awardedMarks, "rubric.rules[" + index + "].awardedMarks");
    const outcome = String(rule.outcome || "").toUpperCase();
    if (!["CORRECT", "PARTIAL", "WRONG"].includes(outcome)) {
      throw scoringError("Every rubric rule requires CORRECT, PARTIAL, or WRONG outcome.");
    }
    if (awardedMarks < -maxMarks || awardedMarks > maxMarks) {
      throw scoringError("Rubric awarded marks exceed the question's allowed score bounds.");
    }
    return { response, awardedMarks, outcome };
  });
  return { ...question, strategyId, maxMarks, rubric: { ...rubric, unmatchedMarks, unansweredMarks, rules } };
}

function evaluateQuestionLevelAttempt(questions, userAnswers) {
  if (!Array.isArray(questions) || questions.length === 0 || questions.length > 450) {
    throw scoringError("Scoring requires between 1 and 450 questions.");
  }
  if (!userAnswers || typeof userAnswers !== "object" || Array.isArray(userAnswers)) {
    throw scoringError("userAnswers must be an object keyed by question ID.");
  }
  const seenIds = new Set();
  let positiveMarksEarned = 0;
  let negativeMarksPenalty = 0;
  let rawScore = 0;
  let maxScore = 0;
  let correctCount = 0;
  let partialCount = 0;
  let wrongCount = 0;
  let unansweredCount = 0;

  for (const rawQuestion of questions) {
    const question = validateQuestion(rawQuestion);
    if (seenIds.has(question.id)) throw scoringError("Duplicate question ID: " + question.id + ".");
    seenIds.add(question.id);
    maxScore += question.maxMarks;
    const rawAnswer = userAnswers[question.id];
    if (rawAnswer == null || (typeof rawAnswer === "string" && !rawAnswer.trim()) || (typeof rawAnswer === "string" && rawAnswer.trim().toUpperCase() === "SKIPPED")) {
      unansweredCount++;
      const marks = question.strategyId === "BINARY_MARKS" || question.strategyId === "NUMERIC_TOLERANCE"
        ? 0 : question.rubric.unansweredMarks;
      rawScore += marks;
      if (marks > 0) positiveMarksEarned += marks;
      if (marks < 0) negativeMarksPenalty += Math.abs(marks);
      continue;
    }

    let awardedMarks;
    let outcome;
    if (question.strategyId === "BINARY_MARKS") {
      const correct = String(rawAnswer).trim().toUpperCase() === question.correctAnswer.trim().toUpperCase();
      awardedMarks = correct ? question.positiveMarks : -question.negativeMarks;
      outcome = correct ? "CORRECT" : "WRONG";
    } else if (question.strategyId === "NUMERIC_TOLERANCE") {
      const answer = finiteNumber(rawAnswer, "numeric response");
      const correct = Math.abs(answer - question.expected) <= question.tolerance;
      awardedMarks = correct ? question.positiveMarks : -question.negativeMarks;
      outcome = correct ? "CORRECT" : "WRONG";
    } else {
      const response = canonicalResponse(rawAnswer, question.strategyId);
      const rule = question.rubric.rules.find(item => item.response === response);
      awardedMarks = rule ? rule.awardedMarks : question.rubric.unmatchedMarks;
      outcome = rule ? rule.outcome : "WRONG";
    }

    rawScore += awardedMarks;
    if (awardedMarks > 0) positiveMarksEarned += awardedMarks;
    if (awardedMarks < 0) negativeMarksPenalty += Math.abs(awardedMarks);
    if (outcome === "CORRECT") correctCount++;
    else if (outcome === "PARTIAL") partialCount++;
    else wrongCount++;
  }

  const round = value => Math.round((value + Number.EPSILON) * 100) / 100;
  const attemptedCount = correctCount + partialCount + wrongCount;
  return {
    totalQuestions: questions.length,
    attemptedCount,
    correctCount,
    partialCount,
    wrongCount,
    unansweredCount,
    positiveMarksEarned: round(positiveMarksEarned),
    negativeMarksPenalty: round(negativeMarksPenalty),
    rawScore: round(rawScore),
    finalScore: round(Math.max(0, rawScore)),
    maxScore: round(maxScore)
  };
}

module.exports = { STRATEGIES, validateQuestion, evaluateQuestionLevelAttempt };
