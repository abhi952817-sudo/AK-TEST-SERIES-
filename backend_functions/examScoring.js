const { resolveRankingWeight } = require("./rankingEngine");

/**
 * Authoritative Server-Side Exam Scoring Engine
 * Strictly mirrors UniversalExamRegistry.kt and ExamScoringEngine.kt.
 */

const VALID_ANSWER_OPTIONS = new Set(["A", "B", "C", "D", "E", "1", "2", "3", "4", "5"]);

function isFiniteNumericAnswer(value) {
  if (typeof value !== "string" || value.trim().length === 0) return false;
  const clean = value.trim();
  // Reject partial parses (for example "12abc"), NaN, and Infinity.
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(clean)) return false;
  return Number.isFinite(Number(clean));
}

function isAnswerKeyVerified(key) {
  if (key === null || key === undefined) return false;
  const clean = String(key).trim().toUpperCase();
  if (clean.length === 0) return false;
  const parts = clean.includes(",") ? clean.split(",").map(part => part.trim()) : [clean];
  if (parts.some(part => part.length === 0) || new Set(parts).size !== parts.length) return false;
  return parts.every(part => VALID_ANSWER_OPTIONS.has(part) || isFiniteNumericAnswer(part));
}

function areOptionsEquivalent(ans1, ans2, isNumerical = false) {
  if (ans1 === null || ans1 === undefined || ans2 === null || ans2 === undefined) return false;
  const a = String(ans1).trim().toUpperCase();
  const b = String(ans2).trim().toUpperCase();
  if (isNumerical && (!isFiniteNumericAnswer(a) || !isFiniteNumericAnswer(b))) return false;
  if (a === b) return true;

  if (!isNumerical) {
    const mapIndex = { "1": "A", "2": "B", "3": "C", "4": "D", "5": "E" };
    if (mapIndex[a] === b || mapIndex[b] === a) return true;
  }

  const num1 = Number(a);
  const num2 = Number(b);
  if (isFiniteNumericAnswer(a) && isFiniteNumericAnswer(b)) {
    return Math.abs(num1 - num2) < 0.0001;
  }
  return false;
}

function isMultiSelectQuestionType(questionType) {
  return ["MSQ", "MULTI_SELECT", "MULTIPLE_SELECT", "MULTIPLE_CORRECT", "MCQ_MULTIPLE"]
    .includes(String(questionType || "").trim().toUpperCase());
}

function isUserAnswerCorrect(userAns, correctAns, isNumerical = false, questionType = "") {
  if (userAns === null || userAns === undefined || correctAns === null || correctAns === undefined) return false;
  const u = String(userAns).trim().toUpperCase();
  const c = String(correctAns).trim().toUpperCase();
  if (u.length === 0 || c.length === 0) return false;

  if (isMultiSelectQuestionType(questionType)) {
    const normalizeOption = value => {
      const token = String(value).trim().toUpperCase();
      if (isNumerical) return token;
      const indexToLetter = { "1": "A", "2": "B", "3": "C", "4": "D", "5": "E" };
      return indexToLetter[token] || token;
    };
    const userParts = u.split(",").map(normalizeOption);
    const correctParts = c.split(",").map(normalizeOption);
    if (userParts.some(part => !part) || correctParts.some(part => !part)) return false;
    if (new Set(userParts).size !== userParts.length || new Set(correctParts).size !== correctParts.length) return false;
    if (userParts.length !== correctParts.length) return false;
    const correctSet = new Set(correctParts);
    return userParts.every(part => correctSet.has(part));
  }

  if (areOptionsEquivalent(u, c, isNumerical)) return true;
  if (c.includes(",")) {
    const acceptedKeys = c.split(",").map(k => k.trim());
    const userParts = u.split(",").map(k => k.trim()).sort();
    const correctParts = acceptedKeys.slice().sort();
    if (userParts.length === correctParts.length && userParts.join(",") === correctParts.join(",")) return true;
    return acceptedKeys.some(k => areOptionsEquivalent(u, k, isNumerical));
  }
  return false;
}

/**
 * Normalizes exam titles to canonical group identifiers.
 * Strictly preserves the distinct identities of all Railway exams (RRB NTPC, RRB Group D,
 * RRB ALP, RRB Technician, RRB JE) and other canonical exam IDs without loss.
 */
