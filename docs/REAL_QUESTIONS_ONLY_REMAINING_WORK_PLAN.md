# Real Questions Only — Branch Remaining Work Plan

**Branch:** `fix/phase1-real-questions-only`  
**Audited HEAD:** `c6110e5ca7c89c25c9dc5312666f367c976d3290`  
**Main HEAD:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** 192 commits ahead, 93 behind; merge base `d2feae47f908b705209c27b8e61e624f94c400d7`.

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

This branch is the hardening workstream for real, source-backed questions only. It must not generate a replacement dummy question when an API, PDF parser, OCR engine or validation step fails.

## Remaining work

### Q1. Remove and block every fake fallback
- [ ] Search all question creation/generation paths for hardcoded sample questions, placeholder options, random questions, canned AI responses, offline fallback generation and silent subject substitution.
- [ ] Trace Student, Admin, PDF upload, retry/recovery, difficulty regeneration, test creation and Firestore import/export call sites.
- [ ] Delete only confirmed dummy production paths after proving no required real workflow depends on them.
- [ ] Add tests proving provider/parser failure returns a visible failure and creates/publishes zero questions.
- [ ] Confirm no fallback from All Subjects to first subject or from missing exam configuration to NEET/default.

### Q2. Real PDF ingestion and extraction
- [ ] Verify native Gemini PDF client sends original bytes and correct MIME type; keep key in a header/request path, never URL or logs.
- [ ] Distinguish text PDF, scanned PDF, mixed PDF, image/diagram/equation/table pages.
- [ ] Use actual configured OCR for scanned pages; unavailable OCR must stop/review, not fabricate content.
- [ ] Preserve PDF page number, crop/image references, page ordering and source identity.
- [ ] Verify PDF session persistence, WorkManager retry/recovery, interrupted upload and cleanup.
- [ ] Apply MIME/signature, size/page limits, storage ownership and retention rules.

### Q3. Question and answer validation
- [ ] Require valid question stem, exact option set, unique IDs, supported question type and configured subject/section.
- [ ] Support Hindi, English and bilingual fields without dropping one language or silently translating technical terms incorrectly.
- [ ] Verify single-correct, multiple-correct, numerical/NAT/TITA, decimal precision, negative marks and five-option formats as configured.
- [ ] Keep question generation, answer-key extraction and independent answer verification separate.
- [ ] Conflicting, missing or low-confidence answers become CONFLICT/UNVERIFIED and stay in review.
- [ ] Validate source answer against actual PDF/source evidence; never trust model confidence alone.
- [ ] Deduplicate against existing repository/private vault using stable fingerprints and preserve source provenance.

### Q4. Privacy, Admin review and publication
- [ ] Keep new tests private/owner-only or Under Review until explicit approval.
- [ ] Keep answer keys/explanations in the protected private payload; public payload must not leak them.
- [ ] Verify owner/Admin/Super Admin authorization on server and Firestore/Storage rules.
- [ ] Test approve, reject, edit/correct, duplicate rejection, ownership and private/public transitions.
- [ ] Preserve private vault and account-switching isolation.

### Q5. Gemini key and connector safety
- [ ] Verify Student BYOK key is encrypted locally using Android Keystore and scoped to the authenticated user.
- [ ] Admin key remains server-side in Secret Manager; no client fallback or silent Admin billing.
- [ ] Test actual request construction with local test server and a separate live authorized provider test; do not conflate them.
- [ ] Handle 401/403/429/5xx, timeout, malformed JSON, empty output and quota errors without dummy fallback.

### Q6. Regression and evidence
- [ ] Reconcile current test inventory. The tracker records historical runs with 642 tests and later 321 tests on different commits; these are not current-head proof.
- [ ] Run full Android tests, backend Node tests, emulator rules tests and Debug APK build on current HEAD.
- [ ] Include PDF byte/header integration, real extraction fixtures, no-fallback tests, answer-key security and recovery.
- [ ] Run live Student BYOK + actual PDF + Admin review end-to-end separately; current historical HTTP test only verified request construction against a local test server.
- [ ] Record exact SHA, CI run, executed/skipped/failed counts, APK artifact and remaining external checks.

## Cross-branch dependencies
- Use the versioned PDF prompt/syllabus contract from `feature/universal-pdf-prompt-contract-v1` after review.
- Use the Student/Admin connector architecture from `feature/universal-gemini-ai-10-phase-2026-11-08`; do not create a duplicate key-management system.
- Review main's newer fail-closed subject routing and deterministic question planner before porting. No blind merge.

## Status
This plan is based on the branch tracker and chat decisions. Historical run 42459562922 passed its tested commit, but the current audited HEAD is later and has not been verified by that result. No current-head test was run during this audit.

### Evidence log
| Date | Tested SHA | Evidence | Result |
|---|---|---|---|
| 2026-11-08 | `71d422d2614d493bd9bbc54721d26107a78e326f` | Historical run 42459562922 | Historical pass on that SHA only; not current HEAD |
| 2026-11-12 | `c6110e5ca7c89c25c9dc5312666f367c976d3290` | Git comparison and tracker review | Audit only; tests not run |
