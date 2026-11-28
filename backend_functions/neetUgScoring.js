"use strict";

/**
 * NEET UG 2026 source-backed scoring policy.
 * Source: NTA NEET (UG)-2026 Information Bulletin, Chapter 4, pp. 21-22.
 * This is deliberately separate from the later universal strategy dispatcher:
 * NEET 2026 is four-option, single-correct MCQ with +4/-1/0 marking.
 */
const NEET_UG_2026_POLICY = Object.freeze({
  examId: "neet_ug",
  examYear: "2026",
  scoringVersion: "neet-ug-2026-v1",
  stageId: "neet_main",
  paperId: "neet_paper_1",
  durationMinutes: 180,
  fullLengthQuestionCount: 180,
  maxMarks: 720,
  positiveMarks: 4,
  negativeMarks: 1,
  unansweredMarks: 0,
  subjectQuestionCounts: Object.freeze({ physics: 45, chemistry: 45, biology: 90 }),
  source: "https://nta.gov.in/cms/uploads/neet_Information_Bulletin_2026_6aee60b758.pdf",
  sourceTitle: "NEET (UG)-2026 Information Bulletin, Chapter 4"
});

const SINGLE_CORRECT_TYPES = new Set([
  "", "MCQ", "MCQ_SINGLE", "DIRECT_MCQ", "STATEMENT_I_II",
  "ASSERTION_REASON", "MATCH_LIST_I_II", "DIAGRAM_LABELING", "GRAPH"
]);
const VALID_OPTIONS = new Set(["A", "B", "C", "D"]);
const BONUS_STATUSES = new Set(["DROPPED", "NO_VALID_OPTION", "INVALID_QUESTION", "BONUS_ALL"]);
const ALL_CORRECT_STATUSES = new Set(["ALL_OPTIONS_CORRECT", "ALL_CORRECT"]);
const MULTIPLE_CORRECT_STATUSES = new Set(["MULTIPLE_CORRECT", "MULTI_CORRECT"]);

function scoringError(message, code = "failed-precondition") {
  const error = new Error(message);
  error.code = code;
  return error;
}

function isNeetUg2026Test(testData) {
  return Boolean(testData && String(testData.examId || "").trim().toLowerCase() === "neet_ug");
}

function validateNeetUg2026Identity(testData) {
  const policy = NEET_UG_2026_POLICY;
  if (!testData || String(testData.examId || "").trim().toLowerCase() !== policy.examId) {
    throw scoringError("NEET scoring requires canonical examId neet_ug.");
  }
  if (String(testData.examYear || "") !== policy.examYear) {
    throw scoringError("This NEET scoring engine supports only the explicitly verified 2026 policy. Missing or different examYear is not inferred.");
  }
  // Numeric version 1 is a documented compatibility alias for older app builds.
  // New uploads must write the canonical string version.
  const version = testData.scoringVersion === 1 || testData.scoringVersion === "1"
    ? policy.scoringVersion
    : String(testData.scoringVersion || "").trim();
  if (version !== policy.scoringVersion) {
    throw scoringError("The NEET exam scoringVersion is missing or unsupported.");
  }
  if (String(testData.stageId || "") !== policy.stageId ||
      String(testData.paperId || "") !== policy.paperId) {
    throw scoringError("The NEET stageId or paperId does not match the approved 2026 paper.");
  }
  return policy;
}

function normalizeSubject(question) {
  const raw = String(question.subject || question.subjectId || "").trim().toLowerCase();
  if (raw.includes("physics") || raw === "phy") return "physics";
  if (raw.includes("chemistry") || raw === "chem") return "chemistry";
  if (raw.includes("biology") || raw.includes("botany") || raw.includes("zoology") || raw === "bio") return "biology";
  return "unclassified";
}

function extractAnswer(value) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    value = value.ans;
  }
  if (value == null) return "";
  if (typeof value !== "string" && typeof value !== "number") {
    throw scoringError("NEET answers must be a single option string.");
  }
  const answer = String(value).trim().toUpperCase();
  return answer === "SKIPPED" ? "" : answer;
}

function acceptedKeys(question) {
  const raw = Array.isArray(question.acceptedAnswers)
    ? question.acceptedAnswers
    : String(question.correctAnswer || "").split(",");
  const keys = raw.map(value => String(value).trim().toUpperCase()).filter(Boolean);
  if (keys.length === 0 || keys.some(key => !VALID_OPTIONS.has(key))) {
    throw scoringError("A NEET question is missing a valid trusted A-D answer key.");
  }
  return new Set(keys);
}

function isFullLengthTest(testData, questions) {
  const type = String(testData.testType || testData.category || "").toLowerCase();
  return testData.isFullLength === true ||
    type.includes("full") ||
    questions.length === NEET_UG_2026_POLICY.fullLengthQuestionCount;
}

