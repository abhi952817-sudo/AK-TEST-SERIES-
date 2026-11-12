# AK TEST SERIES — Universal Exam Scoring & Ranking: End-to-End Launch Roadmap
**Document:** Master phased delivery plan  
**Date:** 2026-11-07  
**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Target working branch:** `feature/ranking-universal-integration-v1` (or a dedicated child branch after checking the current HEAD)  
**Release target:** Safe, auditable, multi-exam scoring and ranking launch  
**Scope:** Exam research, registry, question model, scoring, ranking, Android UI, backend, migration, security, QA, staged release and operations.

---

## 0. Executive summary and current baseline

The previous integration-branch audit identified 25 exam identities: NEET UG and JEE Main Paper 1 were the two source-verified configurations; 23 identities remained pending official source verification and complete implementation. This is a historical audit baseline, not a substitute for re-reading the current branch HEAD and current exam-authority notifications before development.

The goal is **one universal scoring engine + one universal ranking engine + versioned exam/paper configurations**, not 25 separate scoring engines.

### Release principles
1. No guessed exam pattern, marks, negative marking, timer, question count, normalization or partial-credit rule.
2. No dummy questions or fake users/scores in production.
3. No destructive migration, silent score rewrite, or loss of historical attempts.
4. No duplicate/overlapping registry, scoring or ranking implementations.
5. No direct client write to authoritative score/rank materializations.
6. No unverified exam is eligible for official-style ranked tests.
7. `main`, production Firestore, production rules/indexes and Cloud Functions stay untouched until explicit release approval.
8. Every phase ends with evidence, tests and a go/no-go decision. A compiling build is not a completed phase.

## 1. Target system architecture

### 1.1 Single source of truth
- Canonical exam identity: stable `examId`.
- Versioned pattern identity: `examId + configVersion + sourceYear`.
- Exact paper resolution: `examId + stageId + paperId` and, where needed, discipline/post/subject variant.
- One authoritative exam configuration contract consumed by Android and backend, or generated typed models with automated parity tests.
- Historical attempts retain the config version used at submission.

### 1.2 Universal scoring engine
The same deterministic scoring rules must be reproducible on Android and backend:
- Correct, incorrect, unanswered and partially correct outcomes.
- Positive marks, negative marks, zero marks, fractional penalties and rounding.
- MCQ single, MCQ multiple, numerical/NAT/TITA, matching, statement, passage and descriptive response types only where supported.
- Numeric exactness/tolerance, integer-only response and units where the exam requires them.
- Bonus/dropped questions and revised answer keys.
- Section timing and locked sections as test-taking behavior, separate from score calculation.
- Official normalization only where source formula and required input data are available.
- Unknown or invalid inputs fail closed as UNGRADED/INELIGIBLE, never silently receive guessed marks.

### 1.3 Universal ranking engine
Retain one shared implementation for:
- Exam-wise AIR / overall rank where population definition is valid.
- Class/cohort boards (Class 11, Class 12, Dropper) only when cohort data is reliable.
- JEE/NEET and other exam-specific leaderboards.
- Per-test rank, Top 100, My Rank and percentile.
- Existing Full Test weight 1.0x and Part Test weight 0.2x policy, subject to validated test classification.
- XP, leagues, streaks and points as a separate gamification layer, not a substitute for official score/rank.
- Public/private profile visibility, banned-user exclusion and privacy-safe callable APIs.
- First qualifying attempt and duplicate-attempt protection.

### 1.4 Separation of score, rank and XP
Never combine these into one ambiguous field:
- Raw score: exam marking scheme result.
- Maximum score: maximum available for that exact paper.
- Normalized score: only official, reproducible normalization.
- Rank score/weight: ranking policy value, clearly labeled as platform ranking.
- XP: gamification points.
- Percentile: computed only from a defined and sufficiently complete comparison population.

## 2. Phase roadmap and exit gates

## Phase 0 — Repository baseline, ownership and branch safety
**Purpose:** Establish exactly what exists before changing code.

Tasks:
1. Re-read current branch HEAD, commit history, open PRs and workflow status.
2. Compare the three ranking/scoring branches and the integration branch.
3. Inventory all files related to exam registry, scoring, answer validation, ranking, leaderboard, XP, migration, Firestore rules/indexes, Android UI and tests.
4. Build a file ownership map: existing authoritative file, duplicate file, consumer, tests, and planned owner.
5. Identify overlapping/conflicting implementations and decide which one remains canonical.
6. Verify working tree and baseline build/test status.

