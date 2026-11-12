const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  isAnswerKeyVerified,
  areOptionsEquivalent,
  isUserAnswerCorrect,
  getMarkingSchemeForExam,
  getMarkingSchemeForQuestion,
  evaluateTestAttempt,
  calculatePopulationPercentile,
  calculateScorePercentileFromCounts,
  normalizeExamKey,
  resolveCanonicalExamIdentity,
  validateAndMergeAnswerKeys
} = require("./examScoring.js");

test("answer keys reject empty, malformed and non-finite values", () => {
  assert.equal(isAnswerKeyVerified("A"), true);
  assert.equal(isAnswerKeyVerified("4"), true);
  assert.equal(isAnswerKeyVerified("42.5"), true);
  assert.equal(isAnswerKeyVerified("A,C"), true);
  for (const value of ["", "INVALID", "NaN", "Infinity", "12abc", "A,,B", "A,A"]) {
    assert.equal(isAnswerKeyVerified(value), false);
  }
});

test("option equivalence handles index aliases and numeric precision", () => {
  assert.equal(areOptionsEquivalent("1", "A"), true);
  assert.equal(areOptionsEquivalent("B", "2"), true);
  assert.equal(areOptionsEquivalent("10.0", "10.000", true), true);
  assert.equal(areOptionsEquivalent("3.14", "3.15", true), false);
  assert.equal(areOptionsEquivalent("12abc", "12", true), false);
});

test("multi-select comparison requires the exact selected set", () => {
  assert.equal(isUserAnswerCorrect("A,B", "B,A", false, "MSQ"), true);
  assert.equal(isUserAnswerCorrect("1,2", "A,B", false, "MSQ"), true);
  assert.equal(isUserAnswerCorrect("A", "A,B", false, "MSQ"), false);
  assert.equal(isUserAnswerCorrect("A,B,C", "A,B", false, "MSQ"), false);
  assert.equal(isUserAnswerCorrect("A,A", "A,B", false, "MSQ"), false);
});

