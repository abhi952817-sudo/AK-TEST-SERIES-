# Restore Files / Environment Ignore Safety — Branch Plan

**Branch:** `restore-files/removed-env-gitignore-2026-10-19`  
**Audited HEAD:** `4c600633448696ee6aa34b9c7e0b73e8b027b637`  
**Main HEAD:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** branch is 128 commits behind main and has no unique commits ahead of main.

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

This is a historical recovery branch associated with restoring environment and ignore-file safety. It is stale relative to main. Do not use it as an application development base.

## Remaining work

### E1. Verify secret-file protection
- [ ] Compare this branch's `.gitignore` with main and current feature branches.
- [ ] Confirm `.env`, local.properties, keystores, service-account JSON, Firebase credentials, API keys, signing files and debug logs are ignored.
- [ ] Keep only safe placeholder values in `.env.example`; never place a real Gemini/Firebase key there.
- [ ] Confirm root `.gitignore` and `assets/.aistudio/.gitignore` have distinct intended behavior and do not accidentally expose or hide required assets.
- [ ] Scan Git history for accidentally committed secrets. If a secret was exposed, rotate it; deleting a file from the latest commit does not revoke it.

### E2. Restore-file verification
- [ ] Identify the exact file(s) that this branch was intended to restore and the original removal commit.
- [ ] Compare file contents and history; do not restore obsolete code merely because it existed earlier.
- [ ] Confirm Gradle wrapper files remain tracked and executable.
- [ ] Run secret-scanning and repository hygiene checks.

### E3. Branch disposition
- [ ] No application features should be developed here.
- [ ] If no unique required fix remains after audit, mark this branch as archival candidate.
- [ ] Do not delete the branch without owner approval.
- [ ] Do not merge this stale branch into main; it contains no unique ahead commits and is 128 commits behind.

## Completion criteria
Secret/ignore policy is verified, no real secrets are tracked, required wrapper files are preserved, and the owner approves archival or retention. This is a documentation and safety plan, not a request to merge the old branch.

### Audit result
The branch's `.gitignore` currently ignores `.env`, `local.properties`, debug keystore and debug logs. This alone does not prove the full repository/history is secret-free. No secret scan was run in this audit.

## 2026-11-12 Cross-branch code reconciliation (X/Y/Z)

### X — KEEP
- Keep historical evidence that this branch was created for environment/ignore-file recovery.

### Y — PORT / VERIFY
- No application implementation approved for porting.
- Verify main ignore rules, Gradle wrapper and repository secret history with a secret scanner. Rotate any exposed credential; ignore rules do not prove clean history.

### Z — OBSOLETE
- OBSOLETE AS A DEVELOPMENT BASE: 128 commits behind main and no unique ahead commits.
- Do not merge/cherry-pick old application files. Do not delete branch without owner approval.

### PASS / FAIL / OPEN evidence (2026-11-12)
| Check | Result | Evidence / action |
|---|---|---|
| Ahead/behind comparison | PASS | 0 unique ahead / 128 behind main in recorded audit. |
| Current .gitignore entries | PASS (limited) | .env, local.properties, debug keystore and debug logs ignored. |
| Full history secret scan | OPEN | Not run; do not claim secret-free. |
| Restore-file necessity | OPEN | Intended restore and original removal commit need verification. |
| Application port/merge | N/A | No application code should be taken from this branch. |

### Ordered disposition
1. Run repository/history secret scan; rotate exposed credentials.
2. Verify wrapper and ignore rules on main.
3. Retain branch for audit until owner approves deletion.

**Verdict:** OBSOLETE DEVELOPMENT BASE; RETAIN FOR AUDIT. No tests or secret scan were run by this documentation update.
