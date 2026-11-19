# Branch Consolidation Audit — 2026-11-19

## Purpose and safety rules

This tracker records the first-pass inventory and comparison of every branch against the then-current `main`. It is not a claim that every code path has been fully reviewed or tested.

Rules:
- Preserve all branch heads and commits until their unique work is reviewed and safely integrated or explicitly classified as obsolete.
- Never delete a branch merely because it is behind, old, or has overlapping filenames.
- Do not bulk-merge composite integration branches together with their source branches.
- Review file-level patches and tests before selecting changes. Resolve overlap by behavior and contract, not by blindly choosing "ours" or "theirs".
- Keep incomplete work recorded here and continue it after safe consolidation.
- No production Firebase deployment, live data migration, destructive database operation, or secret change is authorized by this tracker.
- A branch comparison is a change inventory, not a code-quality verdict.

## Baseline

- Repository: `abhi952817-sudo/AK-TEST-SERIES-`
- Default branch: `main`
- Baseline main SHA at audit start: `ae58ed7162a195a41dfd6f3d523a4bb37658ef83`
- Integration branch created from that exact SHA: `integration/verified-branch-consolidation-2026-11-19`
- Branch inventory: 25 total (main + 24 non-main)
- Pull requests visible at audit time: 13 (PR #1–#14, with #? gap; exact list below)
- Important: re-fetch main before each integration batch; the baseline can move.

## First-pass branch inventory

Ahead/behind values are relative to the baseline main SHA above.

| Branch | Head SHA (short) | Ahead | Behind | First-pass disposition |
|---|---:|---:|---:|---|
| audit/all-exam-scoring-ranking-2026-11-13 | a89fb90 | 42 | 14 | Review scoring contract, golden vectors, policy schemas and CI; likely audit artifacts useful, code changes need patch review |
| audit/default-firestore-consolidation-2026-11-15 | c7f5d6b | 13 | 2 | Preserve audit and execution-plan docs; compare recommendations against selected default DB implementation |
| copilot/firestore-default-clean | dabeeae | 0 | 1 | No unique commits ahead of main; no merge needed unless later evidence identifies untracked content |
| copilot/standardize-firestore-default | 412d440 | 3 | 1 | Overlaps Firestore implementation; inspect its three commits against the more recent Firestore fix |
| feature/community-completion-2026-11-13 | ed032e1 | 183 | 3 | Large Community candidate; overlaps UI, NEETViewModel, backend, Firestore and Storage; review against PR #8 and composite integration |
| feature/community-development-plan-2026-11-13 | f9b8c2f | 33 | 6 | PR #6 was merged to main historically, but branch has later divergent work; review only unique delta |
| feature/community-mvp-completion-2026-11-13 | c2a0fd5 | 64 | 3 | Community MVP implementation candidate; overlaps other Community branches and rules/backend |
| feature/ranking-engine-v2-2026-11-06 | bacf9d3 | 105 | 109 | Ranking V2 candidate; old divergent base and policy decisions remain; PR #1 draft |
| feature/ranking-universal-integration-v1 | 703ea68 | 477 | 109 | Composite scoring/ranking integration; PR #3 explicitly records unresolved blockers; do not merge as-is |
| feature/secure-user-owned-ai-connectors | dabeeae | 0 | 1 | Same head as copilot/firestore-default-clean; no unique commits ahead of main |
| feature/student-universal-ai-connectors-v1 | 5dc2955 | 59 | 1 | Student AI connector work; overlaps Gemini branch and NEETViewModel/settings/backup; inspect key storage and tests |
| feature/universal-exam-scoring-v1-2026-11-06 | 6b99fb1 | 147 | 109 | Scoring and Room 20→21 migration candidate; PR #2 targets ranking branch, not main; requires migration/parity review |
| feature/universal-gemini-ai-10-phase-2026-11-08 | a2dd36c | 392 | 109 | Large AI/PDF/student connector composite; overlaps scoring, Community, settings, NEETViewModel and Room schema |
| feature/universal-pdf-prompt-contract-v1 | fbafd0c | 143 | 102 | PDF prompt/syllabus pipeline candidate; PR #4 draft; overlaps AI branch and NEETViewModel/registry/pipeline |
| fix/community-ci-recovery-2026-11-13 | 944b5f7 | 75 | 3 | PR #7 merged into Community feature branch, not main; preserve its fixes through Community consolidation |
| fix/firestore-default-database-2026-11-17 | c82c61b | 4 | 1 | PR #14 draft; four-file default DB switch; data inventory and runtime checks remain blockers |
| fix/phase1-real-questions-only | e75003b | 196 | 109 | Broad real-question/security candidate; overlaps AI/PDF/scoring/Community; static patch review required |
| fix/remaining-firebase-analytics-2026-11-15 | 6e5b192 | 201 | 3 | Broad Firebase/analytics/Community/backup candidate; overlaps composite integration and Firestore fixes |
| fix/retain-single-firestore-db-9fa | e12bdb | 6 | 2 | Competing Firestore database choice; do not merge until project database inventory resolves the intended target |
| fix/unify-firestore-database | 828dfa2 | 19 | 1 | Competing Firestore consolidation; overlaps analytics, Community and database config |
| fix/use-default-firestore-everywhere | faa5fb9 | 32 | 2 | Competing default Firestore implementation; overlaps #14 and Firebase analytics/Community branch |
| integration/community-firestore-default-2026-11-15 | ba45c04 | 207 | 3 | Composite Community + Firestore + analytics integration; do not merge together with all source branches |
| integration/root-to-fruit-master-2026-11-13 | e91120d | 18 | 3 | Scoring changes plus master plans; inspect unique backend scoring diff and tests |
| restore-files/removed-env-gitignore-2026-10-19 | 3614f1c | 2 | 144 | Mostly behind; contains remaining-work documentation and .env/gitignore delta; review secrets hygiene before any code selection |
| usi | 81a09ee | 4 | 144 | Mostly behind; contains .env.example, assets ignore and CBT remaining-work plan; review for secret leakage and current relevance |

## Pull-request inventory and gates

- PR #14 — Firestore default database: Draft. Its description explicitly says no build, emulator, production read/write, data migration, deployment or merge was performed. It does not migrate data from named databases to (default).
- PR #13 — Firestore database unification: Draft; overlaps other Firestore PRs.
- PR #12 — Community + default Firestore composite: Draft. Its description requires combined CI, emulator verification, live database inventory, verified backup, staging restore drill and multi-account device QA. Server-side subject verification remains a stated gate.
- PR #11 — Firestore consolidation audit docs: Draft; documentation candidate.
- PR #10 — Default Firestore everywhere: Draft; overlaps PR #14 and other Firestore branches.
- PR #9 — Retain named Firestore database: Draft; conflicts with the default-database direction and needs factual project inventory before decision.
- PR #8 — Community MVP completion: Draft. Its description reports earlier test evidence but final head checks/device QA and production operations remain outstanding.
- PR #7 — Community CI recovery: merged into `feature/community-completion-2026-11-13`, not directly into main.
- PR #6 — Community foundations: merged to main historically; branch still has later divergent changes.
- PR #5 — All-exam scoring/ranking audit: Draft.
- PR #4 — Universal PDF prompt contract: Draft.
- PR #3 — Ranking + universal scoring integration: Draft and explicitly blocked by Android CrashResilienceTest, answer-key security, PDF fallback, exam configuration, Android scoring parity, Room migration, Storage security and ranking fairness.
- PR #2 — Universal scoring: Open, not merged; targets Ranking V2 branch, not main. Current Room migration/debug-build and exam policy review required.
- PR #1 — Ranking V2: Draft; current head needs fresh full CI and unresolved AIR/XP policy decisions require owner review.

## Cross-branch overlap clusters

1. **Firestore/default DB:** audit/default-firestore-consolidation, copilot/standardize-firestore-default, fix/firestore-default-database, fix/retain-single-firestore-db-9fa, fix/unify-firestore-database, fix/use-default-firestore-everywhere, fix/remaining-firebase-analytics, integration/community-firestore-default.
2. **Community:** feature/community-development-plan, feature/community-mvp-completion, feature/community-completion, fix/community-ci-recovery, fix/remaining-firebase-analytics, integration/community-firestore-default.
3. **Ranking/scoring:** feature/ranking-engine-v2, feature/universal-exam-scoring, feature/ranking-universal-integration, audit/all-exam-scoring-ranking, integration/root-to-fruit-master, fix/phase1-real-questions-only.
4. **AI/PDF/connectors:** feature/universal-gemini-ai-10-phase, feature/universal-pdf-prompt-contract, feature/student-universal-ai-connectors, fix/phase1-real-questions-only, feature/ranking-universal-integration.

Repeated high-conflict paths include:
- `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`
- `backend_functions/index.js`
- `firestore.rules`, `firestore.test.js`, `storage.rules`, `storage.test.js`
- `app/src/main/java/Com/ak/testseries/data/remote/FirestoreHelper.kt`
- `app/src/main/java/Com/ak/testseries/exam/UniversalExamRegistry.kt`
- `app/src/main/java/Com/ak/testseries/exam/UniversalPdfPipeline.kt`
- `backend_functions/examScoring.js`
- `app/src/main/java/Com/ak/testseries/MainActivity.kt`
- `app/src/main/res/values/firebase_applet_config.xml`
- `firebase.json`

## Work not yet completed

- This is a branch inventory and comparison only. Every changed file has not yet been reviewed line-by-line.
- No code branch has been merged into this integration branch yet.
- No source branch has been deleted.
- No CI workflow has been run by this audit.
- Android build/device tests, Firebase Emulator tests, production database inventory and live data checks remain unverified here.
- Need fetch exact commit histories and file patches for each candidate; identify commits already represented in main and avoid duplicate cherry-picks.
- Need establish actual Firebase database/data ownership before selecting a Firestore database target.
- Need review the Room schema/migration chain before accepting schema version changes.
- Need inspect answer-key exposure, server-side authorization, duplicate attempts, scoring parity, fake-question fallback, community sharing sanitization, API key storage and PDF provenance.

## Next execution order

1. Re-fetch main and all branch heads.
2. Review Firestore candidate patches and live-data prerequisites; choose one coherent implementation only after project database inventory evidence.
3. Review Community candidates and CI evidence; consolidate one source of truth for UI/backend/rules/tests.
4. Review scoring and Ranking V2 as a coherent chain, including migration, fairness policy and all regression tests.
5. Review AI/PDF/connectors and real-question-only safeguards; consolidate overlapping prompt, extractor, registry and provider code.
6. Review the remaining documentation/config branches.
7. Integrate only reviewed changes into this branch, run CI, open a PR to main, and merge only when all applicable gates pass.
8. Retain/archive branches with incomplete or rejected work until the tracker explicitly records that their unique work is preserved or obsolete.

## Status vocabulary

- `UNREVIEWED`: only branch comparison or PR description inspected.
- `REVIEWING`: code patches/tests being inspected.
- `READY`: reviewed, non-overlapping, tests pass for available environments.
- `PARTIAL`: useful work exists but known items remain incomplete.
- `BLOCKED`: required evidence/environment/decision is missing.
- `INTEGRATED`: safely present in integration branch and verified.
- `MERGED`: PR merged to main and main re-verified.
- `OBSOLETE`: unique work proven unnecessary or superseded; preserve audit evidence before branch cleanup.


## Recheck update — 2026-11-19 (current repository snapshot)

- Current `main`: `724defaa487bfc64863402992474b1044198ae98` (commit `refactor: improve security and AI provider management`).
- Current branch count: **27 total** (main + 26 non-main, including this audit integration branch). The earlier baseline count of 25 was the earlier snapshot and is superseded for current inventory.
- Current open PR count: **11**. Firestore PRs #9, #10, #12, #13 and #14 are overlapping/competing database-target proposals; they must not be stacked or merged together.
- Android CI run `44528426052` for current main completed **success**. This does not replace emulator/device/live Firebase tests.
- F-016 recheck: ordinary `submitTestAttemptServerSide` success and idempotent responses do not return `solutionKeys`; separate `getTestAttemptSolutions` callable returns them behind attempt/review authorization. Mark ordinary-response leak resolved by static review; test dedicated callable authorization at runtime.
- F-009 recheck: current ViewModel forces offline mock fallback false and deprecates the setter; named `generateMockAiTest` function was not found in current file. Keep failure-path audit open until every PDF/AI failure path is shown to stop without publishing questions.
- F-023 recheck: current Community public-copy code removes `correctAnswer` and `explanation`; maintain test coverage as a gate.
- M-001 through M-005 and M-007 remain open as recorded in `MAIN_COMMIT_REVIEW_2026-11-19.md`.
- Markdown policy: update existing branch-specific plan/audit files where available; do not create one report per branch. If a branch has no relevant Markdown file, record its status in this master tracker.
- No feature code merged, no branch deleted, no Firestore target changed, and no production data touched in this recheck.


## Markdown review inventory — first pass (2026-11-19)

The branch Markdown inventory is being done in topic batches. Existing files are being read and retained as the source of truth; this is not a reason to create one report per branch.

| Branch/workstream | Existing Markdown reviewed | What it contains | Current audit note |
|---|---|---|---|
| Community completion | `docs/community/COMMUNITY_GAP_AUDIT_2026-11-14.md` | Community composer, verification card, moderation, server validation and release gaps | Useful detailed audit; stale branch-head/CI references must be refreshed before relying on its pass claims. |
| Ranking V2 | `docs/RANKING_ENGINE_V2_REMAINING_WORK_PLAN.md` | Branch purpose, safety rules, remaining work, ranking release gates | Keep separate from universal scoring and composite integration; branch SHA in document is historical. |
| Universal scoring | `docs/UNIVERSAL_EXAM_SCORING_REMAINING_WORK_PLAN.md` | Scoring contract, migration and parity checklist | Preserve as scoring-specific checklist; compare current branch head and tests before porting. |
| Universal Gemini | `docs/UNIVERSAL_GEMINI_AI_10_PHASE_TRACKER.md` | Ten-phase AI plan: prompt engine, student key path, PDF, exam recognition, question generation, approval and reliability | Useful phase tracker; status dated 2026-11-08 and needs current evidence refresh. |
| PDF prompt contract | `docs/EXAM_SYLLABUS_READINESS_MATRIX.md` | Exam-wise syllabus readiness and source-verification requirements | Important warning: registry config does not prove authoritative syllabus coverage; verify claims against current official sources before implementation. |
| Scoring/ranking audit | `docs/ALL_EXAM_SCORING_RANKING_AUDIT_2026-11-13.md` | Combined scoring/ranking findings and integration gates | Retain as audit evidence; refresh against current main and current branch heads. |
| Firestore consolidation | `docs/DATABASE_CONSOLIDATION_AUDIT_2026-11-15.md`, `docs/FIRESTORE_DEFAULT_CONSOLIDATION_EXECUTION_PLAN.md` | Competing database targets, inventory/backup/restore and cutover steps | Keep all DB choices blocked until read-only live project inventory and restore rehearsal. |
| Firestore default candidate | `docs/PRODUCTION_FIREBASE_QA_CHECKLIST.md`, `docs/PRODUCTION_FIRESTORE_BACKUP_RESTORE_RUNBOOK.md` | Production QA and backup/restore gates | Useful runbooks; not proof that those operations have been performed. |
| Student AI connectors | `docs/STUDENT_UNIVERSAL_AI_CONNECTOR_CHECKPOINT.md` and branch checkpoint README | Student-owned connector/key work and checkpoint state | Must verify current encryption implementation, UID isolation, key deletion/replacement and tests. |
| Real-questions-only | `docs/REAL_QUESTIONS_ONLY_REMAINING_WORK_PLAN.md` | Fail-closed extraction/generation and remaining reliability work | Preserve; verify actual failure paths and zero-publication behavior. |

Inventory coverage is **partial**, not all 27 branch Markdown trees yet. Continue remaining branches in batches and update this table rather than creating more files. Current main/CI/F-016/F-009/F-023 and AI registry findings are recorded in `MAIN_COMMIT_REVIEW_2026-11-19.md`.


## Active implementation progress — 2026-11-19

### Small fixes in review
- PR #15: strict numerical scoring equivalence and backend Node test CI step — https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/15
  - Latest reviewed head: `764078eef2da6a51e707c692adc75fb0e745b028`.
  - Android CI run `44530788784` was still in progress at last poll. Merge gate: require successful workflow and visible pass of `node --test backend_functions/examScoring.test.js`.
- PR #16: remove unused client-side Firestore provider metadata sync and Admin secret resource identifiers — https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/16
  - Head: `9293f723c088f4a575fbacf6ad5aca8f47d037ec`.
  - Android CI run `44530905224` was still in progress at last poll. Merge gate: successful Android build/unit tests.
- Neither PR has been merged; main remains at `724defaa487bfc64863402992474b1044198ae98`.

### Student AI connector branch review
- Branch head `5dc2955d27e28a61f5e06b49caff09cf9da1d6ae`; dedicated CI run `43803404969` passed on that exact SHA.
- Existing branch checkpoint was updated with the review findings.
- Do not merge wholesale: its providers are Gemini/OpenAI/Groq/Anthropic/Cerebras, which conflicts with the required Gemini/Groq/Mistral/Cloudflare/Hugging Face set. PDF/multimodal pipeline integration and physical-device AndroidKeyStore verification also remain incomplete.
- Preserve its AES-GCM and UID-isolation implementation as a reference for a later selective port into the one approved Universal AI Router.

### Next work
Proceed with small independent PRs only where the change is safe and tested. For large branch deltas (Community, Ranking, Universal PDF, Universal AI), inspect existing branch plans and unique code, record blockers in those existing docs, then port cohesive slices with CI rather than wholesale merging.