Deliverables:
- Baseline commit SHA and branch map.
- File ownership/overlap matrix.
- Existing feature inventory.
- Known failures and CI baseline report.

Exit gate:
- No unknown active implementation path.
- No edits to `main`.
- No merge/deploy/data writes.
- All changes assigned to a feature branch.

## Phase 1 — Official exam research and evidence packets
**Purpose:** Replace assumptions with authoritative exam rules.

For each exam/paper/cycle:
1. Retrieve latest official notification, information bulletin, syllabus, corrigenda and official answer-key guidance.
2. Record source title, cycle/year, publication date, direct official URL, retrieval date and page/section references.
3. Extract stages, papers, subjects, sections, question counts, compulsory/optional rules, options, input types and duration.
4. Extract correct/wrong/unanswered/partial marks, negative marking, fractional penalties, rounding, bonus/dropped question and normalization rules.
5. Record language and accessibility constraints where relevant.
6. Independently review the evidence packet.
7. Mark each field VERIFIED, UNKNOWN or NOT_APPLICABLE. Unknown fields cannot be filled from coaching sites.

Deliverables:
- One source packet per distinct paper/stage/version.
- Human-readable scoring truth table.
- Official source coverage matrix for all 25 identities.
- Explicit list of unresolved/cycle-not-yet-published patterns.

Exit gate:
- Every configuration proposed for implementation has complete primary-source evidence.
- No unsupported rule remains.
- Exam config stays disabled until approval.

Priority batches:
- Batch A: JEE Advanced, CUET UG, UPSC CSE Prelims.
- Batch B: SSC CGL, CHSL, MTS, GD, CPO.
- Batch C: IBPS PO/Clerk and SBI PO/Clerk.
- Batch D: RRB NTPC, Group D, ALP, Technician, JE.
- Batch E: UGC NET, CTET, NDA, CDS.
- Batch F: CAT and GATE.
The batch order is a delivery recommendation, not permission to reuse another exam's pattern.

## Phase 2 — Universal data contract and registry
**Purpose:** Model all verified patterns without multiplying engines.

Tasks:
1. Define schema for exam identity, cycle, config version, stage, paper, subject, section and question type.
2. Add provenance fields: source URL/title/year, source pages, verifiedAt, reviewer and verification status.
3. Add explicit `verified`, `scoringEnabled` and `rankingEnabled` gates, default false.
4. Define immutable config versioning; do not edit historical versions in place.
5. Define aliases only as display/search aliases; exact scoring resolution must use canonical IDs.
6. Model multiple papers, discipline/post groups, sectional timers and locked sections.
7. Define compatibility strategy for existing TestEntity, QuestionEntity, Firestore documents and Room database.
8. Generate or validate Android/backend models from the same contract.

Deliverables:
- Schema specification.
- Registry implementation plan and file map.
- Versioning and backward-compatibility rules.
- Schema validation tests.

Exit gate:
- Duplicate IDs and ambiguous paper resolution are rejected.
- Unverified configs cannot score authoritatively or rank.
- Existing NEET/JEE test records still resolve to their original versions.

## Phase 3 — Question model, ingestion and answer-key integrity
**Purpose:** Ensure the engine receives trustworthy, correctly typed questions.

Tasks:
1. Map current QuestionEntity and backend question documents; do not create a parallel model without a migration case.
2. Define supported question types and type-specific answer representations.
3. Validate option count, answer-key cardinality, numerical format, matching pairs and multi-select rules.
4. Store source provenance and review status for imported questions.
5. Admin upload must bind a question to exact exam/version/stage/paper/subject/section.
6. Add PDF/OCR ingestion validation and manual review workflow; OCR output is not automatically trusted.
7. Preserve source PDF and question IDs; canonicalize IDs without collisions.
8. Tests with incomplete source data can be private practice only, never authoritative ranked tests.

Deliverables:
- Question contract.
- Upload/validation rules.
- Answer-key audit report.
- No-dummy-data verification.

Exit gate:
- Duplicate/missing question IDs, invalid answer keys and unsupported question types are rejected.
- No test is ranked with unreviewed or untrusted answer keys.

## Phase 4 — Universal scoring engine
**Purpose:** Implement one deterministic scoring engine for all verified exams.

