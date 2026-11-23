"use strict";

function scoringError(message, code = "failed-precondition") {
  const error = new Error(message);
  error.code = code;
  return error;
}

function requireKeyValue(value, name) {
  if (typeof value !== "string" || !value.trim()) {
    throw scoringError(`Missing required scoring identity: ${name}.`, "invalid-argument");
  }
  return value.trim();
}

function isValidVerifiedAt(value) {
  if (typeof value !== "string") return false;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,3})?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!match) return false;

  const [, yearText, monthText, dayText, hourText, minuteText, secondText, zone] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  if (year < 1 || month < 1 || month > 12 || hour > 23 || minute > 59 || second > 59) return false;

  const calendarDate = new Date(0);
  calendarDate.setUTCFullYear(year, month - 1, day);
  calendarDate.setUTCHours(hour, minute, second, 0);
  if (calendarDate.getUTCFullYear() !== year ||
      calendarDate.getUTCMonth() !== month - 1 ||
      calendarDate.getUTCDate() !== day) return false;

  if (zone !== "Z") {
    const offsetHours = Number(zone.slice(1, 3));
    const offsetMinutes = Number(zone.slice(4, 6));
    if (offsetHours > 23 || offsetMinutes > 59) return false;
  }
  return Number.isFinite(Date.parse(value));
}

/**
 * Resolve one exact policy. No display-name matching, year inference, nearest
 * version, default exam, or generic marking fallback is permitted.
 */
function resolveScoringPolicy(registry, key) {
  if (!registry || registry.schemaVersion !== 1 || registry.status !== "ACTIVE") {
    throw scoringError("The versioned scoring registry is missing, inactive, or unsupported.");
  }
  const exactKey = {
    examId: requireKeyValue(key && key.examId, "examId"),
    examYear: requireKeyValue(String(key && key.examYear || ""), "examYear"),
    scoringVersion: requireKeyValue(key && key.scoringVersion, "scoringVersion"),
    stageId: requireKeyValue(key && key.stageId, "stageId"),
    paperId: requireKeyValue(key && key.paperId, "paperId"),
    subjectId: requireKeyValue(key && key.subjectId, "subjectId"),
    sectionId: requireKeyValue(key && key.sectionId, "sectionId"),
    questionType: requireKeyValue(key && key.questionType, "questionType").toUpperCase()
  };

  if (!Array.isArray(registry.policies)) {
    throw scoringError("The scoring registry policies list is invalid.");
  }
  const matches = registry.policies.filter(policy =>
    policy &&
    policy.examId === exactKey.examId &&
    String(policy.examYear) === exactKey.examYear &&
    policy.scoringVersion === exactKey.scoringVersion &&
    policy.stageId === exactKey.stageId &&
    policy.paperId === exactKey.paperId &&
    policy.subjectId === exactKey.subjectId &&
    policy.sectionId === exactKey.sectionId &&
    String(policy.questionType || "").toUpperCase() === exactKey.questionType
  );

  if (matches.length === 0) {
    throw scoringError("No approved scoring policy matches the exact exam/year/stage/paper/subject/section/question type.");
  }
  if (matches.length !== 1) {
    throw scoringError("The scoring registry contains duplicate policies for the exact resolution key.");
  }

  const policy = matches[0];
  const source = policy.source || {};
  let sourceUrlIsValid = false;
  try {
    const parsedSourceUrl = new URL(source.url);
    sourceUrlIsValid = parsedSourceUrl.protocol === "https:" &&
      Boolean(parsedSourceUrl.hostname) &&
      !parsedSourceUrl.username &&
      !parsedSourceUrl.password;
  } catch (_) {
    sourceUrlIsValid = false;
  }
  if (
    policy.status !== "APPROVED" ||
    !sourceUrlIsValid ||
    typeof source.title !== "string" ||
    !source.title.trim() ||
    !isValidVerifiedAt(policy.verifiedAt) ||
    typeof policy.approvedBy !== "string" ||
    !policy.approvedBy.trim()
  ) {
    throw scoringError("The scoring policy is not approved or lacks verifiable source provenance.");
  }

  const positiveMarks = Number(policy.positiveMarks);
  const negativeMarks = Number(policy.negativeMarks);
  if (!Number.isFinite(positiveMarks) || positiveMarks <= 0 ||
      !Number.isFinite(negativeMarks) || negativeMarks < 0) {
    throw scoringError("The approved scoring policy contains invalid positive/negative marks.");
  }
  const maxAttemptQuestions = policy.maxAttemptQuestions == null
    ? null
    : Number(policy.maxAttemptQuestions);
  if (maxAttemptQuestions !== null &&
      (!Number.isInteger(maxAttemptQuestions) || maxAttemptQuestions <= 0)) {
    throw scoringError("The approved scoring policy contains an invalid section attempt limit.");
  }

  return {
    ...policy,
    positiveMarks,
    negativeMarks,
    maxAttemptQuestions,
    questionType: exactKey.questionType
  };
}

