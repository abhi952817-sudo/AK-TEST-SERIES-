const { test } = require("node:test");
const assert = require("node:assert");
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
} = require("./examScoring.js");

test("Answer verification: standard options, numbers, and multiple keys", () => {
  assert.strictEqual(isAnswerKeyVerified("A"), true);
  assert.strictEqual(isAnswerKeyVerified("d"), true);
  assert.strictEqual(isAnswerKeyVerified("1"), true);
  assert.strictEqual(isAnswerKeyVerified("42.5"), true);
  assert.strictEqual(isAnswerKeyVerified("A, C"), true);
  assert.strictEqual(isAnswerKeyVerified(""), false);
  assert.strictEqual(isAnswerKeyVerified("INVALID_KEY"), false);
});

test("Option equivalence: index to letter and numerical precision", () => {
  assert.strictEqual(areOptionsEquivalent("1", "A"), true);
  assert.strictEqual(areOptionsEquivalent("B", "2"), true);
  assert.strictEqual(areOptionsEquivalent("3.14", "3.15", true), false);
  assert.strictEqual(areOptionsEquivalent("10.0", "10.000", true), true);
  assert.strictEqual(areOptionsEquivalent("A", "C"), false);
});

test("User answer correctness: single, index, multiple correct", () => {
  assert.strictEqual(isUserAnswerCorrect("A", "A"), true);
  assert.strictEqual(isUserAnswerCorrect("1", "A"), true);
  assert.strictEqual(isUserAnswerCorrect("A, B", "B, A"), true);
  assert.strictEqual(isUserAnswerCorrect("A", "A, B"), true);
  assert.strictEqual(isUserAnswerCorrect("C", "A, B"), false);
});

test("Marking schemes for distinct exams", () => {
  const neet = getMarkingSchemeForExam("NEET UG");
  assert.strictEqual(neet.pos, 4.0);
  assert.strictEqual(neet.neg, 1.0);

  const upsc = getMarkingSchemeForExam("UPSC CSE");
  assert.strictEqual(upsc.pos, 2.0);
  assert.strictEqual(upsc.neg, 0.66);

  const ssc = getMarkingSchemeForExam("SSC CGL");
  assert.strictEqual(ssc.pos, 2.0);
  assert.strictEqual(ssc.neg, 0.50);

  const ctet = getMarkingSchemeForExam("CTET");
  assert.strictEqual(ctet.pos, 1.0);
  assert.strictEqual(ctet.neg, 0.0);
});

test("Authoritative evaluation: NEET standard attempt", () => {
  const questions = [
    { id: "q1", correctAnswer: "A", section: "SECTION_A", subject: "Physics" },
    { id: "q2", correctAnswer: "B", section: "SECTION_A", subject: "Physics" },
    { id: "q3", correctAnswer: "C", section: "SECTION_A", subject: "Physics" },
    { id: "q4", correctAnswer: "D", section: "SECTION_A", subject: "Physics" },
  ];

  const answers = {
    q1: { ans: "A" }, // correct: +4
    q2: { ans: "C" }, // wrong: -1
    q3: { ans: "" },  // unanswered: 0
    // q4 unattempted: 0
  };

  const result = evaluateTestAttempt("NEET UG", questions, answers, 120);
  assert.strictEqual(result.totalQuestions, 4);
  assert.strictEqual(result.correctCount, 1);
  assert.strictEqual(result.wrongCount, 1);
  assert.strictEqual(result.unansweredCount, 2);
  assert.strictEqual(result.positiveMarksEarned, 4.0);
  assert.strictEqual(result.negativeMarksPenalty, 1.0);
  assert.strictEqual(result.finalScore, 3.0);
  assert.strictEqual(result.rawScore, 3.0);
  assert.strictEqual(result.accuracyPercent, 50.0);
});

test("Section B optional limit: surplus questions are ignored", () => {
  const questions = [];
  const answers = {};
  for (let i = 1; i <= 12; i++) {
    const qid = `secB_q${i}`;
    questions.push({ id: qid, correctAnswer: "A", section: "SECTION_B", subject: "Physics" });
    answers[qid] = { ans: "A" };
  }

  const result = evaluateTestAttempt("NEET UG", questions, answers, 300);
  assert.strictEqual(result.correctCount, 10);
  assert.strictEqual(result.unansweredCount, 2);
  assert.strictEqual(result.finalScore, 40.0);
});

