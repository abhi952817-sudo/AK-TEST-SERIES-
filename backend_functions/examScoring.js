/**
 * Authoritative Server-Side Exam Scoring Engine
 * Strictly mirrors UniversalExamRegistry.kt and ExamScoringEngine.kt.
 */

const VALID_ANSWER_OPTIONS = new Set(["A", "B", "C", "D", "E", "1", "2", "3", "4", "5"]);

function isAnswerKeyVerified(key) {
  if (!key) return false;
  const clean = String(key).trim().toUpperCase();
  if (clean.length === 0) return false;
  if (VALID_ANSWER_OPTIONS.has(clean) || !isNaN(Number(clean))) return true;
  if (clean.includes(",")) {
    const parts = clean.split(",").map(p => p.trim());
    if (parts.length > 0 && parts.every(p => VALID_ANSWER_OPTIONS.has(p) || !isNaN(Number(p)))) {
      return true;
    }
  }
  return false;
}

function areOptionsEquivalent(ans1, ans2, isNumerical = false) {
  if (!ans1 || !ans2) return false;
  const a = String(ans1).trim().toUpperCase();
  const b = String(ans2).trim().toUpperCase();
  if (a === b) return true;

  if (!isNumerical) {
    const mapIndex = { "1": "A", "2": "B", "3": "C", "4": "D", "5": "E" };
    if (mapIndex[a] === b || mapIndex[b] === a) return true;
  }

  const num1 = parseFloat(a);
  const num2 = parseFloat(b);
  if (!isNaN(num1) && !isNaN(num2)) {
    return Math.abs(num1 - num2) < 0.0001;
  }
  return false;
}

function isUserAnswerCorrect(userAns, correctAns, isNumerical = false) {
  if (!userAns || !correctAns) return false;
  const u = String(userAns).trim().toUpperCase();
  const c = String(correctAns).trim().toUpperCase();
  if (u.length === 0 || c.length === 0) return false;
  if (areOptionsEquivalent(u, c, isNumerical)) return true;

  if (c.includes(",")) {
    const acceptedKeys = c.split(",").map(k => k.trim());
    const userParts = u.split(",").map(k => k.trim()).sort();
    const correctParts = acceptedKeys.slice().sort();

    if (userParts.join(",") === correctParts.join(",")) return true;
    if (acceptedKeys.some(k => areOptionsEquivalent(u, k, isNumerical))) return true;
  }
  return false;
}

function getMarkingSchemeForExam(examName) {
  const name = String(examName || "").trim().toLowerCase();
  if (name.includes("neet")) return { pos: 4.0, neg: 1.0 };
  if (name.includes("jee main") || name === "jee_main" || name === "jee") return { pos: 4.0, neg: 1.0 };
  if (name.includes("jee adv") || name === "jee_advanced") return { pos: 4.0, neg: 2.0 };
  if (name.includes("cuet")) return { pos: 5.0, neg: 1.0 };
  if (name.includes("upsc") || name.includes("civil services")) return { pos: 2.0, neg: 0.66 };
  if (name.includes("cgl") || name.includes("ssc_cgl")) return { pos: 2.0, neg: 0.50 };
  if (name.includes("chsl") || name.includes("ssc_chsl")) return { pos: 2.0, neg: 0.50 };
  if (name.includes("mts") || name.includes("ssc_mts")) return { pos: 3.0, neg: 1.0 };
  if (name.includes("gate")) return { pos: 1.0, neg: 0.33 };
  if (name.includes("nda")) return { pos: 4.0, neg: 1.33 };
  if (name.includes("cds")) return { pos: 1.0, neg: 0.33 };
  if (name.includes("cat")) return { pos: 3.0, neg: 1.0 };
  if (name.includes("ctet") || name.includes("uptet")) return { pos: 1.0, neg: 0.0 };
  if (name.includes("rrb") || name.includes("ntpc")) return { pos: 1.0, neg: 0.33 };
  return { pos: 4.0, neg: 1.0 };
}

