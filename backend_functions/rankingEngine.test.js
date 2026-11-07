const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  RANKING_CONFIG,
  resolveRankingWeight,
  calculateWeightedRankingScore,
  getIndiaDateKey,
  getIndiaWeekId,
  getRankingPeriodIds,
  isValidRankingPeriodId,
  clampRankingLimit,
  isValidRankingWeekId,
  getLeagueForXp,
  calculateDailyStreak,
  calculateXpAward,
  calculateCompetitionRank,
  calculatePercentileFromCounts,
  buildTestRankingSnapshot,
  getRankingStatus
} = require("./rankingEngine");

test("answer payload canonicalization makes object key order irrelevant but preserves values", () => {
  const { canonicalizeAnswerPayload } = require("./rankingEngine");
  const left = canonicalizeAnswerPayload({ q2: { ans: "B", confidence: 1 }, q1: "A" });
  const right = canonicalizeAnswerPayload({ q1: "A", q2: { confidence: 1, ans: "B" } });
  assert.equal(left, right);
  assert.notEqual(
    canonicalizeAnswerPayload({ q1: "A" }),
    canonicalizeAnswerPayload({ q1: "B" })
  );
  assert.throws(() => canonicalizeAnswerPayload([]), { code: "invalid-argument" });
});

test("ranking score normalizes different test maxima before weight", () => {
  assert.equal(calculateWeightedRankingScore(360, 720, 1), 5000);
  assert.equal(calculateWeightedRankingScore(45, 90, 1), 5000);
  assert.equal(calculateWeightedRankingScore(45, 90, 0.2), 1000);
});

test("ranking score rejects invalid maxima and unsupported weights", () => {
  assert.throws(() => calculateWeightedRankingScore(10, 0, 1), { code: "invalid-argument" });
  assert.throws(() => calculateWeightedRankingScore(10, 20, 0.5), { code: "invalid-argument" });
  assert.throws(() => calculateWeightedRankingScore(21, 20, 1), { code: "invalid-argument" });
  assert.throws(() => calculateWeightedRankingScore(-1, 20, 1), { code: "invalid-argument" });
});

test("test weight resolution recognizes part and chapter tests", () => {
  assert.equal(resolveRankingWeight({ category: "Chapter Test" }), 0.2);
  assert.equal(resolveRankingWeight({ isPartTest: true }), 0.2);
  assert.equal(resolveRankingWeight({ category: "Full Syllabus Mock" }), 1);
  assert.equal(resolveRankingWeight({ rankingWeight: 0.2 }), 0.2);
  assert.equal(resolveRankingWeight({ rankingWeight: 0.5, category: "Part Test" }), null);
  assert.equal(resolveRankingWeight({ testType: "Mock Test" }), null);
  assert.equal(resolveRankingWeight({ category: "Full Syllabus Mock" }), 1);
  assert.equal(resolveRankingWeight({ isFullTest: true }), 1);
  assert.equal(resolveRankingWeight({ isChapterTest: true }), 0.2);
  assert.equal(resolveRankingWeight({ category: "Medical Entrance", testType: "Full Test" }), 1);
  assert.equal(resolveRankingWeight({ category: "Full Test", testType: "Chapter Test" }), null);
});

test("India date, weekly and half-month periods use Asia/Kolkata", () => {
  const utc = Date.UTC(2026, 10, 1, 19, 0, 0); // 2 Nov 2026, 00:30 IST
  assert.equal(getIndiaDateKey(utc), "2026-11-02");
  assert.equal(getRankingPeriodIds(utc).halfMonthId, "2026-11-H1");
  assert.match(getIndiaWeekId(utc), /^2026-W\d{2}$/);
  assert.equal(getRankingPeriodIds(Date.UTC(2026, 10, 20)).halfMonthId, "2026-11-H2");
});

test("XP levels have non-overlapping thresholds and monotonic progression", () => {
  assert.equal(getLeagueForXp(0), "ROOKIE");
  assert.equal(getLeagueForXp(500), "ROOKIE");
  assert.equal(getLeagueForXp(501), "SCHOLAR");
  assert.equal(getLeagueForXp(2001), "ELITE");
  assert.equal(getLeagueForXp(5001), "MASTER");
  assert.equal(getLeagueForXp(10001), "CONQUEROR");
});

test("XP award is capped and deterministic", () => {
  assert.equal(calculateXpAward(0), 10);
  assert.equal(calculateXpAward(5), 20);
  assert.equal(calculateXpAward(1000), 370);
});

test("competition ranking uses competition ties and deterministic ordering", () => {
  const { entries, target } = calculateCompetitionRank([
    { userId: "c", score: 90 },
    { userId: "b", score: 100 },
    { userId: "a", score: 100 },
    { userId: "d", score: 80 }
  ], "c");
  assert.deepEqual(entries.map(x => x.rank), [1, 1, 3, 4]);
  assert.equal(target.rank, 3);
});