test("CAT TITA questions have zero negative penalty", () => {
  const questions = [
    { id: "q1", correctAnswer: "15", section: "QA", subject: "Quant", questionType: "TITA" },
    { id: "q2", correctAnswer: "A", section: "VARC", subject: "Verbal", questionType: "MCQ" },
  ];

  const answers = {
    q1: { ans: "20" }, // wrong TITA: 0 penalty
    q2: { ans: "B" },  // wrong MCQ: -1 penalty
  };

  const result = evaluateTestAttempt("CAT", questions, answers, 100);
  assert.strictEqual(result.wrongCount, 2);
  assert.strictEqual(result.negativeMarksPenalty, 1.0);
});

test("Population Percentile: standard ranking formula and boundary cases", () => {
  // Candidate Rank 1 out of 1
  assert.strictEqual(calculatePopulationPercentile(1, 1), 100.0);

  // Candidate Rank 1 out of 10
  assert.strictEqual(calculatePopulationPercentile(1, 10), 100.0);

  // Candidate Rank 2 out of 10: ((10 - 2 + 1) / 10) * 100 = 90.0%
  assert.strictEqual(calculatePopulationPercentile(2, 10), 90.0);

  // Candidate Rank 5 out of 10: ((10 - 5 + 1) / 10) * 100 = 60.0%
  assert.strictEqual(calculatePopulationPercentile(5, 10), 60.0);

  // Candidate Rank 10 out of 10: ((10 - 10 + 1) / 10) * 100 = 10.0%
  assert.strictEqual(calculatePopulationPercentile(10, 10), 10.0);

  // Boundary: Invalid or zero cohort
  assert.strictEqual(calculatePopulationPercentile(1, 0), 0.0);
  assert.strictEqual(calculatePopulationPercentile(0, 10), 0.0);
  assert.strictEqual(calculatePopulationPercentile(-1, 10), 0.0);

  // Rank exceeding cohort size gracefully clamped to lowest percentile bracket (1/N * 100)
  assert.strictEqual(calculatePopulationPercentile(15, 10), 10.0);
});

test("Score Percentile From Counts: exact testing formula with ties", () => {
  // Top score: 9 candidates below, 1 equal out of 10 total -> 100.0%
  assert.strictEqual(calculateScorePercentileFromCounts(9, 1, 10), 100.0);

  // Median score: 4 below, 1 equal out of 10 total -> 50.0%
  assert.strictEqual(calculateScorePercentileFromCounts(4, 1, 10), 50.0);

  // Ties: 3 below, 3 equal (tied with 2 others) out of 10 -> (3 + 3)/10 * 100 = 60.0%
  assert.strictEqual(calculateScorePercentileFromCounts(3, 3, 10), 60.0);

  // Lowest score: 0 below, 1 equal out of 10 -> 10.0%
  assert.strictEqual(calculateScorePercentileFromCounts(0, 1, 10), 10.0);

  // Zero candidates edge case
  assert.strictEqual(calculateScorePercentileFromCounts(0, 0, 0), 0.0);
});

// ==========================================
// PHASE 8.1: SCORING ENGINE PARITY TESTS
// ==========================================

test("Phase 8.1: Marking schemes for all 9 verified exam types", () => {
  // 1. IBPS PO (+1.0 / -0.25)
  const ibpsPo = getMarkingSchemeForExam("IBPS PO");
  assert.strictEqual(ibpsPo.pos, 1.0);
  assert.strictEqual(ibpsPo.neg, 0.25);

  // 2. IBPS Clerk (+1.0 / -0.25)
  const ibpsClerk = getMarkingSchemeForExam("ibps_clerk");
  assert.strictEqual(ibpsClerk.pos, 1.0);
  assert.strictEqual(ibpsClerk.neg, 0.25);

  // 3. SBI PO (+1.0 / -0.25)
  const sbiPo = getMarkingSchemeForExam("SBI PO");
  assert.strictEqual(sbiPo.pos, 1.0);
  assert.strictEqual(sbiPo.neg, 0.25);

  // 4. SBI Clerk (+1.0 / -0.25)
  const sbiClerk = getMarkingSchemeForExam("sbi_clerk");
  assert.strictEqual(sbiClerk.pos, 1.0);
  assert.strictEqual(sbiClerk.neg, 0.25);

  // 5. UGC NET (+2.0 / 0.0)
  const ugcNet = getMarkingSchemeForExam("UGC NET");
  assert.strictEqual(ugcNet.pos, 2.0);
  assert.strictEqual(ugcNet.neg, 0.0);

  // 6. SSC CPO (+1.0 / -0.25)
  const sscCpo = getMarkingSchemeForExam("SSC CPO");
  assert.strictEqual(sscCpo.pos, 1.0);
  assert.strictEqual(sscCpo.neg, 0.25);

  // 7. SSC GD (+2.0 / -0.25)
  const sscGd = getMarkingSchemeForExam("SSC GD");
  assert.strictEqual(sscGd.pos, 2.0);
  assert.strictEqual(sscGd.neg, 0.25);

  // 8. RRB ALP (+1.0 / -0.33)
  const rrbAlp = getMarkingSchemeForExam("rrb_alp");
  assert.strictEqual(rrbAlp.pos, 1.0);
  assert.strictEqual(rrbAlp.neg, 0.33);

  // 9. CAT Base (+3.0 / -1.0)
  const cat = getMarkingSchemeForExam("CAT");
  assert.strictEqual(cat.pos, 3.0);
  assert.strictEqual(cat.neg, 1.0);
});

