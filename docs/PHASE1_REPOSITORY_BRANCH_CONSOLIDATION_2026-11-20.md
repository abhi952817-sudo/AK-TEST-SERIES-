# Phase 1 — Repository and Branch Consolidation Audit
**Audit date:** 2026-11-20  
**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Default branch:** `main`  
**Main baseline SHA:** `29360378146d7c354377b4ea16ff01a7070057ef`

## 1. Safety boundary

This is a read-only source audit recorded on a documentation-only branch. No application source, Firebase configuration, production data, secrets, database, workflow history, or Main ref was changed.

- Main remains at the baseline SHA above.
- No PR was merged or retargeted.
- No branch was deleted, force-pushed, or reset.
- No Firebase deployment, database migration, export, or production read/write was performed.
- Branches are retained until unique work is proven integrated or obsolete.
- A branch comparison is an inventory, not a code-quality verdict.

## 2. Repository inventory

| Item | Count / state |
|---|---:|
| Branches returned by GitHub | 32 total, including Main |
| Non-Main branches | 31 |
| Pull requests | 19 |
| Open PRs | 14 |
| Merged PRs | 5 |
| Latest Main SHA audited | `29360378146d7c354377b4ea16ff01a7070057ef` |

## 3. Branch comparison against Main

Ahead/behind values below are from GitHub compare results with `main` as base and the branch as head. They describe commits unique to the branch and commits present on Main but absent from that branch at this snapshot.

| Branch | Ahead | Behind | First-pass disposition |
|---|---:|---:|---|
| `audit/all-exam-scoring-ranking-2026-11-13` | 42 | 58 | Preserve shared scoring policy, schemas and golden-vector work for review; exclude destructive workflow cleanup |
| `audit/default-firestore-consolidation-2026-11-15` | 13 | 46 | Preserve audit/runbook documents; database decision remains blocked |
| `copilot/firestore-default-clean` | 0 | 45 | No unique commits ahead of Main; no merge needed |
| `copilot/standardize-firestore-default` | 3 | 45 | Overlaps Firestore target changes; inspect only unique helper/backend delta |
| `feature/community-completion-2026-11-13` | 184 | 47 | Large Community candidate; no wholesale merge |
| `feature/community-development-plan-2026-11-13` | 33 | 50 | PR #6 baseline is merged; later branch delta still needs review |
| `feature/community-mvp-completion-2026-11-13` | 64 | 47 | Community MVP candidate; overlaps other Community branches |
| `feature/ranking-engine-v2-2026-11-06` | 110 | 153 | Deeply diverged Ranking V2 branch; selective review only |
| `feature/ranking-universal-integration-v1` | 477 | 153 | Large composite scoring/ranking branch; do not merge as-is |
| `feature/secure-user-owned-ai-connectors` | 0 | 45 | No unique commits ahead of Main; no merge needed |
| `feature/student-universal-ai-connectors-v1` | 60 | 45 | Preserve connector/key-store work for security and device review |
| `feature/universal-exam-scoring-v1-2026-11-06` | 148 | 153 | Scoring + Room migration candidate; review separately from Ranking V2 |
| `feature/universal-gemini-ai-10-phase-2026-11-08` | 392 | 153 | Large AI/PDF/connector composite; no wholesale merge |
| `feature/universal-pdf-prompt-contract-v1` | 143 | 146 | Preserve prompt contract and versioned syllabus resolver for review |
| `fix/community-answer-key-fail-closed-2026-11-19` | 1 | 24 | Already merged through PR #17; stale branch, no re-merge |
| `fix/community-ci-recovery-2026-11-13` | 75 | 47 | PR #7 merged into Community feature branch, not Main; retain its fixes in Community review |
| `fix/fail-closed-configured-scoring-2026-11-19` | 4 | 0 | PR #19 candidate; Android CI passes; backend parity gate remains |
| `fix/firestore-default-database-2026-11-17` | 4 | 45 | PR #14 candidate conflicts with named-database proposals |
| `fix/phase1-real-questions-only` | 196 | 153 | Preserve fail-closed question-generation work for focused review |
| `fix/ranking-unavailable-state-2026-11-19` | 5 | 16 | PR #18 candidate; Android null-state compatibility and ranking semantics remain |
| `fix/remaining-firebase-analytics-2026-11-15` | 201 | 47 | Broad analytics/Community/backup composite; do not merge wholesale |
| `fix/remove-client-ai-secret-metadata-2026-11-19` | 2 | 43 | Already merged through PR #16; no re-merge |
| `fix/retain-single-firestore-db-9fa` | 6 | 46 | Conflicting database target; blocked pending live inventory |
| `fix/strict-numerical-equivalence-2026-11-19` | 3 | 43 | Already merged through PR #15; no re-merge |
| `fix/unify-firestore-database` | 19 | 45 | Named-database consolidation proposal; conflicts with default target |
| `fix/use-default-firestore-everywhere` | 32 | 46 | PR #10 candidate; useful backup/security changes need selective review |
| `integration/community-firestore-default-2026-11-15` | 207 | 47 | Composite Community + Firestore + analytics; do not merge with source branches |
| `integration/root-to-fruit-master-2026-11-13` | 18 | 47 | Review only unique backend scoring delta and documentation |
| `integration/verified-branch-consolidation-2026-11-19` | 11 | 44 | Documentation-only relative to Main; existing audit is stale and must not be treated as current |
| `restore-files/removed-env-gitignore-2026-10-19` | 2 | 188 | Very old; inspect environment/ignore changes and docs before disposition |
| `usi` | 4 | 188 | Very old; inspect environment example and CBT documentation before disposition |