test("percentile handles ties and empty cohorts", () => {
  assert.equal(calculatePercentileFromCounts(7, 2, 10), 90);
  assert.equal(calculatePercentileFromCounts(0, 1, 10), 10);
  assert.equal(calculatePercentileFromCounts(0, 0, 0), 0);
});

test("official AK AIR threshold is 1000 eligible participants", () => {
  assert.equal(getRankingStatus(999).isOfficialAkAir, false);
  assert.equal(getRankingStatus(1000).isOfficialAkAir, true);
  assert.equal(getRankingStatus(1000).status, "Official AK AIR");
});
test("class level normalization supports class 11, class 12 and dropper cohorts", () => {
  const { normalizeClassLevel } = require("./rankingEngine");
  assert.equal(normalizeClassLevel("Class 11"), "11th");
  assert.equal(normalizeClassLevel("11th"), "11th");
  assert.equal(normalizeClassLevel("CLASS_12"), "12th");
  assert.equal(normalizeClassLevel("Dropper"), "Dropper");
  assert.equal(normalizeClassLevel("All"), "All");
});
test("class cohort filters reject unknown values instead of silently widening to All", () => {
  const { resolveClassLevelFilter } = require("./rankingEngine");
  assert.equal(resolveClassLevelFilter(undefined), "All");
  assert.equal(resolveClassLevelFilter("All"), "All");
  assert.equal(resolveClassLevelFilter("Class 11"), "11th");
  assert.equal(resolveClassLevelFilter("Class 12"), "12th");
  assert.equal(resolveClassLevelFilter("Dropper"), "Dropper");
  assert.equal(resolveClassLevelFilter("Class 10"), null);
  assert.equal(resolveClassLevelFilter("Unknown"), null);
});

test("class cohort ranking status is not mislabeled as AIR", () => {
  const { getCohortRankingStatus } = require("./rankingEngine");
  assert.equal(getCohortRankingStatus(1000, "All").status, "Official AK AIR");
  assert.equal(getCohortRankingStatus(1000, "11th").status, "Official AK Class Rank");
  assert.equal(getCohortRankingStatus(999, "12th").status, "Provisional Class Rank");
  assert.equal(getCohortRankingStatus(1000, "11th").isOfficialAkAir, false);
});


test("historical ranking backfill accepts only approved public official ranking tests", () => {
  const { isEligibleHistoricalRankingTest } = require("./rankingEngine");
  const base = {
    isOfficial: true,
    isPublic: true,
    countsTowardRanking: true,
    status: "Published"
  };
  assert.equal(isEligibleHistoricalRankingTest(base), true);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, isPublic: false }), false);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, isOfficial: false }), false);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, countsTowardRanking: false }), false);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, status: "Under Review" }), false);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, status: "Rejected" }), false);
  assert.equal(isEligibleHistoricalRankingTest({ ...base, status: "" }), false);
});

test("ranking rejects scoring results with ungraded or invalid marks", () => {
  const { isEligibleScoringResult } = require("./rankingEngine");
  assert.equal(isEligibleScoringResult({ finalScore: 3.5, maxScore: 10, ungradedCount: 0 }), true);
  assert.equal(isEligibleScoringResult({ finalScore: 3.5, maxScore: 10, ungradedCount: 1 }), false);
  assert.equal(isEligibleScoringResult({ finalScore: 11, maxScore: 10, ungradedCount: 0 }), false);
  assert.equal(isEligibleScoringResult({ finalScore: 0, maxScore: 0, ungradedCount: 0 }), false);
  assert.equal(isEligibleScoringResult({ finalScore: NaN, maxScore: 10, ungradedCount: 0 }), false);
  assert.equal(isEligibleScoringResult({ finalScore: 3, maxScore: 10 }), false);
  assert.equal(isEligibleScoringResult({ finalScore: 3, maxScore: 10, ungradedCount: -1 }), false);
});