function normalizeExamKey(examName) {
  if (!examName || typeof examName !== "string" || examName.trim().length === 0) {
    return "UNKNOWN";
  }
  const name = examName.trim().toLowerCase();
  if (name === "all") return "ALL";

  // Medical: do not collapse NEET PG or other NEET variants into NEET UG.
  if (name === "neet" || name === "neet ug" || name === "neet_ug") return "NEET";
  if (name.includes("neet pg") || name.includes("neet_pg")) return "NEET_PG";

  // Engineering: generic JEE is ambiguous between Main and Advanced.
  if (name.includes("jee adv") || name === "jee_advanced") return "JEE_ADVANCED";
  if (name.includes("jee main") || name === "jee_main") return "JEE_MAIN";
  if (name.includes("gate")) return "GATE";

  // University
  if (name.includes("cuet")) return "CUET";

  // Civil Services & Defence
  if (name.includes("upsc") || name.includes("civil services")) return "UPSC";
  if (name.includes("nda")) return "NDA";
  if (name.includes("cds")) return "CDS";

  // SSC
  if (name.includes("cgl") || name.includes("ssc_cgl")) return "SSC_CGL";
  if (name.includes("chsl") || name.includes("ssc_chsl")) return "SSC_CHSL";
  if (name.includes("mts") || name.includes("ssc_mts")) return "SSC_MTS";
  if (name.includes("cpo") || name.includes("ssc_cpo")) return "SSC_CPO";
  if (name.includes("ssc_gd") || name.includes("ssc gd") || name === "gd") return "SSC_GD";

  // Banking
  if (name.includes("ibps_po") || name.includes("ibps po")) return "IBPS_PO";
  if (name.includes("ibps_clerk") || name.includes("ibps clerk")) return "IBPS_CLERK";
  if (name.includes("sbi_po") || name.includes("sbi po")) return "SBI_PO";
  if (name.includes("sbi_clerk") || name.includes("sbi clerk")) return "SBI_CLERK";

  // Teaching
  if (name.includes("ctet") || name.includes("uptet")) return "CTET";
  if (name.includes("ugc_net") || name.includes("ugc net") || name === "ugc" || (name.includes("net") && !name.includes("neet"))) return "UGC_NET";

  // Management
  if (name.includes("cat")) return "CAT";

  // Railways - DISTINCT CANONICAL IDENTITIES (Do not collapse!)
  if (name.includes("group_d") || name.includes("group d") || name.includes("level 1")) return "RRB_GROUP_D";
  if (name.includes("alp") || name.includes("loco pilot")) return "RRB_ALP";
  if (name.includes("technician") || name.includes("rrb_tech")) return "RRB_TECHNICIAN";
  if (name.includes("rrb_je") || name.includes("rrb je") || name.includes("junior engineer")) return "RRB_JE";
  if (name.includes("ntpc") || name.includes("rrb_ntpc")) return "RRB_NTPC";
  // Bare RRB/Railway is ambiguous; preserve it as an unsupported identity rather than guessing NTPC.

  // Preserve canonical string format without collapsing into NEET or RRB NTPC
  return examName.trim().toUpperCase().replace(/\s+/g, "_");
}

// Only source-verified NEET UG 2026 and JEE Main Paper 1 2027 patterns may be scored server-side.
// Keep this allowlist and exact paper identities aligned with UniversalExamRegistry.kt. Unverified exams fail closed.
const VERIFIED_PAPER_IDENTITIES = Object.freeze({
  NEET: Object.freeze({ stageId: "neet_main", paperId: "neet_paper_1" }),
  JEE_MAIN: Object.freeze({ stageId: "jee_paper_1", paperId: "jee_main_p1" })
});

const VERIFIED_FULL_TEST_PATTERNS = Object.freeze({
  NEET: Object.freeze({
    totalQuestions: 180,
    sections: Object.freeze({
      "physics:phy_all": 45,
      "chemistry:chem_all": 45,
      "botany:bot_all": 45,
      "zoology:zoo_all": 45
    })
  }),
  JEE_MAIN: Object.freeze({
    totalQuestions: 75,
    sections: Object.freeze({
      "physics:phy_sec_a": 20,
      "physics:phy_sec_b": 5,
      "chemistry:chem_sec_a": 20,
      "chemistry:chem_sec_b": 5,
      "mathematics:math_sec_a": 20,
      "mathematics:math_sec_b": 5
    })
  })
});

