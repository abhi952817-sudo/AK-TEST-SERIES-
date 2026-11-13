function makeValidationError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function validateCommunityMcqCreate(data, expectedUid) {
  const payload = data || {};
  const title = typeof payload.title === "string" ? payload.title.trim() : "";
  const question = typeof payload.question === "string" ? payload.question.trim() : "";
  const subject = typeof payload.subject === "string" ? payload.subject.trim() : "";
  const targetExam = typeof payload.targetExam === "string" ? payload.targetExam.trim() : "";
  const options = payload.options;

  if (!title || title.length > 120) {
    throw makeValidationError("invalid-argument", "MCQ title is required and must be at most 120 characters.");
  }
  if (!question || question.length > 5000) {
    throw makeValidationError("invalid-argument", "One question is required and must be at most 5000 characters.");
  }
  if (!subject || subject.toLowerCase() === "general" || subject.length > 100) {
    throw makeValidationError("invalid-argument", "A valid non-General subject is required.");
  }
  if (!targetExam || targetExam.length > 100) {
    throw makeValidationError("invalid-argument", "A valid target exam is required.");
  }
  const registeredExam = resolveCommunityExamSubject(targetExam, subject);
  if (!Array.isArray(options) || ![4, 5].includes(options.length)) {
    throw makeValidationError("invalid-argument", "An MCQ must contain exactly four or five options.");
  }
  const cleanOptions = options.map(option => typeof option === "string" ? option.trim() : "");
  if (cleanOptions.some(option => !option || option.length > 500)) {
    throw makeValidationError("invalid-argument", "Every option must contain 1 to 500 characters.");
  }
  if (new Set(cleanOptions.map(option => option.toLocaleLowerCase())).size !== cleanOptions.length) {
    throw makeValidationError("invalid-argument", "MCQ options must be unique.");
  }
  const correctOptionIndex = payload.correctOptionIndex;
  if (!Number.isInteger(correctOptionIndex) || correctOptionIndex < 0 || correctOptionIndex >= cleanOptions.length) {
    throw makeValidationError("invalid-argument", "Exactly one valid correct option must be selected.");
  }
  const requestId = typeof payload.requestId === "string" ? payload.requestId.trim() : "";
  if (!requestId || requestId.length > 100) {
    throw makeValidationError("invalid-argument", "A stable requestId is required for duplicate protection.");
  }
  let imageUrl = null;
  let imagePath = null;
  if (payload.imageUrl != null && payload.imageUrl !== "") {
    if (typeof payload.imageUrl !== "string" || payload.imageUrl.length > 2048) {
      throw makeValidationError("invalid-argument", "Invalid MCQ image URL.");
    }
    if (typeof expectedUid !== "string" || !expectedUid.trim()) {
      throw makeValidationError("invalid-argument", "MCQ image ownership could not be verified.");
    }
    imagePath = typeof payload.imagePath === "string" ? payload.imagePath.trim() : "";
    const expectedPrefix = `community_images/${expectedUid}/`;
    if (!imagePath.startsWith(expectedPrefix) || imagePath.split("/").length !== 3 || imagePath.includes("..")) {
      throw makeValidationError("invalid-argument", "MCQ image must be uploaded to the creator's Community image folder.");
    }
    let parsed;
    try { parsed = new URL(payload.imageUrl); } catch (_) {
      throw makeValidationError("invalid-argument", "Invalid MCQ image URL.");
    }
    if (parsed.protocol !== "https:" || parsed.hostname !== "firebasestorage.googleapis.com") {
      throw makeValidationError("invalid-argument", "MCQ images must use the approved Firebase Storage host.");
    }
    let decodedObjectPath = "";
    try {
      const encodedObjectPath = parsed.pathname.split("/o/")[1] || "";
      decodedObjectPath = decodeURIComponent(encodedObjectPath);
    } catch (_) {
      throw makeValidationError("invalid-argument", "MCQ image URL path is malformed.");
    }
    if (decodedObjectPath !== imagePath) {
      throw makeValidationError("invalid-argument", "MCQ image URL does not match its owned storage path.");
    }
    imageUrl = payload.imageUrl;
  } else if (payload.imagePath != null && payload.imagePath !== "") {
    throw makeValidationError("invalid-argument", "An image path cannot be supplied without an image URL.");
  }
  return { title, question, subject: registeredExam.subject, targetExam: registeredExam.examName, examId: registeredExam.examId, options: cleanOptions, correctOptionIndex, requestId, imageUrl, imagePath };
}

function validateCommunityMcqAttempt(data) {
  const payload = data || {};
  const postId = typeof payload.postId === "string" ? payload.postId.trim() : "";
  const optionIndex = payload.optionIndex;
  if (!postId || postId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid MCQ postId is required.");
  }
  if (!Number.isInteger(optionIndex) || optionIndex < 0 || optionIndex > 4) {
    throw makeValidationError("invalid-argument", "optionIndex must be an integer from 0 to 4.");
  }
  return { postId, optionIndex };
}


const COMMUNITY_EXAM_SUBJECTS = require("./communityExamSubjects.json");