test("canonical exam identity never infers an exam from a subject or ambiguous alias", () => {
  assert.equal(normalizeExamKey("NEET UG"), "NEET");
  assert.equal(normalizeExamKey("JEE Main Paper 1"), "JEE_MAIN");
  assert.equal(normalizeExamKey("JEE"), "JEE");
  assert.equal(normalizeExamKey("NEET PG"), "NEET_PG");
  assert.equal(normalizeExamKey("RRB"), "RRB");
  assert.equal(normalizeExamKey(""), "UNKNOWN");
  assert.equal(resolveCanonicalExamIdentity({ examId: "neet_ug", targetExam: "NEET UG", stageId: "neet_main", paperId: "neet_paper_1" }), "NEET");
  assert.equal(resolveCanonicalExamIdentity({ examId: "jee_main", targetExam: "JEE Main Paper 1", stageId: "jee_paper_1", paperId: "jee_main_p1" }), "JEE_MAIN");
  assert.throws(
    () => resolveCanonicalExamIdentity({ examId: "neet_ug", targetExam: "JEE Main" }),
    { code: "failed-precondition" }
  );
  assert.throws(() => resolveCanonicalExamIdentity({ examId: "JEE Advanced" }), { code: "failed-precondition" });
  assert.throws(() => resolveCanonicalExamIdentity({ examId: "CUET UG" }), { code: "failed-precondition" });
  assert.throws(
    () => resolveCanonicalExamIdentity({ examId: "jee_main", stageId: "jee_paper_1", paperId: "jee_main_p2a" }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveCanonicalExamIdentity({
      examId: "jee_main", targetExam: "JEE Main Paper 2A",
      stageId: "jee_paper_1", paperId: "jee_main_p1"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveCanonicalExamIdentity({ examId: "neet_ug", stageId: "neet_main" }),
    { code: "failed-precondition" }
  );
  assert.throws(() => resolveCanonicalExamIdentity({ subject: "Biology" }), { code: "failed-precondition" });
  assert.throws(() => resolveCanonicalExamIdentity({ examId: "ALL" }), { code: "failed-precondition" });
});

test("only source-verified NEET UG 2026 and JEE Main 2027 scoring schemes are enabled", () => {
  assert.deepEqual(getMarkingSchemeForExam("NEET UG"), { pos: 4, neg: 1 });
  assert.deepEqual(getMarkingSchemeForExam("JEE Main"), { pos: 4, neg: 1 });
  for (const exam of ["JEE", "JEE Advanced", "NEET PG", "CUET UG", "UPSC CSE", "SSC CGL", "SSC CHSL", "SSC MTS", "SSC GD", "SSC CPO", "RRB NTPC", "RRB Group D", "RRB ALP", "RRB Technician", "RRB JE", "CTET", "UGC NET", "CAT", "IBPS PO", "IBPS Clerk", "SBI PO", "SBI Clerk", "NDA", "CDS", "GATE"]) {
    assert.throws(() => getMarkingSchemeForExam(exam), { code: "failed-precondition" });
  }
});

test("NEET question marking requires exact subject, section and question type", () => {
  assert.deepEqual(getMarkingSchemeForQuestion("NEET UG", "phy_all", "Physics", "MCQ_SINGLE"), { pos: 4, neg: 1 });
  assert.throws(() => getMarkingSchemeForQuestion("NEET UG", "SECTION_A", "Physics", "MCQ_SINGLE"), { code: "failed-precondition" });
  assert.throws(() => getMarkingSchemeForQuestion("NEET UG", "phy_all", "Biology", "MCQ_SINGLE"), { code: "failed-precondition" });
  assert.throws(() => getMarkingSchemeForQuestion("NEET UG", "phy_all", "Physics", "MCQ_MULTIPLE"), { code: "failed-precondition" });
});

test("NEET UG 2026 scoring uses 180 compulsory single-correct questions and +4/-1", () => {
  const questions = [
    { id: "q1", correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" },
    { id: "q2", correctAnswer: "B", section: "chem_all", subject: "Chemistry", questionType: "MCQ_SINGLE" },
    { id: "q3", correctAnswer: "C", section: "bot_all", subject: "Botany", questionType: "MCQ_SINGLE" },
    { id: "q4", correctAnswer: "NOT_SET", section: "zoo_all", subject: "Zoology", questionType: "MCQ_SINGLE" }
  ];
  const result = evaluateTestAttempt("NEET UG", questions, {
    q1: { ans: "A" },
    q2: { ans: "C" },
    q4: { ans: "A" }
  }, 120);
  assert.equal(result.totalQuestions, 4);
  assert.equal(result.correctCount, 1);
  assert.equal(result.wrongCount, 1);
  assert.equal(result.unansweredCount, 1);
  assert.equal(result.ungradedCount, 1);
  assert.equal(result.positiveMarksEarned, 4);
  assert.equal(result.negativeMarksPenalty, 1);
  assert.equal(result.finalScore, 3);
  assert.equal(result.maxScore, 16);
});

test("JEE Main 2026 scores MCQ and compulsory numerical questions under exact section policies", () => {
  const questions = [
    { id: "mcq", correctAnswer: "A", section: "phy_sec_a", subject: "Physics", questionType: "MCQ_SINGLE" },
    { id: "zero", correctAnswer: "0", section: "math_sec_b", subject: "Mathematics", questionType: "NUMERICAL_VALUE" },
    { id: "wrong", correctAnswer: "42", section: "chem_sec_b", subject: "Chemistry", questionType: "NUMERICAL_VALUE" }
  ];
  const result = evaluateTestAttempt("JEE Main", questions, {
    mcq: { ans: "A" },
    zero: { ans: 0 },
    wrong: { ans: "50" }
  }, 90);
  assert.equal(result.correctCount, 2);
  assert.equal(result.wrongCount, 1);
  assert.equal(result.positiveMarksEarned, 8);
  assert.equal(result.negativeMarksPenalty, 1);
  assert.equal(result.finalScore, 7);
  assert.equal(result.maxScore, 12);
});

test("JEE Main Paper 1 numerical answer keys must be integers", () => {
  const valid = evaluateTestAttempt("JEE Main", [
    { id: "integer", correctAnswer: "12", section: "math_sec_b", subject: "Mathematics", questionType: "NUMERICAL_VALUE" },
    { id: "integer_decimal_format", correctAnswer: "12.0", section: "phy_sec_b", subject: "Physics", questionType: "NUMERICAL_VALUE" }
  ], { integer: { ans: "12.0" }, integer_decimal_format: { ans: "12" } }, 30);
  assert.equal(valid.correctCount, 2);
  assert.equal(valid.ungradedCount, 0);
  assert.equal(valid.finalScore, 8);

  const invalid = evaluateTestAttempt("JEE Main", [
    { id: "decimal", correctAnswer: "12.5", section: "math_sec_b", subject: "Mathematics", questionType: "NUMERICAL_VALUE" }
  ], { decimal: { ans: "12.5" } }, 30);
  assert.equal(invalid.correctCount, 0);
  assert.equal(invalid.ungradedCount, 1);
  assert.equal(invalid.finalScore, 0);
  assert.equal(invalid.maxScore, 4);
});

test("direct scoring rejects missing or duplicate question IDs", () => {
  assert.throws(() => evaluateTestAttempt("NEET UG", [
    { id: "duplicate", correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" },
    { id: "duplicate", correctAnswer: "B", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" }
  ], {}, 10), { code: "invalid-argument" });
  assert.throws(() => evaluateTestAttempt("NEET UG", [
    { correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" }
  ], {}, 10), { code: "invalid-argument" });
});

test("explicit SKIPPED answers are counted as unanswered", () => {
  const result = evaluateTestAttempt("NEET UG", [
    { id: "skip", correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" }
  ], { skip: { ans: "SKIPPED" } }, 10);
  assert.equal(result.correctCount, 0);
  assert.equal(result.wrongCount, 0);
  assert.equal(result.unansweredCount, 1);
  assert.equal(result.ungradedCount, 0);
  assert.equal(result.finalScore, 0);
});

test("old NEET Section A/B aliases and unsupported subjects fail closed instead of scoring", () => {
  const result = evaluateTestAttempt("NEET UG", [
    { id: "old", correctAnswer: "A", section: "SECTION_A", subject: "Physics", questionType: "MCQ_SINGLE" },
    { id: "bio", correctAnswer: "B", section: "bot_all", subject: "Biology", questionType: "MCQ_SINGLE" }
  ], { old: { ans: "A" }, bio: { ans: "B" } }, 60);
  assert.equal(result.ungradedCount, 2);
  assert.equal(result.correctCount, 0);
  assert.equal(result.finalScore, 0);
});

test("missing or unverified exam identity cannot silently default to NEET", () => {
  assert.throws(() => evaluateTestAttempt("", [], {}), { code: "invalid-argument" });
  assert.throws(() => evaluateTestAttempt("UPSC CSE", [], {}), { code: "failed-precondition" });
});

test("population and score percentiles handle ties and boundary cases", () => {
  assert.equal(calculatePopulationPercentile(1, 1), 100);
  assert.equal(calculatePopulationPercentile(2, 10), 90);
  assert.equal(calculatePopulationPercentile(10, 10), 10);
  assert.equal(calculatePopulationPercentile(1, 0), 0);
  assert.equal(calculateScorePercentileFromCounts(9, 1, 10), 100);
  assert.equal(calculateScorePercentileFromCounts(3, 3, 10), 60);
  assert.equal(calculateScorePercentileFromCounts(0, 0, 0), 0);
});

test("verified full tests require exact official question and section distribution", () => {
  const { validateFullTestQuestionCoverage, hasFullTestQuestionCoverage } = require("./examScoring");
  const neetSections = [
    ["Physics", "phy_all", 45],
    ["Chemistry", "chem_all", 45],
    ["Botany", "bot_all", 45],
    ["Zoology", "zoo_all", 45]
  ];
  const neetQuestions = neetSections.flatMap(([subject, section, count]) =>
    Array.from({ length: count }, (_, index) => ({ id: subject + index, subject, section }))
  );
  assert.equal(validateFullTestQuestionCoverage("NEET UG", neetQuestions), true);
  assert.equal(hasFullTestQuestionCoverage("NEET UG", neetQuestions), true);
  assert.equal(hasFullTestQuestionCoverage("NEET UG", neetQuestions.slice(1)), false);
  assert.throws(() => validateFullTestQuestionCoverage("NEET UG", neetQuestions.slice(1)));

  const jeeSections = [
    ["Physics", "phy_sec_a", 20], ["Physics", "phy_sec_b", 5],
    ["Chemistry", "chem_sec_a", 20], ["Chemistry", "chem_sec_b", 5],
    ["Mathematics", "math_sec_a", 20], ["Mathematics", "math_sec_b", 5]
  ];
  const jeeQuestions = jeeSections.flatMap(([subject, section, count]) =>
    Array.from({ length: count }, (_, index) => ({ id: subject + section + index, subject, section }))
  );
  assert.equal(validateFullTestQuestionCoverage("JEE Main", jeeQuestions), true);
  assert.equal(hasFullTestQuestionCoverage("JEE Main", jeeQuestions), true);
  assert.equal(hasFullTestQuestionCoverage("JEE Main", jeeQuestions.slice(1)), false);
  assert.throws(() => validateFullTestQuestionCoverage("JEE Main", jeeQuestions.slice(1)));
});

test("full-test classification cannot rank an incomplete paper at full weight", () => {
  const testData = {
    examId: "neet_ug",
    targetExam: "NEET UG",
    stageId: "neet_main",
    paperId: "neet_paper_1",
    status: "PUBLISHED",
    isOfficial: true,
    isPublic: true,
    countsTowardRanking: true,
    testType: "Full Test"
  };
  const question = {
    id: "q1", questionText: "Question", subject: "Physics", section: "phy_all",
    questionType: "MCQ_SINGLE", options: ["A", "B", "C", "D"], positiveMarks: 4, negativeMarks: 1
  };
  assert.throws(() => validateAndMergeAnswerKeys(testData, [question], {
    questions: [{ id: "q1", correctAnswer: "A" }]
  }), { code: "failed-precondition" });
});

test("ranking numerical questions reject MCQ options", () => {
  const testData = {
    examId: "jee_main", targetExam: "JEE Main Paper 1", stageId: "jee_paper_1",
    paperId: "jee_main_p1", status: "PUBLISHED", isOfficial: true, isPublic: true,
    countsTowardRanking: true, testType: "Part Test"
  };
  const question = {
    id: "q1", questionText: "Integer answer", subject: "Mathematics", section: "math_sec_b",
    questionType: "NUMERICAL_VALUE", options: ["A", "B", "C", "D"], positiveMarks: 4, negativeMarks: 1
  };
  assert.throws(() => validateAndMergeAnswerKeys(testData, [question], {
    questions: [{ id: "q1", correctAnswer: "12" }]
  }), { code: "failed-precondition" });
  assert.throws(() => validateAndMergeAnswerKeys(testData, [{
    ...question, options: [], optionAEn: "unexpected option"
  }], {
    questions: [{ id: "q1", correctAnswer: "12" }]
  }), { code: "failed-precondition" });
});

test("test question IDs and private answer-key IDs must be unique", () => {
  const testData = { status: "Published", isPublic: true, isOfficial: false, countsTowardRanking: false };
  assert.throws(() => validateAndMergeAnswerKeys(testData, [
    { id: "duplicate", questionText: "One" },
    { id: "duplicate", questionText: "Two" }
  ], null), { code: "failed-precondition" });
});

test("ranking publication rejects decimal JEE numerical answer keys", () => {
  const testData = {
    examId: "jee_main",
    targetExam: "JEE Main Paper 1",
    stageId: "jee_paper_1",
    paperId: "jee_main_p1",
    status: "PUBLISHED",
    isOfficial: true,
    isPublic: true,
    countsTowardRanking: true,
    testType: "Part Test"
  };
  const question = {
    id: "numeric",
    questionText: "Find the integer value.",
    subject: "Mathematics",
    section: "math_sec_b",
    questionType: "NUMERICAL_VALUE",
    positiveMarks: 4,
    negativeMarks: 1
  };
  assert.throws(() => validateAndMergeAnswerKeys(testData, [question], {
    questions: [{ id: "numeric", correctAnswer: "12.5" }]
  }), { code: "failed-precondition" });
  const valid = validateAndMergeAnswerKeys(testData, [question], {
    questions: [{ id: "numeric", correctAnswer: "12" }]
  });
  assert.equal(valid.questions[0].correctAnswer, "12");
});

test("ranking-eligible tests require complete trusted private answer keys", () => {
  const testData = {
    examId: "neet_ug",
    stageId: "neet_main",
    paperId: "neet_paper_1",
    status: "PUBLISHED",
    isOfficial: true,
    isPublic: true,
    countsTowardRanking: true,
    testType: "Part Test"
  };
  const question = {
    id: "q1",
    questionText: "What is the SI unit of potential difference?",
    subject: "Physics",
    section: "phy_all",
    questionType: "MCQ_SINGLE",
    optionAEn: "Ampere",
    optionBEn: "Volt",
    optionCEn: "Ohm",
    optionDEn: "Watt",
    positiveMarks: 4,
    negativeMarks: 1
  };
  assert.throws(() => validateAndMergeAnswerKeys(testData, [question], null), { code: "failed-precondition" });
  const merged = validateAndMergeAnswerKeys(testData, [question], {
    questions: [{ id: "q1", correctAnswer: "B" }]
  });
  assert.equal(merged.questions[0].correctAnswer, "B");
});
