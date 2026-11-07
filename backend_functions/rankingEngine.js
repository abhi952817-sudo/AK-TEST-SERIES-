/**
 * AK Test Series Ranking Engine v2.
 * Pure, deterministic helpers shared by callable functions and unit tests.
 * Scores are normalized to a 10,000-point full-test scale before weight is applied.
 */
const RANKING_CONFIG = Object.freeze({
  version: 2,
  minQualifyingTests: 2,
  officialParticipantThreshold: 1000,
  fullTestWeight: 1.0,
  partTestWeight: 0.2,
  scoreScale: 10000,
  xp: Object.freeze({
    testCompletion: 10,
    correctAnswer: 2,
    maxPerTest: 370,
    rookieMax: 500,
    scholarMax: 2000,
    eliteMax: 5000,
    masterMax: 10000
  })
});

function canonicalizeAnswerPayload(answers) {
  if (!answers || typeof answers !== "object" || Array.isArray(answers)) {
    const err = new Error("Answer payload must be a JSON object.");
    err.code = "invalid-argument";
    throw err;
  }
  const normalize = value => {
    if (Array.isArray(value)) return value.map(normalize);
    if (value && typeof value === "object") {
      const sorted = {};
      for (const key of Object.keys(value).sort()) sorted[key] = normalize(value[key]);
      return sorted;
    }
    return value;
  };
  return JSON.stringify(normalize(answers));
}

function finiteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function resolveRankingWeight(testData = {}) {
  const explicit = testData.rankingWeight;
  if (explicit !== undefined && explicit !== null) {
    const weight = Number(explicit);
    return weight === 1 || weight === 0.2 ? weight : null;
  }

  const labels = [testData.category, testData.testCategory, testData.testType]
    .filter(value => typeof value === "string")
    .join(" ")
    .trim()
    .toLowerCase();
  const isPart = testData.isPartTest === true ||
    testData.isChapterTest === true ||
    /\b(chapter|topic|subject|part)\b/.test(labels);
  const isFull = testData.isFullTest === true ||
    /\b(full|complete|grand)\b/.test(labels);

  // Conflicting or absent metadata is not safe to classify.
  if (isPart && isFull) return null;
  if (isPart) return RANKING_CONFIG.partTestWeight;
  if (isFull) return RANKING_CONFIG.fullTestWeight;

  // Never guess that an ambiguous "mock test" is a full test. An admin must
  // explicitly classify it before it can contribute to competitive ranking.
  return null;
}

/**
 * Normalizes each test against its own maximum marks, then applies weight.
 * Full tests contribute 0..10,000 points; part/chapter tests contribute 0..2,000.
 * Returns an integer so existing Android clients reading Firestore Long values remain compatible.
 */
function isEligibleHistoricalRankingTest(testData = {}) {
  const status = String(testData.status || "").trim().toUpperCase();
  return testData.isOfficial === true &&
    testData.isPublic === true &&
    testData.countsTowardRanking === true &&
    (status === "APPROVED" || status === "PUBLISHED");
}

function isEligibleScoringResult(result = {}) {
  if (!result || typeof result !== "object" || Array.isArray(result)) return false;
  const score = finiteNumber(result.finalScore, NaN);
  const max = finiteNumber(result.maxScore, NaN);
  const ungraded = finiteNumber(result.ungradedCount, NaN);
  return Number.isFinite(score) && Number.isFinite(max) && Number.isInteger(ungraded) &&
    score >= 0 && max > 0 && score <= max + 0.000001 && ungraded === 0;
}

function calculateWeightedRankingScore(score, maxScore, weight = 1) {
  const raw = finiteNumber(score, NaN);
  const max = finiteNumber(maxScore, NaN);
  const w = finiteNumber(weight, NaN);
  if (!Number.isFinite(raw) || !Number.isFinite(max) || max <= 0 ||
      raw < 0 || raw > max + 0.000001 ||
      !Number.isFinite(w) || (w !== 1 && w !== 0.2)) {
    const err = new Error("Invalid score, maximum score, or ranking weight.");
    err.code = "invalid-argument";
    throw err;
  }
  const normalized = raw / max;
  return Math.round(normalized * RANKING_CONFIG.scoreScale * w);
}

