"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  validateCommunityMcqPost,
  gradeCommunityMcqAttempt,
  validateCommunityReportTarget,
} = require("./communityMvp");

test("Community MCQ accepts exactly four or five distinct non-empty options", () => {
  assert.equal(validateCommunityMcqPost({
    postType: "MCQ", creatorRole: "STUDENT", title: "Question?", quizOptions: ["A", "B", "C", "D"],
  }).valid, true);
  assert.equal(validateCommunityMcqPost({
    postType: "MCQ", creatorRole: "STUDENT", title: "Question?", quizOptions: ["A", "B", "C", "D", "E"],
  }).valid, true);
});

test("Community MCQ rejects missing question, 3/6 options, blank and duplicate options", () => {
  assert.equal(validateCommunityMcqPost({ postType: "MCQ", creatorRole: "STUDENT", title: "", quizOptions: ["A", "B", "C", "D"] }).reason, "QUESTION_REQUIRED");
  assert.equal(validateCommunityMcqPost({ postType: "MCQ", creatorRole: "STUDENT", title: "Q", quizOptions: ["A", "B", "C"] }).reason, "OPTIONS_MUST_BE_FOUR_OR_FIVE");
  assert.equal(validateCommunityMcqPost({ postType: "MCQ", creatorRole: "STUDENT", title: "Q", quizOptions: ["A", "B", "C", "D", "E", "F"] }).reason, "OPTIONS_MUST_BE_FOUR_OR_FIVE");
  assert.equal(validateCommunityMcqPost({ postType: "MCQ", creatorRole: "STUDENT", title: "Q", quizOptions: ["A", "", "C", "D"] }).reason, "OPTIONS_MUST_BE_NON_EMPTY");
  assert.equal(validateCommunityMcqPost({ postType: "MCQ", creatorRole: "STUDENT", title: "Q", quizOptions: ["A", "a", "C", "D"] }).reason, "OPTIONS_MUST_BE_UNIQUE");
});

test("Community MCQ grading validates bounds and reveals correctness only after a selected option", () => {
  assert.deepEqual(gradeCommunityMcqAttempt(2, 2, 4), {
    selectedOptionIndex: 2, correctOptionIndex: 2, isCorrect: true,
  });
  assert.equal(gradeCommunityMcqAttempt(1, 2, 5).isCorrect, false);
  assert.throws(() => gradeCommunityMcqAttempt(-1, 2, 4));
  assert.throws(() => gradeCommunityMcqAttempt(4, 2, 4));
  assert.throws(() => gradeCommunityMcqAttempt(1, 5, 5));
});

test("Community reports require a post or user target", () => {
  assert.equal(validateCommunityReportTarget({ postId: "post-1" }), true);
  assert.equal(validateCommunityReportTarget({ reportedUserId: "user-1" }), true);
  assert.equal(validateCommunityReportTarget({}), false);
});

test("Community MCQ rejects untrusted or missing creator role metadata", () => {
  assert.equal(validateCommunityMcqPost({
    postType: "MCQ", creatorRole: "UNKNOWN", title: "Question?", quizOptions: ["A", "B", "C", "D"],
  }).reason, "CREATOR_ROLE_INVALID");
});
