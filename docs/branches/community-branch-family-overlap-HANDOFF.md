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