function getMarkingSchemeForQuestion(examName, sectionIdOrName = "", subjectName = "", questionType = "") {
  const name = String(examName || "").trim().toLowerCase();
  const sec = String(sectionIdOrName || "").trim().toLowerCase();
  const sub = String(subjectName || "").trim().toLowerCase();
  const type = String(questionType || "").trim().toUpperCase();

  // JEE Advanced
  if (name.includes("jee adv") || name === "jee_advanced") {
    const isMulti = sec.includes("multi") || type === "MCQ_MULTIPLE" || type === "MULTIPLE";
    const isNum = sec.includes("num") || sec.includes("nvq") || type === "NUMERICAL" || type === "NUMERICAL_VALUE" || type === "TITA";
    const isSingle = sec.includes("single") || type === "MCQ_SINGLE" || type === "MCQ";
    if (isMulti) return { pos: 4.0, neg: 2.0 };
    if (isNum) return { pos: 3.0, neg: 0.0 };
    if (isSingle) return { pos: 3.0, neg: 1.0 };
  }

  // NDA
  if (name.includes("nda")) {
    const isMath = sec.includes("math") || sub.includes("math");
    const isGat = sec.includes("gat") || sec.includes("eng") || sec.includes("gk") || sub.includes("gat") || sub.includes("eng") || sub.includes("gk") || sub.includes("science");
    if (isMath && !isGat) return { pos: 2.5, neg: 0.83 };
    if (isGat) return { pos: 4.0, neg: 1.33 };
  }

  // CDS
  if (name.includes("cds")) {
    const isMath = sec.includes("math") || sub.includes("math");
    if (isMath) return { pos: 1.0, neg: 0.33 };
    return { pos: 0.83, neg: 0.27 };
  }

  // GATE
  if (name.includes("gate")) {
    const is2Mark = sec.includes("2m") || sec.includes("2-mark") || sec.includes("2_mark") || sec.includes("2 mark") || sub.includes("2m");
    const isTechOrNum = sec.includes("tech") || type === "NUMERICAL_VALUE" || type === "NUMERICAL" || type === "TITA" || sub.includes("tech") || sub.includes("engineering");
    if (isTechOrNum) {
      return is2Mark ? { pos: 2.0, neg: 0.0 } : { pos: 1.0, neg: 0.0 };
    } else {
      return is2Mark ? { pos: 2.0, neg: 0.66 } : { pos: 1.0, neg: 0.33 };
    }
  }

  // SSC MTS
  if (name.includes("mts") || name.includes("ssc_mts")) {
    const isSession1 = sec.includes("session_1") || sec.includes("math") || sec.includes("reason") || sub.includes("session_1") || sub.includes("math") || sub.includes("reason");
    const isSession2 = sec.includes("session_2") || sec.includes("ga") || sec.includes("eng") || sub.includes("session_2") || sub.includes("awareness") || sub.includes("english");
    if (isSession2 && !isSession1) return { pos: 3.0, neg: 1.0 };
    if (isSession1) return { pos: 3.0, neg: 0.0 };
  }

  return getMarkingSchemeForExam(examName);
}

function hasMultipleMarkingSchemes(examName) {
  const name = String(examName || "").trim().toLowerCase();
  return name.includes("nda") || name.includes("cds") || name.includes("gate") || name.includes("mts") || name.includes("jee adv") || name === "jee_advanced";
}

function getMaximumAttemptQuestionsCountForSection(examName, sectionNameOrId, subjectName = "") {
  const name = String(examName || "").trim().toLowerCase();
  const sec = String(sectionNameOrId || "").trim().toLowerCase();

  if (sec === "section_b" || sec.includes("section b") || sec.includes("sec_b")) {
    if (name.includes("neet")) return 10;
    if (name.includes("jee")) return 5;
  }
  return null;
}

/**
 * Authoritative Evaluation of Test Attempt
 */
