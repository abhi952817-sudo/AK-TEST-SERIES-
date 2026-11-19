# Main Commit Re-Review — 2026-11-19

## Reviewed commit

- Branch: `main`
- Commit: `724defaa487bfc64863402992474b1044198ae98`
- Parent: `ae58ed7162a195a41dfd6f3d523a4bb37658ef83`
- Message: `refactor: improve security and AI provider management`
- Review type: GitHub source/static review. Android build was still running at review time; no local device or Firebase Emulator execution.

## Files changed

- Added `app/src/main/java/Com/ak/testseries/ai/AiProviderModels.kt`
- Added `app/src/main/java/Com/ak/testseries/ai/SharedAiProviderRegistry.kt`
- Added `app/src/main/java/Com/ak/testseries/ai/FirebaseAiService.kt`
- Modified `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`
- Modified `backend_functions/examScoring.js`
- Added two large conversation/prompt text artifacts under `public/assets/aistudio/`

## Positive changes observed

1. Community share payload construction in NEETViewModel no longer copies `correctAnswer` or `explanation` into the public shared question payload at the two reviewed code locations (F-023 mitigation).
2. Backend numerical comparison replaces permissive `parseFloat` with a strict decimal parser that rejects trailing non-numeric characters and non-finite values.
3. A metadata model for five shared provider slots was added.

## Findings requiring follow-up

### M-001 — Connector registry Firestore write is blocked by current rules
- File: `app/src/main/java/Com/ak/testseries/ai/SharedAiProviderRegistry.kt`
- Evidence: `syncProviderRegistryMetadata(context)` writes to `ConnectorMetadata/ProviderRegistry/Providers/{C-01..C-05}`.
- File: `firestore.rules`
- Evidence: no explicit `ConnectorMetadata` match was found; the recursive catch-all rule explicitly denies read/write.
- Impact: the metadata sync operation will be denied by Firestore Security Rules for clients, including admins, unless a narrowly scoped rule or trusted server-side write path is added.
- Severity: Medium (feature correctness; not a credential leak).
- Status: Static evidence confirmed; runtime emulator test not run.
- Required fix: decide whether this metadata needs Firestore at all. Prefer local registry metadata or a server-managed write path; if Firestore is required, add strict schema/field validation and role restrictions with rules tests. Do not simply open the whole collection.

### M-002 — New Firebase AI service bypasses the shared five-provider registry
- File: `app/src/main/java/Com/ak/testseries/ai/FirebaseAiService.kt`
- Evidence: service hardcodes a Firebase AI Gemini model and exposes only Gemini text/JSON/PDF calls. It does not dispatch through `SharedAiProviderRegistry` or implement Groq, Mistral, Cloudflare and Hugging Face adapters.
- Impact: this is a Gemini helper, not yet the promised Universal AI Router or five-provider connector implementation. Existing AI call sites may still use parallel pathways.
- Severity: High for architecture completeness; not a security vulnerability by itself.
- Status: Static evidence confirmed.
- Required fix: inventory all existing AI call sites and consolidate behind one adapter/router contract before claiming five providers are integrated.

### M-003 — Registry metadata publishes internal admin secret identifiers
- File: `app/src/main/java/Com/ak/testseries/ai/SharedAiProviderRegistry.kt`
- Evidence: sync payload includes `adminSecretName` for every provider.
- Impact: these names are not API keys, but exposing server secret resource identifiers to client-readable metadata is unnecessary and increases information disclosure.
- Severity: Low.
- Status: Static evidence confirmed.
- Required fix: remove `adminSecretName` from client-published metadata; keep server secret mapping exclusively in backend configuration.

### M-004 — Conversation transcripts added under public web assets
- Paths:
  - `public/assets/aistudio/Paste_November_17__2026_-_10_11PM_ad411a6d`
  - `public/assets/aistudio/Paste_November_17__2026_-_10_22PM_d190c531`
- Evidence: commit adds approximately 2,502 lines of conversation/prompt text, not executable application code, under the public static asset directory.
- Impact: these files may be served publicly with the web app and add unnecessary content to the public distribution. No API secret was observed in the sampled text, but full secret scanning is still required.
- Severity: Medium (public artifact hygiene/privacy review).
- Status: File type and content purpose confirmed; full secret scan not run.
- Required fix: move approved project documentation to a private/non-public docs location or remove the artifacts after preserving required decisions in the audit docs. Do not delete until provenance and intended use are confirmed.