function resolveCommunityExamSubject(targetExam, subject) {
  const examName = typeof targetExam === "string" ? targetExam.trim().toLocaleLowerCase() : "";
  const subjectName = typeof subject === "string" ? subject.trim().toLocaleLowerCase() : "";
  if (!examName || !subjectName) {
    throw makeValidationError("invalid-argument", "Registered exam and subject are required.");
  }
  const matches = COMMUNITY_EXAM_SUBJECTS.exams.filter(exam =>
    [exam.examId, exam.examName, exam.shortName].some(alias => alias.toLocaleLowerCase() === examName)
  );
  if (matches.length !== 1) {
    throw makeValidationError("invalid-argument", "Exam is not present in the Community subject registry.");
  }
  const exam = matches[0];
  if (!exam.subjects.some(registered => registered.toLocaleLowerCase() === subjectName)) {
    throw makeValidationError("invalid-argument", "Subject is not registered for the selected exam.");
  }
  return { examId: exam.examId, examName: exam.examName, subject: exam.subjects.find(registered => registered.toLocaleLowerCase() === subjectName) };
}

const SAFE_SHARED_QUESTION_FIELDS = [
  "id", "testId", "questionText", "questionTextEn", "questionTextHi",
  "optionA", "optionB", "optionC", "optionD", "optionE",
  "optionAEn", "optionBEn", "optionCEn", "optionDEn", "optionEEn",
  "optionAHi", "optionBHi", "optionCHi", "optionDHi", "optionEHi",
  "options", "questionType", "positiveMarks", "negativeMarks",
  "section", "sectionId", "sectionName", "subject", "chapter",
  "topic", "difficulty", "imageUrl", "needsImage"
];

function sanitizeSharedQuestionProjection(question, testId, index = 0) {
  if (!question || typeof question !== "object" || Array.isArray(question)) {
    throw makeValidationError("failed-precondition", "Shared Community question is malformed.");
  }
  const id = typeof question.id === "string" && question.id.trim()
    ? question.id.trim()
    : `${testId}_q${index}`;
  const safe = {};
  for (const field of SAFE_SHARED_QUESTION_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(question, field)) safe[field] = question[field];
  }
  safe.id = id;
  safe.testId = testId;
  return safe;
}

function splitLegacySharedQuestions(rawQuestions, testId) {
  if (!Array.isArray(rawQuestions) || rawQuestions.length === 0 || rawQuestions.length > 200) {
    throw makeValidationError("failed-precondition", "Legacy Community test has no valid question list or exceeds the migration limit.");
  }
  const publicQuestions = [];
  const privateKeys = [];
  const seenIds = new Set();
  for (let index = 0; index < rawQuestions.length; index += 1) {
    const question = rawQuestions[index];
    if (!question || typeof question !== "object" || Array.isArray(question)) {
      throw makeValidationError("failed-precondition", "Legacy Community test contains a malformed question.");
    }
    const id = typeof question.id === "string" && question.id.trim()
      ? question.id.trim()
      : `${testId}_q${index}`;
    const questionText = typeof question.questionText === "string"
      ? question.questionText.trim()
      : (typeof question.questionTextEn === "string" ? question.questionTextEn.trim() : "");
    const correctAnswer = typeof question.correctAnswer === "string"
      ? question.correctAnswer.trim()
      : (typeof question.answer === "string" ? question.answer.trim() : "");
    if (!questionText || !correctAnswer || seenIds.has(id)) {
      throw makeValidationError("failed-precondition", "Legacy Community test contains a missing question, answer key or duplicate question ID.");
    }
    seenIds.add(id);
    publicQuestions.push(sanitizeSharedQuestionProjection({ ...question, id }, testId, index));
    privateKeys.push({
      id,
      correctAnswer,
      explanation: typeof question.explanation === "string" ? question.explanation : "",
      explanationHi: typeof question.explanationHi === "string" ? question.explanationHi : ""
    });
  }
  return { publicQuestions, privateKeys };
}


function validateCommunityReplyCreate(data) {
  const payload = data || {};
  const postId = typeof payload.postId === "string" ? payload.postId.trim() : "";
  const content = typeof payload.content === "string" ? payload.content.trim() : "";
  const requestId = typeof payload.requestId === "string" ? payload.requestId.trim() : "";
  if (!postId || postId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid postId is required.");
  }
  if (!content || content.length > 2000) {
    throw makeValidationError("invalid-argument", "Reply text must contain 1 to 2000 characters.");
  }
  if (!requestId || requestId.length > 100) {
    throw makeValidationError("invalid-argument", "A stable requestId is required for duplicate protection.");
  }
  return { postId, content, requestId };
}

function validateCommunityPostDelete(data) {
  const payload = data || {};
  const postId = typeof payload.postId === "string" ? payload.postId.trim() : "";
  if (!postId || postId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid postId is required.");
  }
  return { postId };
}

function validateCommunityReplyDelete(data) {
  const payload = data || {};
  const postId = typeof payload.postId === "string" ? payload.postId.trim() : "";
  const commentId = typeof payload.commentId === "string" ? payload.commentId.trim() : "";
  if (!postId || postId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid postId is required.");
  }
  if (!commentId || commentId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid commentId is required.");
  }
  return { postId, commentId };
}

function validateCommunityReaction(data) {
  const payload = data || {};
  const postId = typeof payload.postId === "string" ? payload.postId.trim() : "";
  if (!postId || postId.length > 150) {
    throw makeValidationError("invalid-argument", "A valid postId is required.");
  }
  if (typeof payload.liked !== "boolean") {
    throw makeValidationError("invalid-argument", "liked must be a boolean desired state.");
  }
  return { postId, liked: payload.liked };
}

module.exports = {
  validateCommunityReplyCreate,
  validateCommunityPostDelete,
  validateCommunityReplyDelete,
  validateCommunityReaction,
  validateCommunityMcqCreate,
  validateCommunityMcqAttempt,
  resolveCommunityExamSubject,
  splitLegacySharedQuestions,
  sanitizeSharedQuestionProjection
};
