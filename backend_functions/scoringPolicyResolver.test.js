"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  resolveScoringPolicy,
  validateVersionedTestScoring
} = require("./scoringPolicyResolver");

function makePolicy(overrides = {}) {
  return {
    examId: "example_exam",
    examYear: "2026",
    scoringVersion: "v2026-1",
    stageId: "stage_1",
    paperId: "paper_1",
    subjectId: "physics",
    subjectName: "Physics",
    sectionId: "section_a",
    sectionName: "Section A",
    questionType: "MCQ_SINGLE",
    positiveMarks: 4,
    negativeMarks: 1,
    maxAttemptQuestions: null,
    status: "APPROVED",
    source: {
      url: "https://official.example.gov/exam-bulletin-2026.pdf",
      title: "Official Example Exam Bulletin 2026"
    },
    verifiedAt: "2026-01-10T10:00:00.000Z",
    approvedBy: "primary-admin",
    ...overrides
  };
}

function makeRegistry(policies = [makePolicy()]) {
  return { schemaVersion: 1, status: "ACTIVE", policies };
}

function makeTestData(overrides = {}) {
  return {
    examId: "example_exam",
    examYear: "2026",
    scoringVersion: "v2026-1",
    stageId: "stage_1",
    paperId: "paper_1",
    questionsCount: 1,
    totalMarks: 4,
    ...overrides
  };
}

function makeQuestion(overrides = {}) {
  return {
    id: "q1",
    subjectId: "physics",
    sectionId: "section_a",
    questionType: "MCQ_SINGLE",
    subject: "Physics",
    section: "Section A",
    ...overrides
  };
}

test("Resolver requires an exact year/stage/paper/subject/section/type key", () => {
  const resolved = resolveScoringPolicy(makeRegistry(), {
    examId: "example_exam",
    examYear: "2026",
    scoringVersion: "v2026-1",
    stageId: "stage_1",
    paperId: "paper_1",
    subjectId: "physics",
    sectionId: "section_a",
    questionType: "mcq_single"
  });
  assert.equal(resolved.positiveMarks, 4);
  assert.equal(resolved.negativeMarks, 1);
});

test("Resolver rejects an unknown exam year rather than inferring another year", () => {
  assert.throws(
    () => resolveScoringPolicy(makeRegistry(), {
      examId: "example_exam",
      examYear: "2027",
      scoringVersion: "v2026-1",
      stageId: "stage_1",
      paperId: "paper_1",
      subjectId: "physics",
      sectionId: "section_a",
      questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
});

test("Resolver rejects unsupported registry schema", () => {
  assert.throws(
    () => resolveScoringPolicy({ ...makeRegistry(), schemaVersion: 2 }, {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
});

test("Resolver rejects inactive registry and unapproved or source-less policies", () => {
  assert.throws(
    () => resolveScoringPolicy({ ...makeRegistry(), status: "DRAFT" }, {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy({ status: "DRAFT" })]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy({ source: { url: "http://insecure.example", title: "x" } })]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy({ source: { url: "https://", title: "x" } })]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy({ source: { url: "https://user:pass@example.gov/bulletin.pdf", title: "x" } })]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  for (const verifiedAt of ["not-a-date", "2026-02-30T10:00:00Z", "2026-01-10"]) {
    assert.throws(
      () => resolveScoringPolicy(makeRegistry([makePolicy({ verifiedAt })]), {
        examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
        stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
        sectionId: "section_a", questionType: "MCQ_SINGLE"
      }),
      { code: "failed-precondition" }
    );
  }
});

test("Resolver rejects duplicate exact policies and invalid marks", () => {
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy(), makePolicy()]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => resolveScoringPolicy(makeRegistry([makePolicy({ negativeMarks: -1 })]), {
      examId: "example_exam", examYear: "2026", scoringVersion: "v2026-1",
      stageId: "stage_1", paperId: "paper_1", subjectId: "physics",
      sectionId: "section_a", questionType: "MCQ_SINGLE"
    }),
    { code: "failed-precondition" }
  );
});

test("Versioned test validation derives exact marks and applies section attempt caps", () => {
  const policies = [
    makePolicy(),
    makePolicy({
      sectionId: "section_b",
      sectionName: "Section B",
      questionType: "NUMERICAL_VALUE",
      positiveMarks: 4,
      negativeMarks: 0,
      maxAttemptQuestions: 1
    })
  ];
  const questions = [
    makeQuestion({ id: "q1" }),
    makeQuestion({ id: "q2" }),
    makeQuestion({ id: "q3", sectionId: "section_b", section: "Section B", questionType: "NUMERICAL_VALUE" }),
    makeQuestion({ id: "q4", sectionId: "section_b", section: "Section B", questionType: "NUMERICAL_VALUE" })
  ];
  const result = validateVersionedTestScoring(
    makeRegistry(policies),
    makeTestData({ questionsCount: 4, totalMarks: 12 }),
    questions
  );
  assert.equal(result.calculatedMaxMarks, 12);
  assert.equal(result.questions[0].positiveMarks, 4);
  assert.equal(result.questions[2].maxAttemptQuestions, 1);
});

test("Versioned test validation rejects source marks that conflict with approved policy", () => {
  assert.throws(
    () => validateVersionedTestScoring(
      makeRegistry(),
      makeTestData(),
      [makeQuestion({ positiveMarks: 5 })]
    ),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => validateVersionedTestScoring(
      makeRegistry(),
      makeTestData(),
      [makeQuestion({ positiveMarks: "not-a-number" })]
    ),
    { code: "failed-precondition" }
  );
});

test("Versioned test validation rejects mismatched source total marks and duplicate question IDs", () => {
  assert.throws(
    () => validateVersionedTestScoring(
      makeRegistry(),
      makeTestData({ totalMarks: 5 }),
      [makeQuestion()]
    ),
    { code: "failed-precondition" }
  );
  assert.throws(
    () => validateVersionedTestScoring(
      makeRegistry(),
      makeTestData({ questionsCount: 2, totalMarks: 8 }),
      [makeQuestion(), makeQuestion()]
    ),
    { code: "failed-precondition" }
  );
});
