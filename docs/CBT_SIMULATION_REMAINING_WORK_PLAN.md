# CBT Simulation & Bilingual Exam Experience — Branch Remaining Work Plan

**Branch:** `usi`  
**Audited HEAD:** `53b1e4c4b2f1ed9128751d9a57f3e932db118bca`  
**Main HEAD:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** 2 commits ahead and 128 behind main; merge base `4c600633448696ee6aa34b9c7e0b73e8b027b637`.

# Branch-specific remaining-work plan

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Audit date:** 2026-11-12  
**Status:** Audit-derived execution plan. This document does not claim that listed work or tests are complete.

## Mandatory rules

- Work only on this branch for its assigned scope. Do not merge or cherry-pick automatically.
- Keep `main`, production Firebase, live Firestore/Storage data, rules and indexes unchanged unless the owner separately approves a specific deployment.
- Preserve working screens, data contracts, attempts, test history, user data and existing functionality.
- No dummy/fabricated questions, answers, syllabus data, exam patterns, scores, rank, API success or usage/cost records.
- No silent offline generation or silent provider fallback. Fail closed with a clear error and review state.
- Keep API secrets out of source, APK, logs, crash reports and client-readable Firestore.
- Every implementation phase requires focused tests, regression tests, exact commit SHA and CI evidence. Never call an old branch's CI pass a pass for the current head.
- Before integrating work from another branch, compare source, call sites, migrations, security rules and tests file by file.
- Do not delete a branch or legacy data as part of cleanup without owner approval, backup and rollback plan.

## Audit snapshot

Branch HEAD and main divergence were obtained from GitHub branch comparison on 2026-11-12. Commit counts describe Git ancestry, not completed features. The branch is intentionally kept separate from main.

## Execution checklist

- [ ] Re-read this plan and inspect the current branch HEAD before each session.
- [ ] Confirm source files and call sites; distinguish implemented, partially implemented, missing and unverified.
- [ ] Fix the first blocking issue in this branch's scope without broad unrelated rewrites.
- [ ] Add regression coverage for both valid and invalid cases.
- [ ] Run focused tests, full relevant tests, security/emulator tests and build checks.
- [ ] Record commit SHA, commands, discovered/executed/skipped/failed test counts, CI run links and unresolved issues.
- [ ] Update this plan before moving to the next phase.

## Final release gate

No phase is complete until the current branch-head code, tests, security boundaries, data compatibility and user flow are verified. A successful compile alone is not enough. No production deployment or main merge is authorized by this plan.


## Branch purpose

This branch contains a historical CBT simulation implementation plan and a small environment/AI Studio ignore change. The CBT document is a product specification, not proof that every described UI behavior is implemented.

## Remaining work

### C1. CBT player audit
- [ ] Inspect current `ExamSimulationScreen.kt`, ViewModel state and test model before changing UI.
- [ ] Verify full-screen exam flow, test title/exam identity, timer accuracy, pause/resume policy and safe app/network interruption recovery.
- [ ] Verify section tabs and exact exam-specific section rules.
- [ ] Verify question palette states: Answered, Not Answered, Not Visited, Marked for Review, Answered & Marked for Review.
- [ ] Ensure status counts update immediately and navigation jumps to the correct question.
- [ ] Verify Save & Next, Save & Mark, Mark & Next, Clear Response, Previous and Next semantics.
- [ ] Ensure submission summary counts and server-side submission are consistent; retry must not create duplicate attempts.

### C2. Bilingual question experience
- [ ] Global English/Hindi switch and per-question language toggle must use stored translations, not automatic unreviewed translation.
- [ ] Preserve formulas, diagrams, units, scientific notation and option ordering across languages.
- [ ] If one language is missing, clearly indicate unavailable translation; do not fabricate.
- [ ] Persist user language preference and test account switching.

### C3. Exam-specific tools and accessibility
- [ ] Font zoom A-/A/A+ without breaking LaTeX or layout.
- [ ] Calculator appears only for exams that permit it; scientific/basic mode must be exam-configured.
- [ ] Support screen sizes, landscape/portrait, accessibility labels, touch targets and Hindi fonts.
- [ ] Do not force NEET Section B limits onto other exams; use the canonical exam configuration.

### C4. Scoring and review integration
- [ ] Use canonical `ExamScoringEngine` policy for positive/negative marks, optional sections, multiple-correct, numerical and decimal answers.
- [ ] Verify timer, answer palette, submission payload and scoring agree on attempted/unanswered state.
- [ ] Result and review must respect protected answer-key policy.
- [ ] Rank/percentile display must use server-authoritative values; never display a predicted value as official AIR.

### C5. UI regression and current-base compatibility
- [ ] Reconcile this stale branch with current main and feature branches before porting. Do not merge 128 main commits wholesale.
- [ ] Preserve existing app navigation and UI; do not create duplicate CBT/player screens.
- [ ] Verify schema compatibility, backup/restore, offline answer draft behavior and account-switching isolation.
- [ ] Add Compose/UI tests for palette states, bilingual toggle, section limits, timer, submission and recovery.
- [ ] Run Android unit/UI tests and Debug APK build on the exact integration branch after reviewed porting.

## Important branch disposition

The unique CBT plan is stored under `.aistudio/artifacts/brain/.../implementation_plan.md`; that is an AI Studio artifact and not a stable user-facing project plan. This new `docs/CBT_SIMULATION_REMAINING_WORK_PLAN.md` is the canonical branch checklist. The branch is 128 commits behind main, so it should not be used as the integration base. Port only reviewed CBT requirements to the current active branch after tests.

### Evidence log
| Date | SHA | Result |
|---|---|---|
| 2026-11-12 | `53b1e4c4b2f1ed9128751d9a57f3e932db118bca` | Branch comparison and historical CBT plan review only; no code/tests run |

## 2026-11-12 Cross-branch code reconciliation (X/Y/Z)

### X — KEEP
- Keep CBT requirements: timer/recovery, palette states, Save & Next/Mark flows, bilingual display, accessibility and exam-specific sections.
- Keep this branch as historical reference only; it is 128 commits behind main.

### Y — PORT / REBUILD ON CURRENT BASE
- Port only missing UX behavior after inspecting current main CBT player and ViewModel.
- Use canonical scoring, exam registry, server-authoritative submission and protected answer keys.
- Preserve offline answer drafts only; offline question generation is prohibited.

### Z — OBSOLETE / DO NOT PORT
- Do not merge stale branch wholesale, restore duplicate screens/navigation or hardcoded NEET section limits.
- AI Studio artifact is historical plan, not canonical implementation source.

### PASS / FAIL / OPEN evidence (2026-11-12)
| Check | Result | Evidence / action |
|---|---|---|
| Branch comparison | PASS | 2 unique commits ahead / 128 behind main in recorded audit. |
| CBT runtime behavior | OPEN | Requirements exist; runtime not proven by UI tests. |
| Current-base compatibility | OPEN | Current player/navigation/ViewModel comparison required. |
| Scoring/submission/recovery | OPEN | Canonical scoring and duplicate-submit tests required. |

### Ordered work
1. Inspect current main CBT player and list missing behaviors.
2. Port one behavior at a time without duplicate screens/navigation.
3. Add Compose tests for palette, timer, bilingual, section rules, recovery and submit idempotency.
4. Run UI/unit tests and Debug APK on integration branch.

**Verdict:** REQUIREMENTS REFERENCE / SELECTIVE PORT. No UI tests run by this documentation update.