### M-005 — Strict numerical parser lacks targeted regression tests in this commit
- File: `backend_functions/examScoring.js`
- Evidence: strict parser was added, but `backend_functions/examScoring.test.js` was not changed in this commit.
- Impact: malformed strings such as `12abc`, `1.2.3`, `Infinity`, `NaN`, blank input, signed decimals and scientific notation are not explicitly covered by new tests.
- Severity: Medium.
- Status: Static evidence confirmed.
- Required fix: add table-driven tests for accepted/rejected numeric formats and scoring parity.

### M-006 — Previously identified answer-key response issue remains unverified
- File: `backend_functions/index.js`, prior F-016 location.
- This commit did not modify `backend_functions/index.js`; therefore it does not demonstrate that `solutionKeys` are no longer returned by ordinary attempt submission.
- Severity: Critical until current callable response is re-reviewed.
- Status: NOT RECHECKED in this commit review; do not mark fixed.

### M-007 — Admin/Student credential storage and routing are not implemented by these additions
- Files: new AI registry/service.
- Evidence: new files define provider metadata and Gemini Firebase AI calls, but no Android Keystore/AES-GCM credential repository, no server Secret Manager adapter, and no provider-specific authenticated HTTP adapters are present in these three files.
- Impact: commit message overstates completion if interpreted as a complete credential lifecycle or five-provider runtime integration. Other files/call sites may exist; repository-wide inventory is still required.
- Severity: High for completeness.
- Status: Static review of added files only; repository-wide search pending.

## CI status at review time

- Android CI run `44528426052` for this main commit was `in_progress` at the time of inspection.
- Previous main Android CI for parent commit `ae58ed7` succeeded.
- No claim is made that the new commit passes Android CI until the run concludes.

## Integration decision

- Do not cherry-pick this commit wholesale into the integration branch yet; it is already on main.
- Treat the F-023 and strict numeric parser changes as present on main, subject to tests.
- Keep M-001 through M-007 in the pending remediation list.
- Do not delete either public text artifact until it is confirmed unnecessary and its information is safely preserved.


## Re-review update — 2026-11-19 (after main CI completed)

### Verified current main
- Current main SHA: `724defaa487bfc64863402992474b1044198ae98`.
- Android CI run `44528426052` completed with conclusion `success`: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/44528426052
- This is CI evidence for that exact SHA only; it is not Firebase Emulator, live API, device QA, or production verification.

### M-006 / F-016 — ordinary submission response rechecked
- Current `backend_functions/index.js`: `submitTestAttemptServerSide` builds `solutionKeys` internally for scoring, but both the idempotent-existing-attempt response and the new-attempt transaction response return score/rank/count fields only; neither returns `solutionKeys`.
- A separate `getTestAttemptSolutions` callable exists and returns solution keys after its own authenticated attempt/review checks.
- Status: **RESOLVED by current static source review for the ordinary submit response**. Runtime authorization tests for the dedicated review callable remain required; do not infer production behavior from static review alone.

### Other findings status
- M-001: Still pending. Registry writes to `ConnectorMetadata/ProviderRegistry/Providers`; current rules have no explicit allow rule for this path. Prefer local static registry or trusted server path; do not broaden rules.
- M-002: Still pending. `FirebaseAiService` is Gemini-only and does not dispatch through the five-provider registry.
- M-003: Still pending. Client-published metadata includes `adminSecretName`; remove this server-only identifier from client payload.
- M-004: Still pending. Public AI Studio transcript artifacts remain under `public/assets`; provenance and complete secret scan are outstanding. Do not delete before preservation/review.
- M-005: Still pending. Existing scoring tests cover many scoring cases but do not add focused parser-format regression coverage for malformed numeric strings and accepted decimal formats.
- M-007: Still pending repository-wide. New AI files do not prove the promised Keystore/AES-GCM student credential path, Secret Manager server adapter, or five real provider transports. Inventory all call sites before declaring absent or complete.
- F-009: The reviewed current ViewModel explicitly forces the offline mock fallback flag to false and deprecates the setter; no `generateMockAiTest` function was found in the reviewed current file. Treat the old mock-fallback finding as **mitigated in reviewed path**, while continuing to verify all AI/PDF failure paths return errors and publish zero questions.
- F-023: The reviewed current Community public-copy path strips `correctAnswer` and `explanation`; retain as a positive static mitigation, with rules/backend tests still needed.