test("Phase 8.1: CAT question-level scoring evaluates MCQ vs TITA separately", () => {
  const mcqScheme = getMarkingSchemeForQuestion("CAT", "VARC", "Verbal", "MCQ");
  assert.strictEqual(mcqScheme.pos, 3.0);
  assert.strictEqual(mcqScheme.neg, 1.0);

  const titaScheme = getMarkingSchemeForQuestion("CAT", "QA", "Quant", "TITA");
  assert.strictEqual(titaScheme.pos, 3.0);
  assert.strictEqual(titaScheme.neg, 0.0);

  const numericalScheme = getMarkingSchemeForQuestion("CAT", "QA", "Quant", "NUMERICAL");
  assert.strictEqual(numericalScheme.pos, 3.0);
  assert.strictEqual(numericalScheme.neg, 0.0);
});

test("Phase 8.1: Authoritative evaluation: Banking incorrect answers accumulate -0.25 penalty", () => {
  const questions = [
    { id: "q1", correctAnswer: "A", section: "eng_sec", subject: "English" },
    { id: "q2", correctAnswer: "B", section: "qa_sec", subject: "Quant" },
    { id: "q3", correctAnswer: "C", section: "reaz_sec", subject: "Reasoning" },
    { id: "q4", correctAnswer: "D", section: "reaz_sec", subject: "Reasoning" },
  ];

  const answers = {
    q1: { ans: "A" }, // Correct: +1.0
    q2: { ans: "C" }, // Wrong: -0.25
    q3: { ans: "" },  // Unanswered: 0.0
    q4: { ans: "A" }, // Wrong: -0.25
  };

  const result = evaluateTestAttempt("IBPS PO", questions, answers, 180);
  assert.strictEqual(result.totalQuestions, 4);
  assert.strictEqual(result.correctCount, 1);
  assert.strictEqual(result.wrongCount, 2);
  assert.strictEqual(result.unansweredCount, 1);
  assert.strictEqual(result.positiveMarksEarned, 1.0);
  assert.strictEqual(result.negativeMarksPenalty, 0.50);
  assert.strictEqual(result.rawScore, 0.50);
  assert.strictEqual(result.finalScore, 0.50);
});

test("Phase 8.1: Authoritative evaluation: UGC NET incorrect answers have ZERO negative penalty", () => {
  const questions = [
    { id: "q1", correctAnswer: "A", section: "ta_sec", subject: "Teaching Aptitude" },
    { id: "q2", correctAnswer: "B", section: "ta_sec", subject: "Research Aptitude" },
    { id: "q3", correctAnswer: "C", section: "ta_sec", subject: "Communication" },
  ];

  const answers = {
    q1: { ans: "A" }, // Correct: +2.0
    q2: { ans: "D" }, // Wrong: ZERO penalty (0.0)
    q3: { ans: "" },  // Unanswered: 0.0
  };

  const result = evaluateTestAttempt("UGC NET", questions, answers, 150);
  assert.strictEqual(result.totalQuestions, 3);
  assert.strictEqual(result.correctCount, 1);
  assert.strictEqual(result.wrongCount, 1);
  assert.strictEqual(result.unansweredCount, 1);
  assert.strictEqual(result.positiveMarksEarned, 2.0);
  assert.strictEqual(result.negativeMarksPenalty, 0.0);
  assert.strictEqual(result.rawScore, 2.0);
  assert.strictEqual(result.finalScore, 2.0);
});

