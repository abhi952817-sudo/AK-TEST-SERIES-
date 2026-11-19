# Branch Handoff — feature/ranking-engine-v2-2026-11-06

**Status:** BLOCKED — audit and selective integration only  
**Reviewed against Main:** 2026-11-19  
**Branch HEAD:** `59d45f61051ff11e0234075eafd74c88f0bc75fd`  
**Main HEAD at review:** `213d1c7aa547159ad8ec1ffa8c78b74a3f5732b0`  
**Compare:** 110 commits ahead, 132 behind; 14 changed files.  
**PR:** [#1](https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/1) — keep unmerged.

## Why this branch is not a merge candidate

The branch is substantially divergent and overlaps backend scoring, server functions, Android ViewModel, Firestore rules/indexes and tests. It cannot be safely reconciled as a whole. Its backend currently uses the default Firestore database in places, while Main must continue using the named database:

`ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf`

Do not copy `backend_functions/index.js`, rules, indexes or ViewModel wholesale. Keep the source branch unchanged as evidence.

## Findings to correct before any selective port

1. **Unavailable rank must not become rank zero.** The rank-query error path previously inspected assigns `testRank=0`, `testTotalCandidates=0` and `attemptPercentile=0.0`, then returns/persists rank-shaped data. Represent unavailable ranking explicitly (nullable/status/error) and do not persist fabricated values.
2. **Retry must preserve unavailable state.** The retry path uses fallback expressions equivalent to `percentile || 0.0`, `testRank || 1` and `testTotalCandidates || 1`. These manufacture plausible results. Preserve null/unavailable and retry only the failed computation.
3. **Respect profile visibility at leaderboard creation.** The leaderboard write uses a public display name fallback without consistently applying the user's privacy setting. An authenticated-readable leaderboard must not reveal a private profile's display name. Add public/private rules and data-shape tests.
4. **Define concurrency semantics.** Rank/population reads outside the transaction can observe a stale population under concurrent submissions. Decide whether ranking is an explicitly eventual snapshot or implement a consistent transactional/materialized design; do not describe the existing read as transactionally consistent.
5. **Named database compatibility is a hard gate.** Ported server functions must resolve the canonical Main database ID above, never silently call default `admin.firestore()`.

## Selective integration plan

- Start each code change from current Main, not this branch.
- Keep the change small (roughly 2–4 files): rank availability contract, retry behavior, or privacy filtering can be separate PRs.
- Add backend unit tests for provider/query failures, retry idempotency and nullable rank fields.
- Add Firestore rules/data-shape tests for private/public profiles.
- Add concurrency tests or document the accepted eventual-consistency semantics.
- Verify duplicate attempt handling, first qualifying attempt, tie policy, percentile denominator, exam isolation and eligibility before enabling ranking.
- Require exact-head CI for backend, Firestore rules and Android consumers before merge.

## Branch relationship / overlap

`feature/ranking-universal-integration-v1` is a much larger downstream integration workspace (477 ahead /132 behind Main; 44 changed files) and overlaps this branch's ranking engine plus scoring, PDF pipeline, Room migration and ViewModel. `feature/universal-exam-scoring-v1-2026-11-06` is also divergent (148 ahead /132 behind; 22 files) and changes shared scoring/backend/ViewModel/Room files. Do not merge these branches into one another or Main wholesale. Reconcile their contracts first, then port isolated functions and tests.

## Validation evidence and limitations

The earlier recorded exact-head CI run for this branch was `44535036938` and passed its listed backend, rules and Android jobs at that time. That historical CI result does not certify the current Main integration, does not clear the findings above and must be rerun after any port. No local Android/Firebase emulator or production database test is claimed.

## Completion checklist

- [ ] Rank query failure yields explicit unavailable state; never rank 0.
- [ ] Retry preserves null/unavailable fields and is idempotent.
- [ ] Private profile name is not exposed in leaderboard data.
- [ ] Concurrency/consistency semantics are specified and tested.
- [ ] Every server function uses Main's named Firestore database.
- [ ] Exact-head backend, rules and Android CI green.
- [ ] Main student UI distinguishes unavailable ranking from a real rank.
- [ ] No production backfill/deployment without separate approval.
