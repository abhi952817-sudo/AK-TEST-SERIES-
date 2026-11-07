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
  assert.equal(resolveCanonicalExamIdentity({ examId: "neet_ug", targetExam: "NEET UG" }), "NEET");
  assert.equal(resolveCanonicalExamIdentity({ examId: "jee_main", targetExam: "JEE Main Paper 1" }), "JEE_MAIN");
  assert.throws(
    () => resolveCanonicalExamIdentity({ examId: "neet_ug", targetExam: "JEE Main" }),
    { code: "failed-precondition" }
  );
  assert.throws(() => resolveCanonicalExamIdentity({ subject: "Biology" }), { code: "failed-precondition" });
  assert.throws(() => resolveCanonicalExamIdentity({ examId: "ALL" }), { code: "failed-precondition" });
});

test("only source-verified NEET UG 2026 and JEE Main 2026 scoring schemes are enabled", () => {
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

test("ranking-eligible tests require complete trusted private answer keys", () => {
  const testData = {
    examId: "neet_ug",
    status: "PUBLISHED",
    isOfficial: true,
    isPublic: true,
    countsTowardRanking: true
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
