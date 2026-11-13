function makeValidationError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function validateCommunityMcqCreate(data) {
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
  if (payload.imageUrl != null && payload.imageUrl !== "") {
    if (typeof payload.imageUrl !== "string" || payload.imageUrl.length > 2048) {
      throw makeValidationError("invalid-argument", "Invalid MCQ image URL.");
    }
    let parsed;
    try { parsed = new URL(payload.imageUrl); } catch (_) {
      throw makeValidationError("invalid-argument", "Invalid MCQ image URL.");
    }
    if (parsed.protocol !== "https:" || !["firebasestorage.googleapis.com", "storage.googleapis.com"].includes(parsed.hostname)) {
      throw makeValidationError("invalid-argument", "MCQ images must use the approved Firebase Storage host.");
    }
    imageUrl = payload.imageUrl;
  }
  return { title, question, subject, targetExam, options: cleanOptions, correctOptionIndex, requestId, imageUrl };
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

module.exports = { validateCommunityMcqCreate, validateCommunityMcqAttempt };