Tasks:
1. Create a pure scoring core with no UI, database or network dependency.
2. Implement correct/wrong/unanswered and supported partial credit.
3. Implement fractional negative marking with exact decimal precision and documented rounding.
4. Implement MCQ single, MCQ multiple, numerical/NAT/TITA and other verified types.
5. Implement numeric exactness/tolerance and integer constraints from official sources.
6. Handle bonus/dropped questions and revised answer keys with auditable recalculation.
7. Separate raw, maximum, normalized and display score.
8. Add strict fail-closed validation for incomplete/mismatched configurations.
9. Implement backend authoritative scoring; Android may preview but cannot override final server score.
10. Add Android/backend parity fixtures.

Deliverables:
- Scoring core and typed contract.
- Per-exam scoring truth tables.
- Golden test fixtures sourced from official pattern examples.
- Score explanation/audit record.

Exit gate:
- Same answer payload yields identical Android/backend result.
- All verified pattern test cases pass.
- Invalid/unknown config never receives a guessed score.
- Historical scoring can be reproduced from stored config version.

## Phase 5 — Test lifecycle, timer and eligibility
**Purpose:** Ensure the right test receives the right score and ranking weight.

Tasks:
1. Bind each test to exact exam/version/stage/paper/sections.
2. Implement overall timer, sectional timer and section locks only when required.
3. Define auto-submit, pause/resume, offline behavior and server timestamp policy.
4. Validate expected question count and section structure.
5. Define authoritative Full Test eligibility; title text alone cannot qualify a test as Full.
6. Apply existing Full 1.0x and Part 0.2x ranking weight only after classification.
7. Prevent duplicate submissions, repeated qualifying attempts and score tampering.
8. Keep practice/unverified tests outside official-style leaderboard.
9. Handle network retry and idempotency safely.

Deliverables:
- Test lifecycle state machine.
- Eligibility matrix.
- Timer and submission tests.

Exit gate:
- A malformed or incomplete test cannot be labeled a qualifying Full Test.
- Duplicate/replayed submission cannot create duplicate rank/XP.
- Offline retry does not alter final score.

## Phase 6 — Ranking, percentile and gamification integration
**Purpose:** Connect verified scores to one secure ranking system.

Tasks:
1. Integrate canonical score snapshots with the existing universal ranking engine.
2. Define ranking population and tie-break rules for each board.
3. Define AIR vs platform rank labels; do not imply official AIR from app users only.
4. Implement exam-wise, class/cohort, per-test, Top 100 and My Rank boards.
5. Calculate percentile only for a defined population and documented tie policy.
6. Integrate XP, leagues, streaks and weekly/all-time views separately from score.
7. Verify banned-user exclusion and public/private identity visibility.
8. Keep raw leaderboard collections inaccessible to direct client reads if callable APIs are the approved privacy boundary.
9. Audit legacy Leaderboard collection and compatibility readers.
10. Make rank snapshot failures explicit as unavailable; never show stale/dummy rank as current.

Deliverables:
- Ranking data contract.
- Board definitions and tie-break specification.
- Privacy and legacy-collection audit.
- Ranking API contract and integration tests.

Exit gate:
- Rank is derived from canonical score snapshot only.
- No duplicate rank for repeat qualifying attempts.
- Private identities are not leaked.
- Percentile and AIR labels are not misleading.

## Phase 7 — Historical data, migrations and recovery
**Purpose:** Protect existing users and historical attempts.

Tasks:
1. Inventory Room schema versions, Firestore document versions and legacy leaderboard records.
2. Map old exam IDs, score fields, question IDs and missing metadata.
3. Preserve original score/config provenance; never silently overwrite historical raw score.
4. Recalculate only when source questions, answer key and exact config are trustworthy.
5. Mark unverifiable legacy attempts as historical/unverified, not fabricated.
6. Use resumable, idempotent, checkpointed migrations with concurrency protection.
7. Test batches above Firestore write limits and pagination boundaries.
8. Add dry-run, before/after counts, checksums, rollback/restore and audit logs.
9. Test Android Room migrations from every supported historical version; no destructive fallback for user data.
10. Validate backups and restore procedure before any production migration.

Deliverables:
- Migration matrix and dry-run report.
- Backup/restore evidence.
- Room and Firestore migration tests.
- Reconciliation report.

Exit gate:
- Zero unexplained record loss or duplication.
- Migration can resume safely after interruption.
- Unverifiable data is preserved and labeled, not guessed.
- Restore test succeeds.

## Phase 8 — Android UI, admin and user experience
**Purpose:** Make the system understandable and usable.

