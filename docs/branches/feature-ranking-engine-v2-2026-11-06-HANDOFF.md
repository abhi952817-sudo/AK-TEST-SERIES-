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


## Android compatibility gate discovered 2026-11-19

Main `NEETViewModel.kt` reads missing server percentile as `0.0` in multiple submission/result paths (approximately lines 468, 4623, 6645 and 6682 in the reviewed blob). Therefore the backend null-state proposal in PR #18 must not be merged until the Android result model/UI distinguishes unavailable ranking from a real percentile value. Review the relevant Attempt/Result data model and UI before changing it; this may require more than the 2–4-file safe-fix limit and should then be documented as a separate work item. PR #18 exact-head CI was not yet available at this checkpoint.


## Refreshed comparison against Main — 2026-11-19 latest checkpoint

- Main HEAD: `c8eee7a56ce409695c93585815c276fc85687c18`.
- Ranking V2 branch HEAD remains `59d45f61051ff11e0234075eafd74c88f0bc75fd`.
- Latest compare: **110 commits ahead / 139 behind**, 14 changed files; status `diverged`. The branch is now seven commits further behind than the prior recorded snapshot. It remains unsuitable for wholesale merge.
- PR #18 exact head: `fda2d304cad601d43d50eb6fdb8f3068662f4615`. GitHub currently reports PR #18 open, `mergeable=false`; do not merge or assume the PR's backend proposal is integrated.
- Exact-head workflow run `44541619345` (Android CI) is currently in progress. Backend syntax and Backend Scoring Tests steps have completed successfully; Android Unit Tests are still running; Gradle build and APK upload are pending. This is **not a completed CI pass**.
- Main consumer review confirms null percentile is converted to `0.0` in the direct rank-details fetch and in pending-attempt sync / cloud-attempt hydration. The normal submission path falls back to the local attempt percentile, which can also be a fabricated/default value. This is a data-contract issue, not a safe one-line backend-only fix.
- Required next design: make rank availability explicit in the persisted attempt/result contract (for example nullable rank fields plus a status enum), propagate it through Room entity/migration, Firestore serialization/hydration, ViewModel and result UI, and distinguish “not applicable”, “pending”, and “query unavailable”. Audit all readers before choosing the representation. This exceeds the 2–4-file small-fix boundary; document and plan it before code changes.
- The latest compare also confirms broad overlap remains in `MainActivity.kt`, `NEETViewModel.kt`, `backend_functions/index.js`, Firestore rules/indexes and tests. Keep PR #1 and the branch as evidence only.

