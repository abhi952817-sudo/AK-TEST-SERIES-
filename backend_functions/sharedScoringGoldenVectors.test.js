const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { evaluateTestAttempt } = require("./examScoring");

const fixturePath = path.resolve(__dirname, "../shared/scoring/scoring-golden-v1.json");
const fixtureDocument = JSON.parse(fs.readFileSync(fixturePath, "utf8"));

for (const fixture of fixtureDocument.fixtures) {
  test(`shared scoring vector: ${fixture.id}`, () => {
    const questions = fixture.questions.map((question) => ({
      ...question,
      subject: "General",
      section: fixture.sectionId || "SECTION_A",
      questionType: question.questionType || fixture.questionType || "MCQ_SINGLE",
    }));
    const result = evaluateTestAttempt(fixture.examId, questions, fixture.answers, 0);
    const expected = fixture.expected;

    assert.equal(result.correctCount, expected.correctCount, fixture.id);
    assert.equal(result.wrongCount, expected.wrongCount, fixture.id);
    assert.equal(result.unansweredCount, expected.unansweredCount, fixture.id);
    for (const field of [
      "positiveMarksEarned",
      "negativeMarksPenalty",
      "rawScore",
      "finalScore",
      "maxScore",
    ]) {
      assert.ok(
        Math.abs(result[field] - expected[field]) <= 0.005,
        `${fixture.id}: ${field} expected ${expected[field]}, received ${result[field]}`
      );
    }
  });
}
