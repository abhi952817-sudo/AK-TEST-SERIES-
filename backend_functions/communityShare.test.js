"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { validateCommunityShareSource } = require("./communityShare");

const registry = {
  version: 1,
  exams: {
    neet_ug: {
      examName: "NEET UG",
      scoringVersions: {
        "2": {
          examYear: "2026",
          syllabusVersion: "neet-2026-v1",
          stages: {
            neet_main: {
              papers: {
                neet_paper_1: {
                  subjects: [{
                    subjectId: "physics",
                    subjectName: "Physics",
                    sections: [{
                      sectionId: "phy_sec_a",
                      sectionName: "Section A",
                      questionTypes: {
                        MCQ_SINGLE: { positiveMarks: 4, negativeMarks: 1 }
                      }
                    }]
                  }]
                }
              }
            }
          }
        }
      }
    }
  }
};

function sourceTest(overrides = {}) {
  return {
    uploaderId: "student-1",
    status: "Published",
    examId: "neet_ug",
    examYear: 2026,
    stageId: "neet_main",
    paperId: "neet_paper_1",
    scoringVersion: 2,
    questionsCount: 1,
    durationMinutes: 60,
    totalMarks: 4,
    title: "Physics practice test",
    questionsJson: JSON.stringify([{
      id: "q1",
      questionText: "Which law describes inertia in classical mechanics?",
      questionType: "MCQ_SINGLE",
      sectionId: "phy_sec_a",
      subjectId: "physics",
      subject: "Physics",
      positiveMarks: 4,
      negativeMarks: 1,
      options: ["Newton's first law", "Newton's second law", "Newton's third law", "Law of gravitation"],
      correctAnswer: "A",
      explanation: "The first law describes inertia.",
      generationModel: "Gemini 2.5 Pro",
      sourceVerified: true
    }]),
    ...overrides
  };
}

test("validates source ownership, exam-year policy, subject and question scoring", () => {
  const result = validateCommunityShareSource({
    testId: "test-1",
    ownerUid: "student-1",
    testData: sourceTest(),
    registry,
    selectedSubject: "Physics"
  });
  assert.equal(result.examId, "neet_ug");
  assert.equal(result.subject, "Physics");
  assert.equal(result.publicQuestions.length, 1);
  assert.equal("correctAnswer" in result.publicQuestions[0], false);
  assert.equal("explanation" in result.publicQuestions[0], false);
  assert.equal(result.privateKeys[0].correctAnswer, "A");
});

test("rejects a forged owner", () => {
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1", ownerUid: "attacker", testData: sourceTest(), registry, selectedSubject: "Physics"
  }), /owner/i);
});

test("rejects subject metadata that is not registered for the paper", () => {
  const data = sourceTest({
    questionsJson: JSON.stringify([{
      ...JSON.parse(sourceTest().questionsJson)[0],
      subjectId: "chemistry",
      subject: "Chemistry"
    }])
  });
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1", ownerUid: "student-1", testData: data, registry, selectedSubject: "Physics"
  }), /subject/i);
});

test("rejects question marks that differ from the exam-year section policy", () => {
  const data = sourceTest({
    questionsJson: JSON.stringify([{ ...JSON.parse(sourceTest().questionsJson)[0], positiveMarks: 5 }])
  });
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1", ownerUid: "student-1", testData: data, registry, selectedSubject: "Physics"
  }), /marking/i);
});

test("rejects unverified or non-Gemini questions", () => {
  const data = sourceTest({
    questionsJson: JSON.stringify([{ ...JSON.parse(sourceTest().questionsJson)[0], sourceVerified: false }])
  });
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1", ownerUid: "student-1", testData: data, registry, selectedSubject: "Physics"
  }), /verified/i);
});

test("rejects source year that conflicts with the server-owned scoring-version year", () => {
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1",
    ownerUid: "student-1",
    testData: sourceTest({ examYear: 2025 }),
    registry,
    selectedSubject: "Physics"
  }), /year/i);
});

test("rejects unverified Full Syllabus claims", () => {
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1",
    ownerUid: "student-1",
    testData: sourceTest({ syllabusCoverageVerified: false }),
    registry,
    selectedSubject: "Full Syllabus"
  }), /Full Syllabus/i);
});

test("rejects an MCQ answer key that does not map to an available option", () => {
  const data = sourceTest({
    questionsJson: JSON.stringify([{ ...JSON.parse(sourceTest().questionsJson)[0], correctAnswer: "E" }])
  });
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1", ownerUid: "student-1", testData: data, registry, selectedSubject: "Physics"
  }), /answer key/i);
});

test("copies the registry's optional section attempt limit into the public question projection", () => {
  const registryWithLimit = JSON.parse(JSON.stringify(registry));
  registryWithLimit.exams.neet_ug.scoringVersions["2"].stages.neet_main
    .papers.neet_paper_1.subjects[0].sections[0].maxAttemptQuestions = 1;
  const result = validateCommunityShareSource({
    testId: "test-1",
    ownerUid: "student-1",
    testData: sourceTest(),
    registry: registryWithLimit,
    selectedSubject: "Physics"
  });
  assert.equal(result.publicQuestions[0].maxAttemptQuestions, 1);
});

test("rejects source total marks that do not equal the verified question marks", () => {
  assert.throws(() => validateCommunityShareSource({
    testId: "test-1",
    ownerUid: "student-1",
    testData: sourceTest({ totalMarks: 720 }),
    registry,
    selectedSubject: "Physics"
  }), /total-marks/i);
});