Tasks:
1. Integrate exam, stage, paper and subject selection into existing test builder without breaking existing flows.
2. Display correct timer and section navigation behavior.
3. Show score breakdown: correct, incorrect, unanswered, partial, penalty, raw score and maximum.
4. Clearly distinguish practice score, platform rank and official exam rank.
5. Add leaderboard filters: exam, class/cohort, test, time period and privacy-safe identity.
6. Add My Rank, Top 100, percentile and next-target/XP views.
7. Admin controls: verify config, activate scoring, activate ranking, archive cycle and publish test.
8. Add accessible empty/error/loading/offline states; do not show fake sample ranks.
9. Preserve current app features and visual conventions unless a specific change is approved.

Deliverables:
- UI flow map and screen-by-screen acceptance checklist.
- Admin activation workflow.
- Android UI tests and screenshots for review.

Exit gate:
- No navigation regressions.
- Unavailable rank is clearly shown as unavailable.
- Admin cannot activate unverified configuration accidentally.

## Phase 9 — Security, performance and reliability
**Purpose:** Harden the system before real users depend on it.

Tasks:
1. Review Firestore rules, callable Functions authentication and authorization.
2. Enforce server-side score/rank writes; validate ownership and submission state.
3. Audit private profile fields, public display fields and banned-user policy.
4. Validate rate limits, abuse protection, idempotency and replay resistance.
5. Review indexes and query costs for every leaderboard filter.
6. Load-test concurrent submissions, rank recomputation and large boards.
7. Test pagination, race conditions, stale snapshots and partial failures.
8. Review logs for PII/secrets and ensure operational observability.
9. Test app behavior during network loss, retries, clock changes and process death.
10. Complete dependency/security scan and secret handling review.

Deliverables:
- Security threat model.
- Rules/API/index review.
- Load/performance report.
- Privacy regression suite.

Exit gate:
- No critical/high unresolved security issue.
- No client-side authority over final scores or ranks.
- Concurrency and abuse tests pass.

## Phase 10 — Full QA and release candidate
**Purpose:** Run the complete test program after development is frozen.

Test layers:
1. Unit tests: scoring, registry, ranking, eligibility and migration.
2. Contract tests: Android/backend config and score parity.
3. Integration tests: Firebase emulator, callable APIs, rules and indexes.
4. Room migration tests across supported versions.
5. End-to-end tests: create test → attempt → submit → score → rank → privacy.
6. Negative tests: invalid config, missing questions, duplicate IDs, forged score, banned user, private profile, repeated submission.
7. Boundary tests: zero answers, all wrong, all correct, fractional penalty, partial marks, timer expiry, bonus/dropped question.
8. Load tests: concurrent candidates and large leaderboard pagination.
9. Regression tests: existing login, tests, bookmarks, uploads, notifications and other app features.
10. Clean install and upgrade install on supported Android versions.

Deliverables:
- Current-head CI run URLs and complete pass/fail summary.
- QA sign-off.
- Known limitations list.
- Release candidate build and checksum.

Exit gate:
- Current branch-head CI fully green.
- No ignored/skipped critical tests.
- All P0/P1 defects fixed.
- Independent code/security review completed.

## Phase 11 — Staged launch and production activation
**Purpose:** Launch gradually with rollback readiness.

Launch sequence:
1. **Internal/dev:** test environment only, synthetic fixtures clearly separated from production.
2. **Private pilot:** invited real testers, limited verified exams, monitoring enabled.
3. **Limited public beta:** enable only exams that passed all gates; unverified exams remain practice-only or hidden.
4. **Controlled production:** activate server scoring and selected leaderboards with explicit release approval.
5. **Expansion:** enable additional exams in batches after each exam-specific sign-off.
6. **General availability:** all intended exams enabled only after their own evidence and QA gates pass.

Pre-launch checklist:
- Approved release commit and tagged version.
- Production backup and tested restore.
- Rules, Functions and indexes reviewed and separately approved.
- Migration dry-run and reconciliation approved.
- Monitoring, alerts and incident owner assigned.
- Rollback commit/feature flags ready.
- User-facing explanation of platform rank vs official AIR.
- Support and correction process for answer-key changes.

Rollback triggers:
- Score mismatch between Android and backend.
- Unexpected historical data count changes.
- Unauthorized identity/score exposure.
- Duplicate or missing rank records.
- Abnormal scoring or leaderboard latency/error rate.
- Any verified exam config found to be wrong.

Rollback must disable the affected config/feature flag and stop new ranking writes safely; it must not delete attempts or rewrite historical records.

## Phase 12 — Operations and continuous exam-cycle updates
**Purpose:** Keep patterns correct as authorities change them.