function evaluateNeetUgAttempt(testData, questions, userAnswers, timeSpentSeconds = 0) {
  const policy = validateNeetUg2026Identity(testData);
  if (!Array.isArray(questions) || questions.length < 1 || questions.length > policy.fullLengthQuestionCount) {
    throw scoringError("A NEET 2026 attempt must contain between 1 and 180 questions.");
  }
  if (!userAnswers || typeof userAnswers !== "object" || Array.isArray(userAnswers)) {
    throw scoringError("NEET answers must be an object keyed by question ID.");
  }
  if (!Number.isFinite(Number(timeSpentSeconds)) || Number(timeSpentSeconds) < 0 || Number(timeSpentSeconds) > 86400) {
    throw scoringError("NEET timeSpentSeconds is invalid.");
  }
  if (testData.questionsCount != null && Number(testData.questionsCount) !== questions.length) {
    throw scoringError("NEET source question count does not match its question records.");
  }

  const fullLength = isFullLengthTest(testData, questions);
  if (fullLength) {
    if (questions.length !== policy.fullLengthQuestionCount) {
      throw scoringError("A full-length NEET 2026 paper must contain exactly 180 compulsory questions.");
    }
    if (testData.durationMinutes != null && Number(testData.durationMinutes) !== policy.durationMinutes) {
      throw scoringError("A full-length NEET 2026 paper must use the official 180-minute duration.");
    }
  }

  const seen = new Set();
  const subjects = {
    physics: { total: 0, correct: 0, wrong: 0, unanswered: 0, bonus: 0, score: 0, maxScore: 0 },
    chemistry: { total: 0, correct: 0, wrong: 0, unanswered: 0, bonus: 0, score: 0, maxScore: 0 },
    biology: { total: 0, correct: 0, wrong: 0, unanswered: 0, bonus: 0, score: 0, maxScore: 0 },
    unclassified: { total: 0, correct: 0, wrong: 0, unanswered: 0, bonus: 0, score: 0, maxScore: 0 }
  };

  let correctCount = 0;
  let wrongCount = 0;
  let unansweredCount = 0;
  let bonusCount = 0;
  let positiveMarksEarned = 0;
  let negativeMarksPenalty = 0;
  let rawScore = 0;

  for (const question of questions) {
    if (!question || typeof question !== "object" || Array.isArray(question)) {
      throw scoringError("NEET test contains a malformed question.");
    }
    const id = String(question.id || "").trim();
    if (!id || seen.has(id)) throw scoringError("NEET question IDs must be present and unique.");
    seen.add(id);

    const questionType = String(question.questionType || "").trim().toUpperCase();
    if (!SINGLE_CORRECT_TYPES.has(questionType)) {
      throw scoringError("NEET 2026 accepts only four-option single-correct MCQs; unsupported question type: " + questionType);
    }

    const subject = normalizeSubject(question);
    const stat = subjects[subject];
    stat.total++;
    stat.maxScore += policy.positiveMarks;

    const answer = extractAnswer(userAnswers[id]);
    const status = String(question.scoringStatus || question.answerStatus || "NORMAL").trim().toUpperCase();

    if (BONUS_STATUSES.has(status)) {
      rawScore += policy.positiveMarks;
      positiveMarksEarned += policy.positiveMarks;
      bonusCount++;
      stat.bonus++;
      stat.score += policy.positiveMarks;
      continue;
    }

    if (ALL_CORRECT_STATUSES.has(status)) {
      if (!answer) {
        unansweredCount++;
        stat.unanswered++;
      } else {
        if (!VALID_OPTIONS.has(answer)) throw scoringError("NEET answer must be one option A, B, C, or D.");
        correctCount++;
        positiveMarksEarned += policy.positiveMarks;
        rawScore += policy.positiveMarks;
        stat.correct++;
        stat.score += policy.positiveMarks;
      }
      continue;
    }

    const keys = acceptedKeys(question);
    if (!answer) {
      unansweredCount++;
      stat.unanswered++;
      continue;
    }
    if (!VALID_OPTIONS.has(answer)) throw scoringError("NEET answer must be one option A, B, C, or D.");
    if (keys.has(answer)) {
      correctCount++;
      positiveMarksEarned += policy.positiveMarks;
      rawScore += policy.positiveMarks;
      stat.correct++;
      stat.score += policy.positiveMarks;
    } else {
      wrongCount++;
      negativeMarksPenalty += policy.negativeMarks;
      rawScore -= policy.negativeMarks;
      stat.wrong++;
      stat.score -= policy.negativeMarks;
    }
  }

  const unknownAnswerIds = Object.keys(userAnswers).filter(id => !seen.has(id));
  if (unknownAnswerIds.length) {
    throw scoringError("NEET answers contain unknown question IDs: " + unknownAnswerIds.sort().join(", ") + ".");
  }

  if (fullLength) {
    for (const [subject, expected] of Object.entries(policy.subjectQuestionCounts)) {
      if (subjects[subject].total !== expected) {
        throw scoringError("A full-length NEET 2026 paper requires " + expected + " " + subject + " questions.");
      }
    }
    if (subjects.unclassified.total !== 0) {
      throw scoringError("Every full-length NEET question must map to Physics, Chemistry, or Biology.");
    }
  }

  const round = value => Math.round((value + Number.EPSILON) * 100) / 100;
  const attemptedCount = correctCount + wrongCount;
  const subjectBreakdown = Object.fromEntries(Object.entries(subjects)
    .filter(([, value]) => value.total > 0)
    .map(([name, value]) => [name, {
      total: value.total,
      correct: value.correct,
      wrong: value.wrong,
      unanswered: value.unanswered,
      bonus: value.bonus,
      score: round(value.score),
      maxScore: round(value.maxScore)
    }]));

  return {
    totalQuestions: questions.length,
    attemptedCount,
    correctCount,
    partialCount: 0,
    wrongCount,
    unansweredCount,
    bonusCount,
    positiveMarksEarned: round(positiveMarksEarned),
    negativeMarksPenalty: round(negativeMarksPenalty),
    rawScore: round(rawScore),
    finalScore: round(rawScore),
    maxScore: round(questions.length * policy.positiveMarks),
    accuracyPercent: attemptedCount > 0 ? round((correctCount / attemptedCount) * 100) : 0,
    timeSpentSeconds: Number(timeSpentSeconds),
    subjectBreakdown,
    scoringEngine: "NEET_UG_2026_V1",
    scoringVersion: policy.scoringVersion,
    examYear: policy.examYear,
    scoringPolicySource: policy.source
  };
}

module.exports = {
  NEET_UG_2026_POLICY,
  isNeetUg2026Test,
  validateNeetUg2026Identity,
  evaluateNeetUgAttempt
};