### Branch overlap clusters

1. **Firestore:** audit/default-firestore, copilot standardization, PR #9/#10/#13/#14, fix/unify, fix/retain, fix/default, Community composite and Firebase analytics branches.
2. **Community:** Community development, MVP, completion, CI recovery, Firebase analytics and Community/Firestore composite.
3. **Scoring/Ranking:** Ranking V2, universal scoring, ranking integration, scoring audit, root-to-fruit and real-questions-only branches.
4. **AI/PDF:** Gemini 10-phase, PDF prompt contract, student connectors, real-questions-only and ranking integration.

Repeated conflict paths include `NEETViewModel.kt`, `backend_functions/index.js`, `firestore.rules`, `firestore.test.js`, `storage.rules`, `storage.test.js`, `FirestoreHelper.kt`, `UniversalExamRegistry.kt`, `UniversalPdfPipeline.kt`, `examScoring.js`, `MainActivity.kt`, `firebase_applet_config.xml`, and `firebase.json`.

## 4. Pull request inventory

| PR | Target branch | Commits | State | Exact-head CI found | Disposition |
|---:|---|---:|---|---|---|
| #19 | main | 4 | Open, ready (not draft) | Android CI: success | Keep as a small candidate; require Android/backend scoring parity review before merge |
| #18 | main | 5 | Open, ready (not draft) | Android CI: success | Keep; update/rebase against current Main and verify nullable-rank consumers |
| #17 | main | 1 | Merged | Android CI: success | Already in Main; fail-closed answer-key sanitization |
| #16 | main | 2 | Merged | Android CI: success | Already in Main; removes unused client-side secret metadata |
| #15 | main | 3 | Merged | Android CI: success | Already in Main; strict numerical equivalence |
| #14 | main | 4 | Open, Draft | Android + Community Rules CI: success | Do not merge until database inventory and backup/restore gate |
| #13 | main | 19 | Open, Draft | Android + Community Rules CI: success | Competing named database target; do not stack with default-database PRs |
| #12 | main | 207 | Open, Draft | No exact-head PR run found | Large Community + default-Firestore composite; blocked |
| #11 | main | 13 | Open, Draft | Android CI: success | Documentation candidate; refresh stale baseline/counts |
| #10 | main | 32 | Open, Draft | Android + Community Rules CI: success | Selectively review backup ownership/pagination and runtime smoke tests; database target blocked |
| #9 | main | 6 | Open, Draft | Android + Community Rules CI: success | Competing named database target; blocked |
| #8 | main | 184 | Open, Draft | No exact-head PR run found | Large Community candidate; review subject validation and publication security |
| #7 | `feature/community-completion-2026-11-13` | 4 | Merged to feature branch | Android + Community Rules CI: success | Not merged to Main; retain via Community consolidation |
| #6 | main | 33 | Merged | Android + Community Rules CI: success | Community foundation already in Main |
| #5 | main | 42 | Open, Draft | No exact-head PR run found | Shared scoring policy/golden vectors useful for review; exclude destructive run-deletion workflow |
| #4 | main | 143 | Open, Draft | No exact-head PR run found | Preserve prompt contract/versioned syllabus work for selective review; no deploy workflow execution |
| #3 | `feature/ranking-engine-v2-2026-11-06` | 385 | Open, Draft | No exact-head PR run found | Stacked composite PR; not a Main merge candidate |
| #2 | `feature/ranking-engine-v2-2026-11-06` | 60 | Open | No exact-head PR run found | Depends on Ranking V2 branch; Room 20→21 migration and parity need full validation |
| #1 | main | 110 | Open, Draft | No exact-head PR run found | Ranking V2 is large and deeply diverged; no wholesale merge |

