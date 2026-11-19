# Branch Handoff — Community Branch Family

**Status:** OVERLAPPING / BLOCKED FOR WHOLE-BRANCH MERGE  
**Reviewed against Main:** 2026-11-19  
**Main HEAD at review:** `213d1c7aa547159ad8ec1ffa8c78b74a3f5732b0`

## Branch inventory and overlap

| Branch | HEAD | Ahead / behind Main | Changed files | Decision |
|---|---|---:|---:|---|
| `feature/community-completion-2026-11-13` | `a3bcc1ae6658e19872b63eb5bc416ce2aa8919f9` | 184 / 26 | 22 | Keep as feature source; selective port only |
| `feature/community-development-plan-2026-11-13` | `f9b8c2fe1102be3d3a703c7dfc25bcd83f10be11` | 33 / 29 | 9 | Requirements/reference; compare before port |
| `feature/community-mvp-completion-2026-11-13` | `c2a0fd5d682d927c28362ef9107eefb25e329458` | 64 / 26 | 14 | Selective port only |
| `fix/community-ci-recovery-2026-11-13` | `944b5f79808d467eac3cec7e524786a291d84b70` | 75 / 26 | 17 | CI/test reference; no whole merge |
| `integration/community-firestore-default-2026-11-15` | `ba45c04bbee78b7a11b72ef146f7707d8bc7b5ea` | 207 / 26 | 36 | Integration snapshot only; DB mismatch gate |

These branches overlap in Community UI, moderation/admin UI, ViewModel, backend functions, rules, Storage and CI. Merging all of them would duplicate or conflict on the same user-facing flows and server contracts.

## Main is the canonical base

- Preserve Main's selected Firestore database ID: `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf`.
- Do not port the integration branch's Firestore-default changes unchanged.
- Main already contains the Community rules workflow and UID-scoped Storage evidence paths/tests recorded in the current reconciliation audit; verify current exact files before modifying them.
- Main's public-question sanitizer now fails closed after PR #17 (`422f5849fc39de469ecc3f597e243e6142f5271c)). Do not reintroduce a raw-payload fallback.

## Required canonical Community contract

1. A trusted server callable must validate ownership and eligibility, canonical exam/stage/paper, question count, duration, subject distribution, question schema and image references before publication. Client-only validation is not a security boundary.
2. Public shared question documents contain question text/options only. Correct answers and explanations remain in a private, access-controlled answer-key store. Any parse/sanitization failure blocks publication.
3. Publish success is reported only after the canonical Firestore write succeeds. Local Room save success, notification success or image upload success must not be shown as Community publish success.
4. Duplicate sharing must be idempotent and ownership-bound; deterministic IDs alone do not replace server authorization.
5. Moderation, reports, bans, profile visibility and private content must be checked against current Main rules and backend authorization.
6. Image paths/hosts and Storage access must be UID-scoped and validated server-side where applicable.

## Selective port order

- First map Main's current publish call graph and UI result states; determine whether swallowed upload errors can cause false success.
- Compare the pure validation helpers/tests from `fix/community-ci-recovery-2026-11-13` and `feature/community-completion-2026-11-13` against Main's existing sanitizer and callable contracts. Port only helpers whose API and rules align.
- Review MVP subject registry and UI components separately; do not replace Main's current navigation or ViewModel wholesale.
- Add callable ownership/metadata validation and tests before enabling any client entry point.
- Add success/failure UI state and tests; keep local save and cloud publish outcomes distinct.
- Run exact-head backend, Firestore rules, Storage rules and Android CI after each small port.

## Do not do

- Do not merge all five branches or chain their PRs.
- Do not replace Main's named Firestore database with `(default)` or another branch's database ID.
- Do not relax Firestore/Storage rules to make a branch compile.
- Do not publish answer keys, accept untrusted client metadata as authoritative, or claim successful publication after a caught exception.
- Do not delete source branches; retain them for reference.

## Completion checklist

- [ ] Trusted callable validates owner and all publish metadata.
- [ ] Public/private question split is fail-closed.
- [ ] UI distinguishes local save, cloud publish and notification outcomes.
- [ ] Duplicate share and retry tests pass.
- [ ] Moderation, profile privacy and report authorization tests pass.
- [ ] Firestore and Storage emulator suites pass on exact SHA.
- [ ] Canonical named database ID verified in Android and backend.
- [ ] One canonical Community flow remains; duplicate branch implementations are not merged.

## Main call-graph evidence added 2026-11-19

