"use strict";

function validateCommunityMcqPost(post) {
  if (!post || post.postType !== "MCQ") {
    return { valid: false, reason: "POST_NOT_MCQ" };
  }
  const question = typeof post.title === "string" ? post.title.trim() : "";
  const options = post.quizOptions;
  if (!question) return { valid: false, reason: "QUESTION_REQUIRED" };
  if (!Array.isArray(options) || ![4, 5].includes(options.length)) {
    return { valid: false, reason: "OPTIONS_MUST_BE_FOUR_OR_FIVE" };
  }
  const normalized = options.map(value => typeof value === "string" ? value.trim() : "");
  if (normalized.some(value => !value || value.length > 500)) {
    return { valid: false, reason: "OPTIONS_MUST_BE_NON_EMPTY" };
  }
  if (new Set(normalized.map(value => value.toLocaleLowerCase())).size !== normalized.length) {
    return { valid: false, reason: "OPTIONS_MUST_BE_UNIQUE" };
  }
  return { valid: true, options: normalized };
}

function gradeCommunityMcqAttempt(selectedOptionIndex, correctOptionIndex, optionsCount) {
  if (!Number.isInteger(optionsCount) || ![4, 5].includes(optionsCount)) {
    throw new Error("MCQ options are invalid.");
  }
  if (!Number.isInteger(selectedOptionIndex) || selectedOptionIndex < 0 || selectedOptionIndex >= optionsCount) {
    throw new Error("Selected option is invalid.");
  }
  if (!Number.isInteger(correctOptionIndex) || correctOptionIndex < 0 || correctOptionIndex >= optionsCount) {
    throw new Error("The private answer key is invalid.");
  }
  return {
    selectedOptionIndex,
    correctOptionIndex,
    isCorrect: selectedOptionIndex === correctOptionIndex,
  };
}

function validateCommunityReportTarget(input) {
  const postId = typeof input?.postId === "string" ? input.postId.trim() : "";
  const reportedUserId = typeof input?.reportedUserId === "string" ? input.reportedUserId.trim() : "";
  return Boolean(postId || reportedUserId);
}

module.exports = {
  validateCommunityMcqPost,
  gradeCommunityMcqAttempt,
  validateCommunityReportTarget,
};
