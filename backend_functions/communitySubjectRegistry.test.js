"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  COMMUNITY_SUBJECT_REGISTRY,
  getCommunityRegisteredSubjects,
  isCommunitySubjectValid,
  findCommunityExam,
} = require("./communitySubjectRegistry");

test("Community server subject registry matches the canonical Kotlin registry exactly", () => {
  const kotlinPath = path.join(
    __dirname,
    "../app/src/main/java/Com/ak/testseries/exam/UniversalExamRegistry.kt"
  );
  const source = fs.readFileSync(kotlinPath, "utf8");
  const blocks = source.split(/^\s*\/\/ \d+\./m).slice(1);
  const canonical = [];

  for (const block of blocks) {
    const examId = block.match(/examId\s*=\s*"([^"]+)"/)?.[1];
    const examName = block.match(/examName\s*=\s*"([^"]+)"/)?.[1];
    const shortName = block.match(/shortName\s*=\s*"([^"]+)"/)?.[1];
    if (!examId || !examName || !shortName) continue;
    const subjects = [...block.matchAll(/SubjectConfig\(\s*"[^"]+"\s*,\s*"([^"]+)"/g)]
      .map(match => match[1]);
    canonical.push({ examId, examName, shortName, subjects: [...new Set(subjects)] });
  }

  assert.deepEqual(COMMUNITY_SUBJECT_REGISTRY, canonical);
  assert.equal(COMMUNITY_SUBJECT_REGISTRY.length, 23);
});

test("Community subject validation uses exact registered subject labels and aliases", () => {
  assert.equal(isCommunitySubjectValid("NEET", "Physics"), true);
  assert.equal(isCommunitySubjectValid("NEET UG", "Botany"), true);
  assert.equal(isCommunitySubjectValid("NEET", "Mathematics"), false);
  assert.equal(isCommunitySubjectValid("UNKNOWN EXAM", "Physics"), false);
  assert.deepEqual(getCommunityRegisteredSubjects("unknown"), []);
  assert.equal(findCommunityExam("JEE")?.examId, "jee_main");
  assert.equal(isCommunitySubjectValid("SSC CGL", "Quantitative Aptitude"), true);
});