“CI not found” means the GitHub commit-workflow lookup returned no PR-triggered run for that exact head. It does not prove that no workflow ever ran on the branch.

## 5. CI and commit findings

- Exact-head Android CI success was found for PRs #19, #18, #14, #13, #11, #10 and #9.
- Exact-head Community Firebase Rules CI success was also found for #14, #13, #10 and #9.
- Merged PRs #17, #16, #15, #7 and #6 had successful relevant CI runs on their recorded heads.
- No exact-head PR-triggered run was found for #12, #8, #5, #4, #3, #2 or #1.
- Main's previously recorded Android CI run #44545697251 completed successfully. The combined-status lookup for the current documentation commit returned no status entries; this audit does not infer a new build from an older run.
- GitHub commit search returned the latest 100 repository commits. Branch ahead/behind comparisons and PR commit counts were collected for the full inventory. This report is not a claim that every commit in the very large composite branches has received a line-by-line code review. That review is a required gate before selecting their code.

## 6. Critical conflicts and risks

### Firestore database target conflict

The repository contains competing targets:
- `(default)` in PR #10 and PR #14.
- Named database `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf` in PR #13 and historical Android configuration.
- Named database `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612` in PR #9 / historical backend configuration.

These changes are not additive. PR #12 is a composite Community + default database proposal. Source code alone cannot prove which database holds authoritative production data. Keep all database-target changes blocked until the owner verifies every database in the actual Firebase project, exports/backs up data, rehearses restore, and reconciles records. No database switch or retirement is authorized by this audit.

### Scoring and Ranking

- PR #19 removes silent section-marking fallbacks in Android scoring, but does not update backend scoring. Do not merge it alone until Android/backend parity is established.
- PR #18 changes backend unavailable-rank values to null. Verify Android/UI consumers, profile visibility, population consistency and concurrency semantics before integration.
- PR #5's scoring policy deliberately leaves scoring/ranking disabled pending review. Preserve that safety default. Its CI includes a manually triggered workflow that deletes completed Actions runs across branches; exclude that workflow from any consolidation.
- PR #3 changes Room migration behavior and emergency reset/backup handling. Review SQLite WAL/SHM consistency, backup recovery and all migration paths before adopting it.
- PR #2 introduces Room schema 21 and a 20→21 migration; it requires the full migration chain and real-data migration tests.

### Community

- PR #8 and PR #12 are large and overlap Community UI, NEETViewModel, Functions, Firestore rules and Storage rules.
- PR #7 is merged into the Community feature branch, not Main.
- Server-side subject/exam validation, ownership checks, answer-key stripping, image-only upload constraints and publication fail-closed behavior remain mandatory review gates.
- Do not merge multiple Community branches or the composite branch together.