function getIndiaDateParts(timestamp = Date.now()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date(timestamp));
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return { year: Number(values.year), month: Number(values.month), day: Number(values.day) };
}

function getIndiaDateKey(timestamp = Date.now()) {
  const { year, month, day } = getIndiaDateParts(timestamp);
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function getIndiaWeekId(timestamp = Date.now()) {
  const { year, month, day } = getIndiaDateParts(timestamp);
  const date = new Date(Date.UTC(year, month - 1, day));
  const weekday = date.getUTCDay() || 7;
  date.setUTCDate(date.getUTCDate() + 4 - weekday);
  const weekYear = date.getUTCFullYear();
  const yearStart = new Date(Date.UTC(weekYear, 0, 1));
  const week = Math.ceil((((date - yearStart) / 86400000) + 1) / 7);
  return `${weekYear}-W${String(week).padStart(2, "0")}`;
}

function getRankingPeriodIds(timestamp = Date.now()) {
  const { year, month, day } = getIndiaDateParts(timestamp);
  const monthId = `${year}-${String(month).padStart(2, "0")}`;
  return {
    halfMonthId: `${monthId}-H${day <= 15 ? 1 : 2}`,
    monthId
  };
}

function clampRankingLimit(value, fallback = 100, min = 1, max = 100) {
  if (value === undefined || value === null || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.floor(parsed)));
}

function isValidRankingPeriodId(periodType, periodId) {
  const type = String(periodType || "").toUpperCase();
  const id = String(periodId || "");
  if (type === "HALF_MONTH") return /^\d{4}-(0[1-9]|1[0-2])-H[12]$/.test(id);
  if (type === "MONTH") return /^\d{4}-(0[1-9]|1[0-2])$/.test(id);
  return false;
}

function isValidRankingWeekId(weekId) {
  return /^\d{4}-W(?:0[1-9]|[1-4]\d|5[0-3])$/.test(String(weekId || ""));
}

function resolveClassLevelFilter(value) {
  if (value === undefined || value === null || String(value).trim() === "") return "All";
  const normalized = normalizeClassLevel(value);
  if (normalized === "All" && String(value).trim().toLowerCase() !== "all") return null;
  return normalized;
}

function normalizeClassLevel(value) {
  const normalized = String(value || "").trim().toLowerCase().replace(/[\s_-]/g, "");
  if (["11", "11th", "class11", "class11th"].includes(normalized)) return "11th";
  if (["12", "12th", "class12", "class12th"].includes(normalized)) return "12th";
  if (["dropper", "repeater", "dropyear"].includes(normalized)) return "Dropper";
  return "All";
}

function calculateDailyStreak(previousDate, currentDate, existingStreak) {
  const current = String(currentDate || "");
  const previous = String(previousDate || "");
  const streak = Math.max(0, Math.floor(Number(existingStreak) || 0));
  if (!/^\d{4}-\d{2}-\d{2}$/.test(current)) return streak;
  if (previous === current) return streak;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(previous)) return 1;
  const currentMs = Date.parse(current + "T00:00:00.000Z");
  const previousMs = Date.parse(previous + "T00:00:00.000Z");
  if (!Number.isFinite(currentMs) || !Number.isFinite(previousMs)) return 1;
  return currentMs - previousMs === 86400000 ? streak + 1 : 1;
}

function getLeagueForXp(totalXp) {
  const xp = Math.max(0, Math.floor(finiteNumber(totalXp, 0)));
  if (xp <= RANKING_CONFIG.xp.rookieMax) return "ROOKIE";
  if (xp <= RANKING_CONFIG.xp.scholarMax) return "SCHOLAR";
  if (xp <= RANKING_CONFIG.xp.eliteMax) return "ELITE";
  if (xp <= RANKING_CONFIG.xp.masterMax) return "MASTER";
  return "CONQUEROR";
}

function calculateXpAward(correctCount) {
  const correct = Math.max(0, Math.floor(finiteNumber(correctCount, 0)));
  return Math.min(
    RANKING_CONFIG.xp.maxPerTest,
    RANKING_CONFIG.xp.testCompletion + correct * RANKING_CONFIG.xp.correctAnswer
  );
}