function evaluateTestAttempt(targetExam, questions, userAnswers, timeSpentSeconds = 0) {
  const exam = String(targetExam || "NEET");
  const baseScheme = getMarkingSchemeForExam(exam);
  const isMultiScheme = hasMultipleMarkingSchemes(exam);
  const isCat = exam.toLowerCase().includes("cat");

  let correctCount = 0;
  let wrongCount = 0;
  let unansweredCount = 0;

  let positiveMarksEarned = 0.0;
  let negativeMarksPenalty = 0.0;
  let maxPossible = 0.0;

  const sectionAttemptCounts = {};

  for (const q of questions) {
    const qId = q.id;
    const rawUserOpt = userAnswers[qId] ? (typeof userAnswers[qId] === "object" ? userAnswers[qId].ans : userAnswers[qId]) : null;
    const userOpt = rawUserOpt ? String(rawUserOpt).trim().toUpperCase() : "";
    const correctOpt = q.correctAnswer ? String(q.correctAnswer).trim().toUpperCase() : "";
    const isKeyVerified = isAnswerKeyVerified(correctOpt);

    const scheme = isMultiScheme
      ? getMarkingSchemeForQuestion(exam, q.section, q.subject, q.questionType)
      : baseScheme;

    maxPossible += scheme.pos;

    const isTitaOrNumerical = (q.questionType || "").toUpperCase() === "TITA" ||
      (q.questionType || "").toUpperCase() === "NUMERICAL" ||
      (q.questionType || "").toUpperCase() === "NUMERICAL_VALUE";

    const effectiveNegMark = (isCat && isTitaOrNumerical) ? 0.0 : scheme.neg;

    if (!userOpt || userOpt === "SKIPPED") {
      unansweredCount++;
    } else {
      const secKey = `${String(q.subject || '').toLowerCase().trim()}_${String(q.section || '').toLowerCase().trim()}`;
      const secLimit = getMaximumAttemptQuestionsCountForSection(exam, q.section, q.subject);
      const currentAttempts = sectionAttemptCounts[secKey] || 0;

      if (secLimit !== null && currentAttempts >= secLimit) {
        // Surplus question beyond section limit - ignored per exam rules
        unansweredCount++;
      } else {
        if (secLimit !== null) {
          sectionAttemptCounts[secKey] = currentAttempts + 1;
        }

        if (!isKeyVerified) {
          // Unverified answer key: 0 marks and no penalty
        } else if (isUserAnswerCorrect(userOpt, correctOpt, isTitaOrNumerical)) {
          correctCount++;
          positiveMarksEarned += scheme.pos;
        } else {
          wrongCount++;
          negativeMarksPenalty += effectiveNegMark;
        }
      }
    }
  }

  // Round maxPossible and calculate final scores
  const rawScore = positiveMarksEarned - negativeMarksPenalty;
  const finalScore = Math.max(0.0, rawScore);
  const attemptedCount = correctCount + wrongCount;
  const accuracyPercent = attemptedCount > 0 ? Math.round((correctCount / attemptedCount) * 10000) / 100 : 0.0;

  return {
    totalQuestions: questions.length,
    attemptedCount,
    correctCount,
    wrongCount,
    unansweredCount,
    positiveMarksEarned: Math.round(positiveMarksEarned * 100) / 100,
    negativeMarksPenalty: Math.round(negativeMarksPenalty * 100) / 100,
    rawScore: Math.round(rawScore * 100) / 100,
    finalScore: Math.round(finalScore * 100) / 100,
    maxScore: Math.round(maxPossible * 100) / 100,
    accuracyPercent,
    percentile: 0.0, // Population-based percentile computed against candidate cohort
    timeSpentSeconds,
    correctMarksPerQuestion: baseScheme.pos,
    negativeMarksPerQuestion: baseScheme.neg,
  };
}

/**
 * Calculates genuine population-based percentile rank:
 * P = ((totalEligible - rank + 1) / totalEligible) * 100
 * Handles all boundary conditions deterministically.
 */
function calculatePopulationPercentile(rank, totalEligible) {
  const n = Number(totalEligible);
  const r = Number(rank);
  if (isNaN(n) || n <= 0) return 0.0;
  if (isNaN(r) || r <= 0) return 0.0;
  if (n === 1) return 100.0;
  if (r > n) return Math.round((1.0 / n) * 10000) / 100;

  const raw = ((n - r + 1) / n) * 100.0;
  const rounded = Math.round(raw * 100) / 100;
  return r === 1 ? 100.0 : Math.min(99.99, Math.max(0.01, rounded));
}

/**
 * Standard NTA/testing score-based percentile:
 * P = 100 * (Number of candidates with score <= candidate's score) / totalCandidates
 */
function calculateScorePercentileFromCounts(countBelow, countEqual, totalCount) {
  const n = Number(totalCount);
  if (isNaN(n) || n <= 0) return 0.0;
  const below = Math.max(0, Number(countBelow) || 0);
  const equal = Math.max(1, Number(countEqual) || 1);
  const raw = ((below + equal) / n) * 100.0;
  const rounded = Math.round(raw * 100) / 100;
  return Math.min(100.0, Math.max(0.01, rounded));
}