test("scoring result with any ungraded question cannot contribute to AIR", () => {
  const { evaluateTestAttempt } = require("./examScoring");
  const { isEligibleScoringResult } = require("./rankingEngine");
  const result = evaluateTestAttempt(
    "NEET UG",
    [
      { id: "q1", correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" },
      { id: "q2", correctAnswer: "", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" }
    ],
    { q1: { ans: "A" }, q2: { ans: "B" } },
    60
  );
  assert.equal(result.ungradedCount, 1);
  assert.equal(isEligibleScoringResult(result), false);

  const fullyVerified = evaluateTestAttempt(
    "NEET UG",
    [{ id: "q1", correctAnswer: "A", section: "phy_all", subject: "Physics", questionType: "MCQ_SINGLE" }],
    { q1: { ans: "A" } },
    30
  );
  assert.equal(fullyVerified.ungradedCount, 0);
  assert.equal(isEligibleScoringResult(fullyVerified), true);
});


test("per-test rank snapshot only ranks the canonical first qualifying board entry", () => {
  const first = buildTestRankingSnapshot({
    countsTowardRanking: true,
    isFirstQualifyingAttempt: true,
    canonicalEntry: { score: 80, isBanned: false },
    higherCount: 1,
    totalCount: 5
  });
  assert.deepEqual(first, {
    rankStatus: "AVAILABLE", testRank: 2, testTotalCandidates: 5, percentile: 80
  });
  const repeat = buildTestRankingSnapshot({
    countsTowardRanking: true,
    isFirstQualifyingAttempt: false,
    canonicalEntry: { score: 80 },
    higherCount: 1,
    totalCount: 5
  });
  assert.deepEqual(repeat, {
    rankStatus: "NOT_RANKED_REPEAT_ATTEMPT", testRank: null,
    testTotalCandidates: null, percentile: null
  });
});

test("per-test rank snapshot fails closed for missing board entries and invalid populations", () => {
  const unavailable = buildTestRankingSnapshot({
    countsTowardRanking: true, isFirstQualifyingAttempt: true,
    canonicalEntry: null, higherCount: 0, totalCount: 0
  });
  assert.equal(unavailable.rankStatus, "UNAVAILABLE");
  assert.equal(unavailable.testRank, null);
  const invalidPopulation = buildTestRankingSnapshot({
    countsTowardRanking: true, isFirstQualifyingAttempt: true,
    canonicalEntry: { score: 50, isBanned: false }, higherCount: 4, totalCount: 4
  });
  assert.equal(invalidPopulation.rankStatus, "UNAVAILABLE");
  const notEligible = buildTestRankingSnapshot({ countsTowardRanking: false });
  assert.equal(notEligible.rankStatus, "NOT_ELIGIBLE");
  assert.equal(notEligible.percentile, null);
});


test("ranking period and weekly leaderboard identifiers reject malformed dates", () => {
  assert.equal(isValidRankingPeriodId("HALF_MONTH", "2026-11-H1"), true);
  assert.equal(isValidRankingPeriodId("HALF_MONTH", "2026-11-H2"), true);
  assert.equal(isValidRankingPeriodId("MONTH", "2026-11"), true);
  for (const id of ["2026-00-H1", "2026-13-H2", "2026-11-H3", "2026-11", "2026-1-H1"]) {
    assert.equal(isValidRankingPeriodId("HALF_MONTH", id), false);
  }
  assert.equal(isValidRankingPeriodId("MONTH", "2026-11-H1"), false);
  assert.equal(isValidRankingPeriodId("MONTH", "2026-99"), false);
  assert.equal(isValidRankingPeriodId("MONTH", "0000-11"), false);
  assert.equal(isValidRankingPeriodId("HALF_MONTH", "0000-11-H1"), false);
  assert.equal(isValidRankingWeekId("2026-W01"), true);
  assert.equal(isValidRankingWeekId("2026-W53"), true);
  assert.equal(isValidRankingWeekId("2025-W53"), false);
  assert.equal(isValidRankingWeekId("2020-W53"), true);
  assert.equal(isValidRankingWeekId("2021-W53"), false);
  assert.equal(isValidRankingWeekId("2026-W00"), false);
  assert.equal(isValidRankingWeekId("2026-W54"), false);
});


test("ranking query limits are finite integers within endpoint bounds", () => {
  assert.equal(clampRankingLimit(undefined, 100, 1, 100), 100);
  assert.equal(clampRankingLimit(2.9, 100, 1, 100), 2);
  assert.equal(clampRankingLimit(0, 100, 1, 100), 1);
  assert.equal(clampRankingLimit(-5, 100, 1, 100), 1);
  assert.equal(clampRankingLimit(1000, 10, 1, 20), 20);
  assert.equal(clampRankingLimit("not-a-number", 10, 1, 20), 10);
  assert.equal(clampRankingLimit(Infinity, 10, 1, 20), 10);
});


test("per-test rank snapshot does not rank a banned canonical board entry", () => {
  const result = buildTestRankingSnapshot({
    countsTowardRanking: true, isFirstQualifyingAttempt: true,
    canonicalEntry: { score: 100, isBanned: true }, higherCount: 0, totalCount: 1
  });
  assert.equal(result.rankStatus, "UNAVAILABLE");
  assert.equal(result.testRank, null);
});


test("daily streak updates on consecutive days and never increments twice in one day", () => {
  assert.equal(calculateDailyStreak("2026-11-06", "2026-11-06", 4), 4);
  assert.equal(calculateDailyStreak("2026-11-06", "2026-11-07", 4), 5);
  assert.equal(calculateDailyStreak("2026-11-04", "2026-11-07", 4), 1);
  assert.equal(calculateDailyStreak("", "2026-11-07", 0), 1);
  assert.equal(calculateDailyStreak("invalid", "2026-11-07", 2), 1);
  assert.equal(calculateDailyStreak("2026-11-06", "2026-02-30", 4), 4);
  assert.equal(calculateDailyStreak("2026-02-30", "2026-11-07", 2), 1);
});
