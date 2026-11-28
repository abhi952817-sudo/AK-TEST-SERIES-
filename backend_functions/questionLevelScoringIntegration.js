"use strict";

const crypto = require("node:crypto");

function scoringError(message) {
  const error = new Error(message);
  error.code = "failed-precondition";
  return error;
}

function createQuestionLevelTestFingerprint(testId, testData, questions) {
  if (!testData || !Array.isArray(questions) || questions.length === 0) {
    throw scoringError("A complete test and question list are required for rubric binding.");
  }
  const questionIdentity = questions.map(question => ({
    id: String(question && question.id || ""),
    subjectId: String(question && question.subjectId || ""),
    sectionId: String(question && question.sectionId || ""),
    questionType: String(question && question.questionType || "").toUpperCase()
  })).sort((a, b) => a.id.localeCompare(b.id));
  if (questionIdentity.some(item => !item.id || !item.subjectId || !item.sectionId || !item.questionType)) {
    throw scoringError("Every question requires immutable ID, subject, section, and question type for rubric binding.");
  }
  const canonical = JSON.stringify({
    testId: String(testId),
    examId: String(testData.examId || ""),
    examYear: String(testData.examYear || ""),
    scoringVersion: String(testData.scoringVersion || ""),
    stageId: String(testData.stageId || ""),
    paperId: String(testData.paperId || ""),
    questions: questionIdentity
  });
  return crypto.createHash("sha256").update(canonical).digest("hex");
}

function buildApprovedQuestionLevelQuestions(registry, testId, fingerprint, questions) {
  if (!registry || registry.schemaVersion !== 1 || registry.status !== "ACTIVE" ||
      !Array.isArray(registry.tests)) {
    throw scoringError("The question-level rubric registry is missing, inactive, or unsupported.");
  }
  const matches = registry.tests.filter(item => item && item.testId === testId);
  if (matches.length !== 1) throw scoringError("Exactly one server-approved rubric binding is required for this test.");
  const binding = matches[0];
  if (binding.status !== "APPROVED" || binding.testFingerprint !== fingerprint ||
      typeof binding.approvedBy !== "string" || !binding.approvedBy.trim() ||
      !binding.approvedAt || !Number.isFinite(Date.parse(binding.approvedAt)) ||
      !binding.source || typeof binding.source.url !== "string" ||
      !/^https:\/\//i.test(binding.source.url) ||
      typeof binding.source.title !== "string" || !binding.source.title.trim() ||
      !binding.rubricVersion || !Array.isArray(binding.questions)) {
    throw scoringError("The rubric binding is not approved, source-backed, versioned, or bound to this exact test revision.");
  }

  const rulesById = new Map();
  for (const item of binding.questions) {
    if (!item || typeof item.id !== "string" || !item.id.trim() || rulesById.has(item.id)) {
      throw scoringError("The approved rubric contains a missing or duplicate question ID.");
    }
    rulesById.set(item.id, item);
  }
  if (rulesById.size !== questions.length) {
    throw scoringError("The approved rubric must cover every question exactly once.");
  }

  return questions.map(question => {
    const rubric = rulesById.get(String(question.id));
    if (!rubric) throw scoringError("The approved rubric is missing question " + question.id + ".");
    const strategyId = String(rubric.strategyId || "").trim().toUpperCase();
    const maxMarks = Number(rubric.maxMarks);
    if (!Number.isFinite(maxMarks) || maxMarks <= 0) {
      throw scoringError("Approved rubric maxMarks must be finite and positive.");
    }
    const merged = {
      id: String(question.id),
      strategyId,
      maxMarks,
      positiveMarks: rubric.positiveMarks,
      negativeMarks: rubric.negativeMarks,
      rubric: rubric.rubric
    };
    if (strategyId === "BINARY_MARKS" || strategyId === "NUMERIC_TOLERANCE") {
      if (question.correctAnswer == null || String(question.correctAnswer).trim() === "") {
        throw scoringError("A trusted private answer key is required for " + question.id + ".");
      }
      merged.correctAnswer = question.correctAnswer;
    }
    return merged;
  });
}

module.exports = {
  createQuestionLevelTestFingerprint,
  buildApprovedQuestionLevelQuestions
};