Main source file `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt` was re-read at blob `1e2620e9bfb80fc600191ceda3020b4f10b26406`.

- `uploadTestToFirestore` is a private suspend function at approximately line 5724 and returns `Unit`.
- It catches image-upload errors and returns normally; its outer Firestore/sanitization catch logs/prints the exception and also returns normally.
- Five call sites were found (approximately lines 1526, 1687, 5724 is definition, 6035 and 6318; actual call sites: 1526, 1687, 6035, 6318, plus source line 5724 is the definition). Each caller cannot distinguish a successful cloud write from a swallowed failure through the return value.
- In `saveGeneratedTest`, the public-test path calls this helper and then separately attempts a LiveTests write. The LiveTests block has its own catch and can also fail independently. Local Room insertion occurs before cloud publication.
- Therefore the confirmed defect is **loss of publication outcome at the helper contract**. A user-visible false-success message has not yet been proven from this call graph alone; inspect the generation UI/state consumers before claiming that specific symptom.

**Recommended next change:** trace every caller's UI/state contract, then change the helper to return an explicit result (e.g. Published / SkippedPrivate / Failed with safe message) and make each caller report local-save and cloud-publish outcomes separately. Include the separate LiveTests result in the generator flow. This likely crosses more than a simple isolated one-file fix; do not patch only the helper and leave callers ignoring the result. Add unit/instrumentation coverage for image failure, sanitizer failure, Firestore write failure, private local-only save and LiveTests partial failure. No rules changes are needed for this outcome-reporting correction.


## Refreshed publication call-site and UI-state audit — 2026-11-19

Re-read Main `NEETViewModel.kt` at blob `1e2620e9bfb80fc600191ceda3020b4f10b26406`. Four active call sites were inspected:

1. Around line 1530: an admin test creation path inserts the test and questions into Room, then calls `uploadTestToFirestore`; no publication result is returned to the caller.
2. Around line 1680: scheduled Live Test creation inserts Room records, calls the helper, and the enclosing coroutine only catches/prints exceptions. The helper swallows its own upload errors.
3. Around line 6035: `saveGeneratedTest` inserts the local test and questions, updates preview state, then calls the helper for public tests. For Live Tests it separately writes `LiveTests`, then private answer keys; that block has its own catch. Notification broadcast has another independent catch. The method proceeds to folder placement and optional Community auto-share even if an earlier cloud write failed. Therefore Tests_Data publication, LiveTests publication, private-key write, notification and Community share are distinct outcomes and must not be represented as one generic “saved/published” result.
4. Around line 6318: another admin creation path inserts local records and calls the helper with no observable publication result.

The helper itself (around line 5724) returns `Unit`; private tests return normally by design, image-upload failure returns normally, and the outer catch prints/logs errors and returns normally. Public sanitization remains fail-closed after PR #17, but failure is not propagated to these callers.

### UI/state evidence and limits

The inspected generator flow updates `previewQuestions` after Room insertion and proceeds with local folder placement. In the inspected range there is no explicit state result from `uploadTestToFirestore` to gate a “published” UI state. This proves missing outcome propagation, but does not by itself prove the exact text of any user-visible success toast/message. Search and trace all UI collectors/callbacks before claiming a specific false-success notification.

### Required correction and acceptance tests

- Return a typed outcome from the helper, distinguishing at least PRIVATE_LOCAL_ONLY, PUBLISHED, IMAGE_UPLOAD_FAILED, SANITIZATION_FAILED and FIRESTORE_WRITE_FAILED; avoid leaking internal exception details to students.
- For generator Live Tests, separately track Tests_Data, LiveTests document, private answer-key write, notification and Community-share outcomes. Do not report overall Live Test publication as successful unless all required durable writes succeed; notification may be a separately reported best-effort side effect.
- Preserve local Room save even if cloud publication fails, but display “saved locally / cloud publish failed” rather than implying cloud publication.
- Ensure callers either surface the result or explicitly record a retryable state. Avoid automatic duplicate writes on retry; use stable IDs/idempotent writes.
- Tests: private local-only; image upload failure; fail-closed sanitizer exception; Tests_Data write failure; LiveTests write failure after Tests_Data succeeds; private answer-key write failure; notification failure; Community share failure; retry/idempotency; local Room save remains intact.

**Scope decision:** this spans the helper, four call sites, generator UI/state, and tests. It exceeds the 2–4-file small-fix boundary. No source code was changed; implement only after the full UI state/callback call graph and retry semantics are mapped. No Firestore rules relaxation or database-ID change is required or permitted for this outcome-propagation work.