/**
 * Merges trusted private answer keys into questions in a fail-closed manner.
 * 
 * Rules:
 * 1. Ranking-eligible tests:
 *    - privateKeyData must be non-null and contain a non-empty list of keys.
 *    - EVERY question in rawQuestions must have a matching trusted answer key.
 *    - Never falls back to public question.correctAnswer.
 *    - Missing or incomplete private key throws failed-precondition error.
 * 2. Non-ranking / practice tests:
 *    - Merges private key if present.
 *    - Falls back to public question properties if private key is absent.
 */
function validateAndMergeAnswerKeys(testData, rawQuestions, privateKeyData) {
  const countsTowardRanking = (testData.countsTowardRanking === true || (testData.countsTowardRanking !== false && testData.isOfficial === true)) &&
                              (testData.isOfficial === true || testData.isPublic === true);

  const keyMap = new Map();
  if (privateKeyData) {
    const keyList = privateKeyData.questions || privateKeyData.keys || [];
    for (const item of keyList) {
      if (item && item.id) {
        keyMap.set(String(item.id), item);
      }
    }
  }

  if (countsTowardRanking && (!privateKeyData || keyMap.size === 0)) {
    const err = new Error("Official ranking-eligible test requires verified server-side answer keys. None found in private storage.");
    err.code = "failed-precondition";
    throw err;
  }

  const mergedQuestions = rawQuestions.map(q => {
    const qId = String(q.id);
    if (keyMap.has(qId)) {
      const trusted = keyMap.get(qId);
      return {
        ...q,
        correctAnswer: trusted.correctAnswer,
        explanation: trusted.explanation || q.explanation || ""
      };
    } else {
      if (countsTowardRanking) {
        const err = new Error(`Official ranking-eligible test is missing trusted grading key for question ID: ${qId}`);
        err.code = "failed-precondition";
        throw err;
      }
      return {
        ...q,
        correctAnswer: q.correctAnswer || "",
        explanation: q.explanation || ""
      };
    }
  });

  const solutionKeys = {};
  for (const q of mergedQuestions) {
    if (q.id) {
      const qId = String(q.id);
      const trusted = keyMap.get(qId);
      solutionKeys[qId] = {
        correctAnswer: trusted ? trusted.correctAnswer : (countsTowardRanking ? "" : (q.correctAnswer || "")),
        explanation: trusted ? (trusted.explanation || "") : (countsTowardRanking ? "" : (q.explanation || ""))
      };
    }
  }

  return { questions: mergedQuestions, solutionKeys };
}

/**
 * Calculates exam-specific eligibility and qualifying test counts.
 * Strictly guarantees that qualifying tests in one exam (e.g. NEET) do not unlock
 * eligibility or count towards another exam (e.g. JEE Main).
 */
function getExamEligibility(userData, canonicalExam) {
  if (!userData) {
    return {
      qualifyingCount: 0,
      isEligible: false,
      competitiveScore: 0,
      minQualifyingTests: 2
    };
  }

  const examQCountKey = `qualifyingTestsCount_${canonicalExam}`;
  const qualifyingCount = typeof userData[examQCountKey] === "number" ? userData[examQCountKey] : 0;
  const examScoreKey = `cumulativeScore_${canonicalExam}`;
  const competitiveScore = typeof userData[examScoreKey] === "number" ? userData[examScoreKey] : 0;
  const isEligible = qualifyingCount >= 2;

  return {
    qualifyingCount,
    isEligible,
    competitiveScore,
    minQualifyingTests: 2
  };
}

module.exports = {
  VALID_ANSWER_OPTIONS,
  isAnswerKeyVerified,
  areOptionsEquivalent,
  isUserAnswerCorrect,
  getMarkingSchemeForExam,
  getMarkingSchemeForQuestion,
  hasMultipleMarkingSchemes,
  getMaximumAttemptQuestionsCountForSection,
  evaluateTestAttempt,
  calculatePopulationPercentile,
  calculateScorePercentileFromCounts,
  validateAndMergeAnswerKeys,
  getExamEligibility,
};