function hasFullTestQuestionCoverage(examName, questions) {
  try {
    validateFullTestQuestionCoverage(examName, questions);
    return true;
  } catch (_) {
    return false;
  }
}

function validateFullTestQuestionCoverage(examName, questions) {
  const examKey = normalizeExamKey(examName);
  const pattern = VERIFIED_FULL_TEST_PATTERNS[examKey];
  if (!pattern) throw new Error("No source-verified full-test pattern exists for this exam.");
  if (!Array.isArray(questions) || questions.length !== pattern.totalQuestions) {
    throw new Error(`Full test must contain exactly ${pattern.totalQuestions} verified questions.`);
  }
  const counts = new Map();
  for (const question of questions) {
    const subject = normalizeScoringSubject(question.subject);
    const section = String(question.section || "").trim().toLowerCase();
    const key = subject + ":" + section;
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  const expectedKeys = Object.keys(pattern.sections);
  if (counts.size !== expectedKeys.length ||
      expectedKeys.some(key => counts.get(key) !== pattern.sections[key])) {
    throw new Error("Full test subject/section distribution does not match the verified exam paper.");
  }
  return true;
}

const VERIFIED_SCORING_POLICIES = Object.freeze({
  NEET: Object.freeze({
    physics: Object.freeze({ phy_all: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }) }),
    chemistry: Object.freeze({ chem_all: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }) }),
    botany: Object.freeze({ bot_all: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }) }),
    zoology: Object.freeze({ zoo_all: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }) })
  }),
  JEE_MAIN: Object.freeze({
    physics: Object.freeze({
      phy_sec_a: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }),
      phy_sec_b: Object.freeze({ questionType: "NUMERICAL_VALUE", pos: 4.0, neg: 1.0, optionsCount: 0, integerAnswerRequired: true })
    }),
    chemistry: Object.freeze({
      chem_sec_a: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }),
      chem_sec_b: Object.freeze({ questionType: "NUMERICAL_VALUE", pos: 4.0, neg: 1.0, optionsCount: 0, integerAnswerRequired: true })
    }),
    mathematics: Object.freeze({
      math_sec_a: Object.freeze({ questionType: "MCQ_SINGLE", pos: 4.0, neg: 1.0, optionsCount: 4 }),
      math_sec_b: Object.freeze({ questionType: "NUMERICAL_VALUE", pos: 4.0, neg: 1.0, optionsCount: 0, integerAnswerRequired: true })
    })
  })
});

function normalizeScoringSubject(subjectName) {
  const subject = String(subjectName || "").trim().toLowerCase().replace(/[^a-z]/g, "");
  if (subject === "physics" || subject === "phy") return "physics";
  if (subject === "chemistry" || subject === "chem") return "chemistry";
  if (subject === "botany") return "botany";
  if (subject === "zoology") return "zoology";
  if (subject === "mathematics" || subject === "math" || subject === "maths") return "mathematics";
  return "";
}

function resolveVerifiedQuestionPolicy(examName, question = {}) {
  const examKey = normalizeExamKey(examName);
  const examPolicy = VERIFIED_SCORING_POLICIES[examKey];
  if (!examPolicy) return null;
  const subjectKey = normalizeScoringSubject(question.subject);
  const sectionKey = String(question.section || "").trim().toLowerCase();
  const subjectPolicy = examPolicy[subjectKey];
  if (!subjectPolicy || !sectionKey || !subjectPolicy[sectionKey]) return null;
  const policy = subjectPolicy[sectionKey];
  const type = String(question.questionType || "").trim().toUpperCase();
  const acceptedTypes = policy.questionType === "MCQ_SINGLE"
    ? new Set(["MCQ_SINGLE", "MCQ", "DIRECT_MCQ"])
    : new Set(["NUMERICAL_VALUE", "NUMERICAL", "TITA", "NAT"]);
  if (!acceptedTypes.has(type)) return null;
  return policy;
}