function calculateCompetitionRank(entries, targetUserId) {
  const sorted = [...entries].sort((a, b) => {
    const scoreDelta = finiteNumber(b.score) - finiteNumber(a.score);
    return scoreDelta !== 0 ? scoreDelta : String(a.userId).localeCompare(String(b.userId));
  });
  let previousScore = null;
  let currentRank = 0;
  const ranked = sorted.map((entry, index) => {
    const score = finiteNumber(entry.score);
    if (previousScore === null || score !== previousScore) currentRank = index + 1;
    previousScore = score;
    return { ...entry, rank: currentRank };
  });
  return {
    entries: ranked,
    target: ranked.find(entry => entry.userId === targetUserId) || null
  };
}

function buildTestRankingSnapshot({
  countsTowardRanking,
  isFirstQualifyingAttempt,
  canonicalEntry,
  higherCount,
  totalCount
} = {}) {
  const unavailable = (status) => ({
    rankStatus: status,
    testRank: null,
    testTotalCandidates: null,
    percentile: null
  });
  if (!countsTowardRanking) return unavailable("NOT_ELIGIBLE");
  if (!isFirstQualifyingAttempt) return unavailable("NOT_RANKED_REPEAT_ATTEMPT");
  if (!canonicalEntry || canonicalEntry.isBanned !== false || !Number.isFinite(Number(canonicalEntry.score))) {
    return unavailable("UNAVAILABLE");
  }
  const higher = Number(higherCount);
  const total = Number(totalCount);
  if (!Number.isInteger(higher) || !Number.isInteger(total) || higher < 0 || total < 1 || higher >= total) {
    return unavailable("UNAVAILABLE");
  }
  const rank = higher + 1;
  return {
    rankStatus: "AVAILABLE",
    testRank: rank,
    testTotalCandidates: total,
    percentile: total === 1 ? 100.0 : Math.round(((total - rank + 1) / total) * 10000) / 100
  };
}

function calculatePercentileFromCounts(countBelow, countEqual, totalCount) {
  const total = Math.max(0, Math.floor(finiteNumber(totalCount, 0)));
  if (total === 0) return 0;
  const below = Math.max(0, Math.floor(finiteNumber(countBelow, 0)));
  const equal = Math.max(0, Math.floor(finiteNumber(countEqual, 0)));
  if (equal === 0) return 0;
  return Math.round(Math.min(100, ((below + equal) / total) * 100) * 100) / 100;
}

function getRankingStatus(totalEligible) {
  const count = Math.max(0, Math.floor(finiteNumber(totalEligible, 0)));
  const isOfficialAkAir = count >= RANKING_CONFIG.officialParticipantThreshold;
  return {
    isOfficialAkAir,
    status: isOfficialAkAir ? "Official AK AIR" : "Provisional Rank",
    rankLabel: isOfficialAkAir ? "AK AIR" : "Provisional"
  };
}

function getCohortRankingStatus(totalEligible, classLevel) {
  const base = getRankingStatus(totalEligible);
  if (normalizeClassLevel(classLevel) === "All") {
    return { ...base, isOfficialClassRank: false };
  }
  return {
    ...base,
    isOfficialAkAir: false,
    isOfficialClassRank: base.isOfficialAkAir,
    status: base.isOfficialAkAir ? "Official AK Class Rank" : "Provisional Class Rank",
    rankLabel: "Class Rank"
  };
}

module.exports = {
  RANKING_CONFIG,
  canonicalizeAnswerPayload,
  resolveRankingWeight,
  calculateWeightedRankingScore,
  isEligibleScoringResult,
  isEligibleHistoricalRankingTest,
  getIndiaDateParts,
  getIndiaDateKey,
  getIndiaWeekId,
  getRankingPeriodIds,
  isValidRankingPeriodId,
  clampRankingLimit,
  isValidRankingWeekId,
  getIsoWeeksInYear,
  normalizeClassLevel,
  resolveClassLevelFilter,
  getLeagueForXp,
  calculateDailyStreak,
  calculateXpAward,
  calculateCompetitionRank,
  calculatePercentileFromCounts,
  buildTestRankingSnapshot,
  getRankingStatus,
  getCohortRankingStatus
};