test("Phase 8.1: Authoritative evaluation: CAT MCQ (-1.0) and TITA (0.0) incorrect answers evaluated separately", () => {
  const questions = [
    { id: "q1", correctAnswer: "A", section: "VARC", subject: "Verbal", questionType: "MCQ" },
    { id: "q2", correctAnswer: "B", section: "VARC", subject: "Verbal", questionType: "MCQ" },
    { id: "q3", correctAnswer: "42", section: "QA", subject: "Quant", questionType: "TITA" },
    { id: "q4", correctAnswer: "99", section: "QA", subject: "Quant", questionType: "TITA" },
  ];

  const answers = {
    q1: { ans: "A" },  // Correct MCQ: +3.0
    q2: { ans: "C" },  // Wrong MCQ: -1.0 penalty
    q3: { ans: "42" }, // Correct TITA: +3.0
    q4: { ans: "50" }, // Wrong TITA: 0.0 penalty
  };

  const result = evaluateTestAttempt("CAT", questions, answers, 240);
  assert.strictEqual(result.totalQuestions, 4);
  assert.strictEqual(result.correctCount, 2);
  assert.strictEqual(result.wrongCount, 2);
  assert.strictEqual(result.positiveMarksEarned, 6.0);
  assert.strictEqual(result.negativeMarksPenalty, 1.0); // Only MCQ penalized
  assert.strictEqual(result.rawScore, 5.0);
  assert.strictEqual(result.finalScore, 5.0);
});

test("Phase 8.1: Unknown or missing exam handling throws controlled error without defaulting to NEET", () => {
  // Empty exam name throws invalid-argument
  assert.throws(
    () => getMarkingSchemeForExam(""),
    (err) => err.code === "invalid-argument"
  );

  // Unknown exam name throws invalid-argument rather than returning NEET +4/-1
  assert.throws(
    () => getMarkingSchemeForExam("NON_EXISTENT_OLYMPIAD_XYZ"),
    (err) => err.code === "invalid-argument"
  );

  // evaluateTestAttempt with unknown exam throws invalid-argument
  assert.throws(
    () => evaluateTestAttempt("UNKNOWN_EXAM_FOO", [{ id: "q1", correctAnswer: "A" }], { q1: "A" }),
    (err) => err.code === "invalid-argument"
  );
});

test("Phase 8.1: Railway normalization preserves all 5 distinct railway exam keys", () => {
  assert.strictEqual(normalizeExamKey("rrb_ntpc"), "RRB_NTPC");
  assert.strictEqual(normalizeExamKey("rrb_group_d"), "RRB_GROUP_D");
  assert.strictEqual(normalizeExamKey("rrb_alp"), "RRB_ALP");
  assert.strictEqual(normalizeExamKey("rrb_technician"), "RRB_TECHNICIAN");
  assert.strictEqual(normalizeExamKey("rrb_je"), "RRB_JE");
});

test("Phase 8.1: Railway normalization handles supported aliases without cross-exam identity loss", () => {
  assert.strictEqual(normalizeExamKey("RRB NTPC CBT-1"), "RRB_NTPC");
  assert.strictEqual(normalizeExamKey("Railway Group D Level 1"), "RRB_GROUP_D");
  assert.strictEqual(normalizeExamKey("Assistant Loco Pilot (ALP)"), "RRB_ALP");
  assert.strictEqual(normalizeExamKey("RRB Technician Grade III"), "RRB_TECHNICIAN");
  assert.strictEqual(normalizeExamKey("RRB Junior Engineer CBT 1"), "RRB_JE");
});

test("Phase 8.1: Normalization: Missing or unrecognized inputs do not silently become NEET or RRB NTPC", () => {
  assert.strictEqual(normalizeExamKey(""), "UNKNOWN");
  assert.strictEqual(normalizeExamKey(null), "UNKNOWN");
  assert.strictEqual(normalizeExamKey(undefined), "UNKNOWN");
  assert.strictEqual(normalizeExamKey("   "), "UNKNOWN");
  assert.strictEqual(normalizeExamKey("Special State Board Exam"), "SPECIAL_STATE_BOARD_EXAM");
});

test("Phase 8.1: Regression test for existing NEET, JEE Main, and UPSC marking schemes", () => {
  const neet = getMarkingSchemeForExam("NEET UG");
  assert.strictEqual(neet.pos, 4.0);
  assert.strictEqual(neet.neg, 1.0);

  const jee = getMarkingSchemeForExam("JEE Main Paper 1");
  assert.strictEqual(jee.pos, 4.0);
  assert.strictEqual(jee.neg, 1.0);

  const upsc = getMarkingSchemeForExam("UPSC Civil Services Prelims");
  assert.strictEqual(upsc.pos, 2.0);
  assert.strictEqual(upsc.neg, 0.66);
});