function getMarkingSchemeForExam(examName) {
  const examKey = normalizeExamKey(examName);
  if (!VERIFIED_SCORING_POLICIES[examKey]) {
    const err = new Error(`Scoring pattern is not source-verified for exam "${examName}".`);
    err.code = "failed-precondition";
    throw err;
  }
  return { pos: 4.0, neg: 1.0 };
}

function getMarkingSchemeForQuestion(examName, sectionIdOrName = "", subjectName = "", questionType = "") {
  const policy = resolveVerifiedQuestionPolicy(examName, {
    section: sectionIdOrName,
    subject: subjectName,
    questionType
  });
  if (!policy) {
    const err = new Error("Question subject, section, or type is not part of a source-verified exam pattern.");
    err.code = "failed-precondition";
    throw err;
  }
  return { pos: policy.pos, neg: policy.neg };
}

function hasMultipleMarkingSchemes(examName) {
  // The two currently source-verified exams use the same +4/-1 marks in every section.
  return false;
}

function getMaximumAttemptQuestionsCountForSection(examName, sectionNameOrId, subjectName = "") {
  // NEET UG 2026 and JEE Main 2026 have no optional Section-B attempt cap.
  // Other exam patterns are not enabled until official source verification is complete.
  return null;
}

/**
 * Authoritative Evaluation of Test Attempt
 */
