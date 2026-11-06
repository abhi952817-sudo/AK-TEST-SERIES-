const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  RANKING_CONFIG,
  resolveRankingWeight,
  calculateWeightedRankingScore,
  getIndiaDateKey,
  getIndiaWeekId,
  getRankingPeriodIds,
  getLeagueForXp,
  calculateXpAward,
  calculateCompetitionRank,
  calculatePercentileFromCounts,
  getRankingStatus
} = require("./rankingEngine");

test("ranking score normalizes different test maxima before weight", () => {
  assert.equal(calculateWeightedRankingScore(360, 720, 1), 5000);
  assert.equal(calculateWeightedRankingScore(45, 90, 1), 5000);
  assert.equal(calculateWeightedRankingScore(45, 90, 0.2), 1000);
});

test("ranking score rejects invalid maxima and unsupported weights", () => {
  assert.throws(() => calculateWeightedRankingScore(10, 0, 1), { code: "invalid-argument" });
  assert.throws(() => calculateWeightedRankingScore(10, 20, 0.5), { code: "invalid-argument" });
});

test("test weight resolution recognizes part and chapter tests", () => {
  assert.equal(resolveRankingWeight({ category: "Chapter Test" }), 0.2);
  assert.equal(resolveRankingWeight({ isPartTest: true }), 0.2);
  assert.equal(resolveRankingWeight({ category: "Full Syllabus Mock" }), 1);
  assert.equal(resolveRankingWeight({ rankingWeight: 0.2 }), 0.2);
  assert.equal(resolveRankingWeight({ rankingWeight: 0.5, category: "Part Test" }), 0.2);
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
test("class cohort ranking status is not mislabeled as AIR", () => {
  const { getCohortRankingStatus } = require("./rankingEngine");
  assert.equal(getCohortRankingStatus(1000, "All").status, "Official AK AIR");
  assert.equal(getCohortRankingStatus(1000, "11th").status, "Official AK Class Rank");
  assert.equal(getCohortRankingStatus(999, "12th").status, "Provisional Class Rank");
  assert.equal(getCohortRankingStatus(1000, "11th").isOfficialAkAir, false);
});
