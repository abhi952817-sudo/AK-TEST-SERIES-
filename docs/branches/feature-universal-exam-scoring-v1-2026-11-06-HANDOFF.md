# Branch Handoff — feature/universal-exam-scoring-v1-2026-11-06

**Disposition:** KEEP UNMERGED — selective integration only  
**Reviewed:** 2026-11-19  
**Branch HEAD:** `a686861eea49bf15ddb7d8d350201e464ef721fe`  
**Main HEAD at comparison:** `59ce96b74f1a27b4e4acaea432d98ae03a436ce1`  
**GitHub compare:** 148 commits ahead, 151 behind; 22 changed files; diverged  
**Existing PR:** #2, Draft/open, targets Ranking V2 rather than Main (refresh PR topology before changing its base).

## What is useful

- Android scoring adds finite-number checks and exact-set MSQ matching in `ExamScoringEngine.kt`.
- Explicit `ungradedCount` is propagated in Android/backend scoring results.
- A Room 20→21 migration and schema snapshot are present.
- New backend `rankingEngine.js` isolates pure ranking helpers and has focused tests.
- Additional Kotlin/Node tests and CI workflow exist.
- A detailed remaining-work plan records parity and official-policy blockers.

These are candidates for selective porting, not proof that the full branch is correct.

## Confirmed blockers / incompatibilities

1. **Firestore database mismatch:** branch `backend_functions/index.js` initializes `const db = admin.firestore()` (default database). Main uses the user's canonical named database. Do not port this backend as-is or change the selected database ID.
2. **Ranking persistence is not solved by this migration:** branch `MIGRATION_20_21` only adds `ungradedCount`; it does not add ranking availability/status, rank, or total candidates. It does not resolve the Main PR #18 null-vs-zero contract.
3. **Destructive fallback remains:** branch database builder still uses `.fallbackToDestructiveMigration()`. The presence of migration 20→21 does not prove every supported historical upgrade path preserves data.
4. **Large overlap:** 22 files touch scoring, ranking, backend, Room, MainActivity, NEETViewModel and Firestore rules. Branch is 151 commits behind Main. Wholesale merge risks overwriting later Main security and data-contract work.
5. **Scoring policy is not fully canonical:** Android still has a configured `calculateScore(ExamConfig,...)` path that defaults missing section policy to the first configured section's marks. The branch plan itself flags this as unsafe.
6. **Parity remains open:** Android and backend are separate implementations; backend rounds aggregate fields to 2 decimals while Android may retain raw doubles. Shared, wired golden vectors and one explicit rounding policy are still needed.
7. **Official policy proof is incomplete:** registry/test constants and labels are not independent official-source verification for every exam-year/stage/paper/section.
8. **Ranking failure values remain fabricated in this branch:** backend initializes rank/total/percentile to zero, catches cohort query errors by resetting to zero, and retry path uses `existing.percentile || 0.0`, `existing.testRank || 1`, `existing.testTotalCandidates || 1`. This can turn unavailable ranking into plausible values.
9. **Default database and rules must be reviewed together:** branch changes Firestore rules and indexes; do not port rules or indexes without validating all Main collections, named database target, and emulator tests.

## Selective integration plan

1. Treat `UNIVERSAL_EXAM_SCORING_REMAINING_WORK_PLAN.md` as a candidate audit checklist, then reconcile its statements against current Main rather than copying it verbatim.
2. Extract only pure scoring behaviors with tests: finite numeric parsing, exact-set MSQ normalization, explicit ungraded accounting. First compare against Main's current strict numerical fix and existing sanitizer/answer-key contract to avoid duplicate logic.
3. Define one versioned scoring contract: canonical examId/year/stageId/paperId/sectionId/questionType, marking rules, optional caps, numeric tolerance, partial credit, rounding, ungraded status, and source provenance.
4. Wire one shared JSON golden-vector file into Kotlin and Node tests. Vectors must distinguish synthetic mechanics from official-source-backed policy.
5. Remove silent Android default marks and backend unknown-exam fallback; reject missing/ambiguous config.
6. Correct optional-section selection and max-score computation in both engines.
7. Reconcile ranking separately against Main's ranking-availability contract and canonical named Firestore database.
8. Verify every Room migration path, backup/restore compatibility, and no data loss before schema changes.
9. Run exact-head backend, rules, Android unit, Gradle and APK CI. CI success is not a substitute for official policy review or device/runtime tests.

## Required tests before any merge

- Shared Kotlin/Node parity vectors for MCQ, MSQ, NAT/TITA, malformed/non-finite values, duplicates, missing IDs, unknown types and unverified keys.
- Optional section caps, deterministic selection, dropped/bonus questions, partial marking only when verified, fractional marks and rounding.
- Unknown/empty exam and missing section config fail closed (never silently use NEET or +4/-1).
- Ranking cohort query failure, zero candidates, retry/idempotency and null/unavailable status.
- Named Firestore database integration and rules/index tests.
- Room upgrades across all supported versions, preserving attempt history; backup export/import and Firestore backup/restore.
- Android/backend exact-head CI and APK artifact.

## Safety decision

Do not merge PR #2 or this branch wholesale. Keep source branch intact. Port small, independently proven units only after Main compatibility is checked. No production deployment, Firestore rule loosening, data migration, or database-ID change is authorized by this handoff.
