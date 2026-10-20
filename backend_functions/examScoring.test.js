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