function evaluateTestAttempt(targetExam, questions, userAnswers, timeSpentSeconds = 0) {
  if (typeof targetExam !== "string" || targetExam.trim().length === 0) {
    const err = new Error("An explicit supported exam identity is required for scoring.");
    err.code = "invalid-argument";
    throw err;
  }
  if (!Array.isArray(questions)) {
    const err = new Error("Question payload must be an array.");
    err.code = "invalid-argument";
    throw err;
  }
  const questionIds = questions.map(question => String(question && question.id || "").trim());
  if (questionIds.some(id => !id) || new Set(questionIds).size !== questionIds.length) {
    const err = new Error("Question payload contains missing or duplicate question IDs.");
    err.code = "invalid-argument";
    throw err;
  }
  const exam = targetExam.trim();
  const baseScheme = getMarkingSchemeForExam(exam);

  let correctCount = 0;
  let wrongCount = 0;
  let unansweredCount = 0;
  let ungradedCount = 0;
  let positiveMarksEarned = 0.0;
  let negativeMarksPenalty = 0.0;
  let maxPossible = 0.0;

  for (const q of questions) {
    const policy = resolveVerifiedQuestionPolicy(exam, q);
    if (!policy) {
      // Invalid/missing subject, section or question type invalidates the result even
      // when the student left that question unanswered. Never score by a generic fallback.
      ungradedCount++;
      continue;
    }

    const scheme = { pos: policy.pos, neg: policy.neg };
    maxPossible += scheme.pos;
    const qId = q.id;
    const answerValue = userAnswers && Object.prototype.hasOwnProperty.call(userAnswers, qId)
      ? userAnswers[qId]
      : null;
    const rawUserOpt = answerValue !== null && typeof answerValue === "object"
      ? answerValue.ans
      : answerValue;
    const userOpt = rawUserOpt === null || rawUserOpt === undefined
      ? ""
      : String(rawUserOpt).trim().toUpperCase();
    const correctOpt = q.correctAnswer === null || q.correctAnswer === undefined
      ? ""
      : String(q.correctAnswer).trim().toUpperCase();

    const isNumerical = policy.questionType === "NUMERICAL_VALUE";
    const isValidKey = isNumerical
      ? isFiniteNumericAnswer(correctOpt) &&
        (!policy.integerAnswerRequired || Number.isInteger(Number(correctOpt)))
      : ["A", "B", "C", "D", "1", "2", "3", "4"].includes(correctOpt);
    if (!isValidKey) {
      ungradedCount++;
      continue;
    }

    if (!userOpt || userOpt === "SKIPPED") {
      unansweredCount++;
      continue;
    }

    if (isUserAnswerCorrect(userOpt, correctOpt, isNumerical, q.questionType)) {
      correctCount++;
      positiveMarksEarned += scheme.pos;
    } else {
      wrongCount++;
      negativeMarksPenalty += scheme.neg;
    }
  }

  const rawScore = positiveMarksEarned - negativeMarksPenalty;
  const finalScore = Math.max(0.0, rawScore);
  const attemptedCount = correctCount + wrongCount + ungradedCount;
  const accuracyDenominator = correctCount + wrongCount;
  const accuracyPercent = accuracyDenominator > 0
    ? Math.round((correctCount / accuracyDenominator) * 10000) / 100
    : 0.0;

  return {
    totalQuestions: questions.length,
    attemptedCount,
    correctCount,
    wrongCount,
    unansweredCount,
    ungradedCount,
    positiveMarksEarned: Math.round(positiveMarksEarned * 100) / 100,
    negativeMarksPenalty: Math.round(negativeMarksPenalty * 100) / 100,
    rawScore: Math.round(rawScore * 100) / 100,
    finalScore: Math.round(finalScore * 100) / 100,
    maxScore: Math.round(maxPossible * 100) / 100,
    accuracyPercent,
    percentile: 0.0,
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
  if (!Array.isArray(rawQuestions)) {
    const err = new Error("Test question payload must be an array.");
    err.code = "failed-precondition";
    throw err;
  }
  const questionIds = rawQuestions.map(question => String(question && question.id || "").trim());
  if (questionIds.some(id => !id) || new Set(questionIds).size !== questionIds.length) {
    const err = new Error("Test contains missing or duplicate question IDs; scoring would be ambiguous.");
    err.code = "failed-precondition";
    throw err;
  }
  const status = String(testData.status || "").trim();
  const countsTowardRanking = testData.isOfficial === true &&
    testData.isPublic === true &&
    testData.countsTowardRanking === true &&
    ["APPROVED", "PUBLISHED", "Published"].includes(status);

  const keyMap = new Map();
  if (privateKeyData) {
    const keyList = privateKeyData.questions || privateKeyData.keys || [];
    for (const item of keyList) {
      if (item && item.id) {
        const keyId = String(item.id);
        if (keyMap.has(keyId)) {
          const err = new Error(`Duplicate trusted answer-key ID: ${keyId}`);
          err.code = "failed-precondition";
          throw err;
        }
        keyMap.set(keyId, item);
      }
    }
  }

  if (countsTowardRanking && (!privateKeyData || keyMap.size === 0)) {
    const err = new Error("Official ranking-eligible test requires verified server-side answer keys. None found in private storage.");
    err.code = "failed-precondition";
    throw err;
  }
  if (countsTowardRanking && [...keyMap.keys()].some(id => !questionIds.includes(id))) {
    const err = new Error("Private answer-key document contains IDs that are not present in the test question set.");
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

  if (countsTowardRanking) {
    const examIdentity = resolveCanonicalExamIdentity(testData);
    const rankingWeight = resolveRankingWeight(testData);
    if (rankingWeight === null) {
      const err = new Error("Official ranking test classification is missing or conflicting.");
      err.code = "failed-precondition";
      throw err;
    }
    if (rankingWeight === 1) {
      try {
        validateFullTestQuestionCoverage(examIdentity, mergedQuestions);
      } catch (coverageError) {
        const err = new Error(coverageError.message);
        err.code = "failed-precondition";
        throw err;
      }
    } else if (rankingWeight === 0.2 && hasFullTestQuestionCoverage(examIdentity, mergedQuestions)) {
      const err = new Error("A complete verified full-paper composition cannot be classified as a part test.");
      err.code = "failed-precondition";
      throw err;
    }
    for (const q of mergedQuestions) {
      const questionText = String(q.questionText || q.questionTextEn || q.question || "").trim();
      if (!questionText) {
        const err = new Error("Ranking-eligible question is missing question text.");
        err.code = "failed-precondition";
        throw err;
      }
      const policy = resolveVerifiedQuestionPolicy(examIdentity, q);
      if (!policy) {
        const err = new Error("Ranking-eligible question has an unsupported subject, section, or question type.");
        err.code = "failed-precondition";
        throw err;
      }
      const answer = String(q.correctAnswer || "").trim().toUpperCase();
      const isNumerical = policy.questionType === "NUMERICAL_VALUE";
      const validAnswer = isNumerical
        ? isFiniteNumericAnswer(answer) &&
          (!policy.integerAnswerRequired || Number.isInteger(Number(answer)))
        : ["A", "B", "C", "D"].includes(answer);
      if (!validAnswer) {
        const err = new Error("Ranking-eligible question has an invalid trusted answer key.");
        err.code = "failed-precondition";
        throw err;
      }
      const arrayOptions = Array.isArray(q.options)
        ? q.options.map(option => String(
          option && typeof option === "object" ? (option.text || option.value || option.label || "") : option || ""
        ).trim())
        : [];
      const optionFields = ["A", "B", "C", "D", "E"].map(letter =>
        String(q[`option${letter}En`] ?? q[`option${letter}`] ?? "").trim()
      );
      const options = arrayOptions.length > 0
        ? arrayOptions
        : optionFields.slice(0, Math.max(4, policy.optionsCount));
      if (policy.optionsCount > 0) {
        if (options.length !== policy.optionsCount || options.some(option => !option) ||
            new Set(options.map(option => option.toLowerCase())).size !== options.length) {
          const err = new Error("Ranking-eligible MCQ has missing, duplicate, or incorrectly counted options.");
          err.code = "failed-precondition";
          throw err;
        }
      } else if (arrayOptions.some(Boolean) || optionFields.some(Boolean)) {
        const err = new Error("Ranking-eligible numerical-value questions must not contain MCQ options.");
        err.code = "failed-precondition";
        throw err;
      }
      if (q.positiveMarks !== undefined && Number(q.positiveMarks) !== policy.pos) {
        const err = new Error("Question positive marks conflict with the verified section policy.");
        err.code = "failed-precondition";
        throw err;
      }
      if (q.negativeMarks !== undefined && Number(q.negativeMarks) !== policy.neg) {
        const err = new Error("Question negative marks conflict with the verified section policy.");
        err.code = "failed-precondition";
        throw err;
      }
    }
  }

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
 * Resolve the test's canonical exam identity without guessing from its subject or defaulting to NEET.
 * Prefer the explicit canonical examId, then the display targetExam for legacy records.
 */
function resolveCanonicalExamIdentity(testData = {}) {
  const supplied = [testData.examId, testData.targetExam]
    .filter(value => typeof value === "string" && value.trim().length > 0)
    .map(value => normalizeExamKey(value));
  if (supplied.length === 0) {
    const err = new Error("Test is missing a canonical examId/targetExam; refusing to score or rank it under a guessed exam.");
    err.code = "failed-precondition";
    throw err;
  }
  if (supplied.some(value => !value || value === "UNKNOWN" || value === "ALL")) {
    const err = new Error("Test has an invalid or non-specific exam identity; refusing to score or rank it.");
    err.code = "failed-precondition";
    throw err;
  }
  const unique = [...new Set(supplied)];
  if (unique.length !== 1) {
    const err = new Error("Conflicting examId and targetExam values; refusing to route this test into either exam leaderboard.");
    err.code = "failed-precondition";
    throw err;
  }
  // Identity alone is insufficient: only exams with an enabled, source-verified
  // scoring policy may be scored or rebuilt into ranking materializations.
  try {
    getMarkingSchemeForExam(unique[0]);
  } catch (_) {
    const err = new Error("This exam has no source-verified scoring policy; scoring and ranking are disabled.");
    err.code = "failed-precondition";
    throw err;
  }
  const suppliedIdentityText = [testData.examId, testData.targetExam]
    .filter(value => typeof value === "string")
    .join(" ").toLowerCase();
  if (unique[0] === "JEE_MAIN" && /paper\s*2[ab]|b\.?\s*arch|b\.?\s*planning/.test(suppliedIdentityText)) {
    const err = new Error("JEE Main Paper 2A/2B is not part of the currently verified Paper 1 scoring configuration.");
    err.code = "failed-precondition";
    throw err;
  }
  const paperIdentity = VERIFIED_PAPER_IDENTITIES[unique[0]];
  if (!paperIdentity ||
      String(testData.stageId || "").trim() !== paperIdentity.stageId ||
      String(testData.paperId || "").trim() !== paperIdentity.paperId) {
    const err = new Error("The test stageId/paperId does not match the source-verified exam paper. Scoring and ranking are blocked.");
    err.code = "failed-precondition";
    throw err;
  }
  return unique[0];
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
  validateFullTestQuestionCoverage,
  hasFullTestQuestionCoverage,
  getExamEligibility,
  normalizeExamKey,
  resolveCanonicalExamIdentity,
};