/**
 * Applies exact, source-backed policy marks to the server-loaded source questions.
 * The source's supplied marks are treated as consistency checks, never as authority.
 */
function validateVersionedTestScoring(registry, testData, questions) {
  if (!testData || !Array.isArray(questions) || questions.length === 0) {
    throw scoringError("A versioned test requires a source test record and questions.");
  }
  if (questions.length > 450) {
    throw scoringError("The versioned test exceeds the supported question limit.");
  }

  const identity = {
    examId: testData.examId,
    examYear: testData.examYear,
    scoringVersion: testData.scoringVersion,
    stageId: testData.stageId,
    paperId: testData.paperId
  };
  for (const [field, value] of Object.entries(identity)) {
    requireKeyValue(String(value == null ? "" : value), field);
  }

  if (testData.questionsCount != null &&
      Number(testData.questionsCount) !== questions.length) {
    throw scoringError("The source question count does not match the question records.");
  }

  const seenIds = new Set();
  const sectionGroups = new Map();
  const resolvedQuestions = questions.map(question => {
    if (!question || typeof question !== "object" || Array.isArray(question)) {
      throw scoringError("The versioned test contains a malformed question.");
    }
    const id = requireKeyValue(String(question.id || ""), "question.id");
    if (seenIds.has(id)) throw scoringError("The versioned test contains duplicate question IDs.");
    seenIds.add(id);

    const policy = resolveScoringPolicy(registry, {
      ...identity,
      subjectId: question.subjectId,
      sectionId: question.sectionId,
      questionType: question.questionType
    });

    if (question.positiveMarks != null) {
      const suppliedPositive = Number(question.positiveMarks);
      if (!Number.isFinite(suppliedPositive) || Math.abs(suppliedPositive - policy.positiveMarks) > 0.01) {
        throw scoringError(`Question ${id} positive marks conflict with the approved policy.`);
      }
    }
    if (question.negativeMarks != null) {
      const suppliedNegative = Number(question.negativeMarks);
      if (!Number.isFinite(suppliedNegative) || Math.abs(suppliedNegative - policy.negativeMarks) > 0.01) {
        throw scoringError(`Question ${id} negative marks conflict with the approved policy.`);
      }
    }

    const subject = policy.subjectName || question.subject;
    const section = policy.sectionName || question.section;
    if (typeof subject !== "string" || !subject.trim() ||
        typeof section !== "string" || !section.trim()) {
      throw scoringError(`Question ${id} is missing canonical subject/section display metadata.`);
    }

    const sectionKey = `${policy.subjectId}::${policy.sectionId}`;
    const existing = sectionGroups.get(sectionKey) || { cap: policy.maxAttemptQuestions, marks: [] };
    if (existing.cap !== policy.maxAttemptQuestions) {
      throw scoringError("Questions in the same section resolve to conflicting attempt limits.");
    }
    existing.marks.push(policy.positiveMarks);
    sectionGroups.set(sectionKey, existing);

    return {
      ...question,
      subjectId: policy.subjectId,
      sectionId: policy.sectionId,
      subject,
      section,
      questionType: policy.questionType,
      positiveMarks: policy.positiveMarks,
      negativeMarks: policy.negativeMarks,
      maxAttemptQuestions: policy.maxAttemptQuestions
    };
  });

  const calculatedMaxMarks = Array.from(sectionGroups.values()).reduce((total, group) => {
    const eligible = group.cap == null
      ? group.marks
      : group.marks.slice().sort((a, b) => b - a).slice(0, group.cap);
    return total + eligible.reduce((sum, mark) => sum + mark, 0);
  }, 0);

  const sourceTotalMarks = Number(testData.totalMarks);
  if (!Number.isFinite(sourceTotalMarks) || sourceTotalMarks <= 0 ||
      Math.abs(sourceTotalMarks - calculatedMaxMarks) > 0.01) {
    throw scoringError("The source totalMarks does not match the approved question-level scoring policy.");
  }

  return {
    questions: resolvedQuestions,
    calculatedMaxMarks: Math.round(calculatedMaxMarks * 100) / 100
  };
}

module.exports = {
  resolveScoringPolicy,
  validateVersionedTestScoring
};