### Current next action
Continue the branch-by-branch Markdown and implementation inventory. Do not merge feature branches or change main code until the relevant branch docs, unique code deltas, call sites and tests have been reviewed.


### M-001 clarification — registry sync has no external call site
- GitHub code search at current main SHA finds `syncProviderRegistryMetadata` only in its declaration inside `SharedAiProviderRegistry.kt`; no caller was found.
- Therefore the Firestore rules mismatch is a **dormant integration defect**, not evidence that an active app flow is currently failing.
- Keep the method disabled/unreferenced until a decision is made. Preferred options remain local static metadata or a trusted server-managed path. Do not add broad Firestore permissions merely to activate it.

### M-002 clarification — Gemini helper has limited current call sites
- Current code search finds `FirebaseAiService` references in `UniversalPdfPipeline.kt` and `GeminiPdfExtractor.kt` in addition to its own declaration.
- This establishes limited Gemini-service adoption, not a five-provider router. No dispatch across Groq, Mistral, Cloudflare and Hugging Face was found in the reviewed registry/service additions.


## Small remediation executed — F-004 numerical equivalence edge case (2026-11-19)

- New fix branch: `fix/strict-numerical-equivalence-2026-11-19`.
- Base: current main `724defaa487bfc64863402992474b1044198ae98`.
- Commits: scoring fix `d534d590fe58b7c8e3dad86809730730f6e4b3c3`; regression tests `0cf3a3e4339573d358dc9c6e11986f80369ab4db`.
- PR #15: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/15 (open, draft, targets main).
- Root cause: numerical comparison checked exact string equality before strict numeric parsing, so identical invalid inputs such as `NaN`, `Infinity`, or `12abc` could compare as correct.
- Change: for numerical questions, parse both operands first and require finite strict-decimal values before comparing; preserve ordinary option/index matching for non-numerical questions.
- Added regression cases: valid signed decimals, malformed strings, non-finite strings, blanks and scientific notation (currently rejected by the strict decimal contract).
- Local focused Node verification of the exact patched comparison logic: **11/11 cases passed**.
- GitHub Android CI for PR head: run `44530440435`, still in progress at this update. The repository currently has no dedicated Node/Cloud Functions test workflow in `.github/workflows`; Android CI alone does not prove `examScoring.test.js` was executed.
- Merge gate: inspect final PR checks and, before merge, ensure the full backend test file is executed in a Node 18 environment. Do not mark merged until checks prove it.
- No main code, Firebase deployment, production data, or other branches changed by this remediation.


## Follow-on small fixes opened — 2026-11-19

### PR #15 — F-004 numerical comparison
- Branch: `fix/strict-numerical-equivalence-2026-11-19`.
- Latest head: `764078eef2da6a51e707c692adc75fb0e745b028`.
- Added a CI step to execute `node --test backend_functions/examScoring.test.js`; prior Android workflow only syntax-checked the scoring source and did not execute this test file.
- Run `44530788784` for the latest head was in progress at last check. Do not merge until it completes successfully and the backend scoring test step passes.
- PR: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/15

### PR #16 — M-001/M-003 client metadata cleanup
- Branch: `fix/remove-client-ai-secret-metadata-2026-11-19`.
- Head: `9293f723c088f4a575fbacf6ad5aca8f47d037ec`.
- Removed the unused Android Firestore metadata sync function (no caller found) and removed Admin Secret Manager resource-name identifiers from the Android provider model.
- No Firestore rules were broadened; no API keys or server secrets changed.
- Android CI run `44530905224` was queued at last check. Merge only after successful build/test.
- PR: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/16

### Merge policy
Both are intentionally separate draft PRs against main. Neither has been merged. The current main remains unchanged. Continue with other small, independently reviewable fixes while CI runs; defer larger router, Firestore cutover, and provider adapter work to dedicated implementation plans.