### AI/PDF

- Keep AI/PDF branches separate until prompt schema, real PDF extraction, source provenance, generated-question validation, no-fallback behavior, student-key protection and Admin approval are reviewed.
- PR #4 contains a Firebase deployment workflow. It has not been executed; no deployment permission is implied.
- The real-questions-only branch is a useful candidate for focused failure-path review, not a wholesale merge.

### Existing audit document is stale

The older `integration/verified-branch-consolidation-2026-11-19` tracker uses an earlier Main SHA and earlier branch/PR counts. It is useful historical evidence but not the current inventory. This report supersedes its counts for the 2026-11-20 snapshot.

## 7. Selective consolidation decision

**Already integrated:** PRs #15, #16 and #17 into Main; PR #6 into Main. PR #7 is only integrated into the Community feature branch.

**Keep as first small review candidates:**
1. PR #19 — Android fail-closed scoring; backend parity is a blocker.
2. PR #18 — explicit unavailable ranking state; Android nullable handling and ranking semantics are blockers.
3. PR #10 — review only the independent backup ownership/pagination and Firebase initialization test improvements. Do not adopt its database target until the live-data gate is complete.
4. PR #5 — review shared scoring schemas/golden vectors separately; exclude the run-deletion workflow.
5. PR #4 — review the prompt contract and versioned syllabus resolver separately; exclude deployment workflow execution.

**Keep isolated for deeper topic review:** Community (#8 and source branches), Ranking/Scoring (#1–#3), AI/PDF (#4 and Gemini branches), real-questions-only, student connectors, and Firebase analytics/backup composite.

**Do not merge or delete:** competing Firestore target PRs; large composite branches; branches with unreviewed unique work; old branches until their unique content and environment-file hygiene are verified.

## 8. Phase 1 completion gates

1. Review each selected PR's full diff and exact test coverage.
2. Resolve overlapping files by behavior and one canonical contract, not by blindly merging branch histories.
3. Port only cohesive, reviewed fixes into a fresh integration branch based on the current Main SHA.
4. Run Android unit tests/build, backend tests, Firestore/Storage emulator tests, and relevant migration/security tests on the combined branch.
5. Open a review PR targeting Main only after the integration branch is green.
6. Keep Main unchanged until a separate explicit merge approval.
7. Archive/delete branches only after their unique commits are proven integrated or explicitly classified obsolete, with approval.

**Current result:** inventory and first-pass disposition completed; no application code merged, no branch deleted, and Main preserved. Full commit-by-commit source review and selective integration remain the next work items.


## 9. Follow-up execution update (2026-11-20)

After the initial inventory, the following selective actions were taken on a separate integration branch:

- PR #19 was retargeted from `main` to `integration/ak-test-series-complete-2026-11-20` and squash-merged at commit `635e11ac72d96367364f32a85b78d7f36f74c6af`. Its Android configured-scoring path now rejects missing stage, paper, section configuration and unknown/missing question section IDs. It adds mixed-section and fail-closed regression tests.
- PR #18 was retargeted from `main` to the same integration branch and squash-merged at commit `c58438cf2235436c25c37b69fb32f8c2b20c8189`. Its backend per-test ranking snapshot now represents unavailable or inconsistent population counts as null and adds tests. Ranking population transaction consistency and privacy remain open issues.
- Both original PRs are now merged into the integration branch, not Main. Main remains at `29360378146d7c354377b4ea16ff01a7070057ef`.
- Draft PR #20 was opened from the integration branch to Main solely to trigger validation. It is not approved for merge. Its head SHA is `c58438cf2235436c25c37b69fb32f8c2b20c8189`.
- At the time of this addendum, PR #20's Android CI run `44714010245` was still in progress. No passing result is claimed until it completes.

These two changes are selective candidates, not a declaration that the entire scoring/ranking engine is complete. PR #19 covers the explicit configured `calculateScore` path and does not by itself remove all legacy scoring heuristics or prove backend parity.