Tasks:
1. Monitor official exam portals for new bulletins, corrigenda and answer-key notices.
2. Create a new config version for a new cycle; never mutate the old version in place.
3. Re-run scoring fixtures and Android/backend parity tests on every config update.
4. Publish a change log with affected tests and attempts.
5. Define how bonus/dropped questions and revised keys affect already submitted attempts.
6. Monitor score distributions, rank anomalies, failed submissions and leaderboard costs.
7. Run scheduled backup/restore drills and access reviews.
8. Deprecate old patterns only after historical attempt compatibility is proven.

Deliverables:
- Exam-cycle update SOP.
- Monitoring dashboard and alert thresholds.
- Incident and answer-key correction playbook.

Exit gate:
- Each active config has an owner, source packet, version, review date and automated test suite.

---

## 3. Per-exam delivery tracker

Initial tracker from the last audit; verify current repository and official source status before implementation.

| Exam | Batch | Source packet | Config | Scoring | Ranking | QA | Launch |
|---|---|---|---|---|---|---|---|
| NEET UG | Existing verified baseline | Revalidate cycle | Existing | Existing | Verify integration | Pending sign-off | Not approved |
| JEE Main P1 | Existing verified baseline | Revalidate cycle | Existing | Existing | Verify integration | Pending sign-off | Not approved |
| JEE Advanced | A | Pending | Pending | Pending | Disabled | Pending | Disabled |
| CUET UG | A | Pending | Pending | Pending | Disabled | Pending | Disabled |
| UPSC CSE Prelims | A | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SSC CGL | B | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SSC CHSL | B | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SSC MTS | B | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SSC GD | B | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SSC CPO | B | Pending | Pending | Pending | Disabled | Pending | Disabled |
| IBPS PO | C | Pending | Pending | Pending | Disabled | Pending | Disabled |
| IBPS Clerk/CSA | C | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SBI PO | C | Pending | Pending | Pending | Disabled | Pending | Disabled |
| SBI Clerk/JA | C | Pending | Pending | Pending | Disabled | Pending | Disabled |
| RRB NTPC | D | Pending | Pending | Pending | Disabled | Pending | Disabled |
| RRB Group D | D | Pending | Pending | Pending | Disabled | Pending | Disabled |
| RRB ALP | D | Pending | Pending | Pending | Disabled | Pending | Disabled |
| RRB Technician | D | Pending | Pending | Pending | Disabled | Pending | Disabled |
| RRB JE | D | Pending | Pending | Pending | Disabled | Pending | Disabled |
| UGC NET | E | Pending | Pending | Pending | Disabled | Pending | Disabled |
| CTET | E | Pending | Pending | Pending | Disabled | Pending | Disabled |
| NDA & NA | E | Pending | Pending | Pending | Disabled | Pending | Disabled |
| CDS | E | Pending | Pending | Pending | Disabled | Pending | Disabled |
| CAT | F | Pending | Pending | Pending | Disabled | Pending | Disabled |
| GATE | F | Pending | Pending | Pending | Disabled | Pending | Disabled |

**Important:** "Existing" means present in the prior audit's verified baseline, not independently re-certified by this roadmap. The current branch HEAD and latest official bulletin must be checked again.

## 4. Project-wide definition of done

The overall system is launch-ready only when:
- Every launch-enabled exam has its own approved official source packet.
- There is one authoritative registry and one universal scoring engine.
- Android/backend score parity is proven.
- Ranking derives from validated canonical scores and documented populations.
- Full/Part eligibility and 1.0x/0.2x policy are tested.
- Privacy and security boundaries are enforced server-side.
- Historical data and migrations are reconciled and restorable.
- Existing app features pass regression tests.
- Current branch-head CI and release QA are green.
- Monitoring, rollback and support procedures are ready.
- Explicit production release approval is recorded.

A single exam can launch while other exams remain disabled, provided it independently passes all gates. Do not delay a verified safe release just to display 25 exam names, and do not activate an unverified exam to make the dashboard look complete.

## 5. Immediate next actions

1. Refresh GitHub branch HEAD and current workflow status.
2. Produce the Phase 0 file ownership/overlap matrix.
3. Compare actual registry implementation with the 25-exam tracker and reconcile any missing/extra IDs.
4. Audit the existing NEET/JEE configs and score parity before treating them as release-ready.
5. Begin Batch A source packets, starting with JEE Advanced, CUET UG and UPSC CSE Prelims.
6. Do not begin broad code edits until Phase 0 and the first source packets are approved.

**This roadmap is a planning artifact. It does not claim the system is developed, tested, merged or launched.**
