"use strict";

const { isAnswerKeyVerified } = require("./examScoring");

function fail(message, code = "failed-precondition") {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalized(value) {
  return cleanString(value).toLowerCase().replace(/\s+/g, " ");
}

function finiteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function parseQuestions(testData) {
  let questions = testData.questionsJson;
  if (typeof questions === "string") {
    try {
      questions = JSON.parse(questions);
    } catch (_) {
      fail("The source test question JSON is invalid.");
    }
  }
  if (!Array.isArray(questions) || questions.length === 0) {
    fail("The source test has no server-readable questions.");
  }
  return questions;
}

function getOptions(question) {
  if (Array.isArray(question.options)) {
    return question.options.map(value => cleanString(value));
  }
  return ["A", "B", "C", "D", "E"]
    .map(letter => cleanString(question["option" + letter] || question["option" + letter + "En"]))
    .filter(Boolean);
}

function getQuestionTypeRule(section, questionType) {
  const rules = section && section.questionTypes;
  if (!rules || typeof rules !== "object") return null;
  return rules[questionType] || rules[questionType.toUpperCase()] || null;
}

/**
 * Validates and projects a Cloud Firestore-owned test for Community publication.
 *
 * Registry contract is server-owned at Config/community_exam_registry:
 * {
 *   version: integer,
 *   exams: {
 *     [examId]: {
 *       examName: string,
 *       years: {
 *         [examYear]: {
 *           scoringVersion: integer,
 *           syllabusVersion: string,
 *           stages: {
 *             [stageId]: {
 *               papers: {
 *                 [paperId]: {
 *                   subjects: [{
 *                     subjectId: string,
 *                     subjectName: string,
 *                     sections: [{
 *                       sectionId: string,
 *                       sectionName: string,
 *                       questionTypes: {
 *                         [questionType]: { positiveMarks: number, negativeMarks: number }
 *                       }
 *                     }]
 *                   }]
 *                 }
 *               }
 *             }
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 */
function validateCommunityShareSource({
  testId,
  ownerUid,
  testData,
  registry,
  selectedSubject
}) {
  if (!testData || typeof testData !== "object") fail("Source test record is missing.", "not-found");
  if (!registry || !Number.isInteger(registry.version) || registry.version < 1 ||
      !registry.exams || typeof registry.exams !== "object") {
    fail("The server-side Community exam registry is not configured.");
  }

  const owner = cleanString(testData.uploaderId || testData.creatorId || testData.ownerId);
  if (!owner || owner !== ownerUid) fail("Only the authenticated source-test owner can share this test.", "permission-denied");

  const status = cleanString(testData.status);
  if (!["PUBLISHED", "APPROVED"].includes(status.toUpperCase())) {
    fail("Only a published or approved source test can be shared.");
  }

  const examId = cleanString(testData.examId);
  const stageId = cleanString(testData.stageId);
  const paperId = cleanString(testData.paperId);
  const examYear = cleanString(String(testData.examYear || ""));
  const scoringVersion = Number(testData.scoringVersion);

  if (!examId || !stageId || !paperId || !/^20\d{2}$/.test(examYear) ||
      !Number.isInteger(scoringVersion) || scoringVersion < 1) {
    fail("The source test is missing canonical exam, year, stage, paper, or scoring-version metadata.");
  }

  const exam = registry.exams[examId];
  if (!exam || !cleanString(exam.examName)) fail("This exam is not registered for Community sharing.");
  const yearPolicy = exam.years && exam.years[examYear];
  if (!yearPolicy) fail("This exam year has no approved server-side Community scoring policy.");
  if (Number(yearPolicy.scoringVersion) !== scoringVersion) {
    fail("The source test scoring version does not match the approved exam-year policy.");
  }

  const stage = yearPolicy.stages && yearPolicy.stages[stageId];
  const paper = stage && stage.papers && stage.papers[paperId];
  if (!paper || !Array.isArray(paper.subjects) || paper.subjects.length === 0) {
    fail("The source test stage or paper is not present in the approved exam registry.");
  }

  const questions = parseQuestions(testData);
  const expectedCount = Number(testData.questionsCount || testData.questionCount);
  if (!Number.isInteger(expectedCount) || expectedCount !== questions.length) {
    fail("The source question count does not match its server-side question records.");
  }
  if (questions.length > 450) fail("This test exceeds the safe Community publication limit.");

  const subjectById = new Map();
  const subjectByName = new Map();
  for (const subject of paper.subjects) {
    const id = cleanString(subject.subjectId);
    const name = cleanString(subject.subjectName);
    if (!id || !name || !Array.isArray(subject.sections)) {
      fail("The approved exam registry contains an incomplete subject or section mapping.");
    }
    subjectById.set(id, subject);
    subjectByName.set(normalized(name), subject);
  }

  const requestedSubject = cleanString(selectedSubject);
  if (!requestedSubject) fail("Select the subject shown on the test verification card.", "invalid-argument");
  const isFullSyllabus = requestedSubject.toLowerCase() === "full syllabus";
  const chosenSubject = isFullSyllabus ? null : subjectByName.get(normalized(requestedSubject));
  if (!isFullSyllabus && !chosenSubject) fail("The selected subject is not registered for this exam paper.", "invalid-argument");

  const seenIds = new Set();
  const seenSubjects = new Set();
  const publicQuestions = [];
  const privateKeys = [];

  for (let index = 0; index < questions.length; index += 1) {
    const source = questions[index];
    if (!source || typeof source !== "object" || Array.isArray(source)) {
      fail("A source question record is malformed.");
    }
    const id = cleanString(source.id || source.questionId);
    const questionText = cleanString(source.questionText || source.questionTextEn || source.text);
    const questionType = cleanString(source.questionType).toUpperCase();
    const sectionId = cleanString(source.sectionId || source.section);
    const subjectId = cleanString(source.subjectId);
    const subjectName = cleanString(source.subject);
    const subject = (subjectId && subjectById.get(subjectId)) ||
      (subjectName && subjectByName.get(normalized(subjectName)));

    if (!id || seenIds.has(id)) fail("Question IDs must be present and unique.");
    seenIds.add(id);
    if (!subject || (subjectId && subject.subjectId !== subjectId) ||
        (subjectName && normalized(subject.subjectName) !== normalized(subjectName))) {
      fail("A question subject does not match the approved exam-paper subject mapping.");
    }
    seenSubjects.add(subject.subjectId);

    if (!questionText || questionText.length < 10 || !questionType || !sectionId) {
      fail("A question is missing its text, type, or section metadata.");
    }
    if (source.sourceVerified !== true ||
        !cleanString(source.generationModel).toLowerCase().includes("gemini")) {
      fail("Every shared question must be a verified, real Gemini-generated source question.");
    }

    const section = subject.sections.find(item =>
      cleanString(item.sectionId) === sectionId ||
      normalized(item.sectionName) === normalized(sectionId)
    );
    const typeRule = getQuestionTypeRule(section, questionType);
    if (!section || !typeRule ||
        !finiteNumber(typeRule.positiveMarks) || !finiteNumber(typeRule.negativeMarks)) {
      fail("A question section or question-type scoring rule is not registered.");
    }

    const positiveMarks = Number(source.positiveMarks);
    const negativeMarks = Number(source.negativeMarks);
    if (!finiteNumber(positiveMarks) || !finiteNumber(negativeMarks) ||
        Math.abs(positiveMarks - typeRule.positiveMarks) > 0.000001 ||
        Math.abs(negativeMarks - typeRule.negativeMarks) > 0.000001) {
      fail("Question marking metadata does not match the approved exam-year section policy.");
    }

    const answer = cleanString(source.correctAnswer);
    if (!answer || !isAnswerKeyVerified(answer)) {
      fail("A source question is missing a valid answer key; the test was not shared.");
    }

    const numeric = ["NUMERICAL", "NUMERICAL_VALUE", "NAT", "TITA"].includes(questionType);
    const options = getOptions(source);
    if (numeric) {
      if (options.length !== 0) fail("Numerical questions must not contain multiple-choice options.");
    } else if (options.length < 4 || options.length > 5 ||
        options.some(option => !option) ||
        new Set(options.map(normalized)).size !== options.length) {
      fail("Each multiple-choice question must have four or five unique, non-empty options.");
    }

    const imageUrl = cleanString(source.imageUrl);
    if (imageUrl && !/^https:\/\/\//i.test(imageUrl)) {
      fail("A question image is not a valid hosted HTTPS URL.");
    }

    if (!isFullSyllabus && subject.subjectId !== chosenSubject.subjectId) {
      fail("The selected subject does not match every question in this subject test.");
    }

    const publicQuestion = {
      id,
      testId,
      questionText,
      options,
      questionType,
      positiveMarks,
      negativeMarks,
      section: cleanString(section.sectionId),
      sectionName: cleanString(section.sectionName),
      subject: cleanString(subject.subjectName),
      subjectId: cleanString(subject.subjectId),
      chapter: cleanString(source.chapter),
      topic: cleanString(source.topic),
      difficulty: cleanString(source.difficulty),
      imageUrl
    };
    for (const field of ["questionTextHi", "optionAHi", "optionBHi", "optionCHi", "optionDHi"]) {
      const value = cleanString(source[field]);
      if (value) publicQuestion[field] = value;
    }
    publicQuestions.push(publicQuestion);
    privateKeys.push({
      id,
      correctAnswer: answer,
      explanation: cleanString(source.explanation || source.explanationEn),
      explanationHi: cleanString(source.explanationHi)
    });
  }

  if (isFullSyllabus) {
    const requiredSubjectIds = paper.subjects.map(subject => subject.subjectId);
    if (testData.syllabusCoverageVerified !== true ||
        cleanString(testData.syllabusVersion) !== cleanString(yearPolicy.syllabusVersion) ||
        requiredSubjectIds.some(id => !seenSubjects.has(id))) {
      fail("Full Syllabus sharing requires a server-verified coverage record for every registered subject.");
    }
  }

  const durationMinutes = Number(testData.durationMinutes);
  const totalMarks = Number(testData.totalMarks);
  if (!Number.isInteger(durationMinutes) || durationMinutes <= 0 ||
      !finiteNumber(totalMarks) || totalMarks <= 0) {
    fail("The source test is missing valid duration or total-marks metadata.");
  }

  return {
    testId,
    examId,
    examName: cleanString(exam.examName),
    examYear,
    stageId,
    paperId,
    scoringVersion,
    subject: isFullSyllabus ? "Full Syllabus" : cleanString(chosenSubject.subjectName),
    title: cleanString(testData.title),
    description: cleanString(testData.description),
    classLevel: cleanString(testData.classLevel),
    chapter: cleanString(testData.chapter),
    topic: cleanString(testData.topic),
    difficulty: cleanString(testData.difficulty),
    durationMinutes,
    totalMarks,
    questionCount: publicQuestions.length,
    publicQuestions,
    privateKeys,
    sourceUpdatedAt: testData.updatedAt || testData.timestamp || null
  };
}

module.exports = { validateCommunityShareSource };
