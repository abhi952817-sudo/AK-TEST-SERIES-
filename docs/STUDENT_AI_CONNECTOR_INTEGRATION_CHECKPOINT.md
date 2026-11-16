# Student AI Connector — Integration Checkpoint

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Integration branch:** `feature/universal-gemini-ai-10-phase-2026-11-08`  
**Student source branch:** `feature/student-universal-ai-connectors-v1` (latest reviewed Student core checkpoint: `9c710af904dea246cbe79b28b30443fc05996212`)  
**Protected branch:** `main`  
**Checkpoint date:** 2026-11-16

## 2026-11-16 integration audit

### Universal module status
- Confirmed integrated: Student generation uses `NEETViewModel.generateAiForQuestions()` -> `StudentAIProviderClient.generatePreferred()`; Admin generation uses the server-side callable. Prompt construction is shared through `UniversalAIContext`.
- Confirmed universal question-generation path: `NEETViewModel` builds exam-specific prompts with `UniversalAIContext.buildDynamicGenerationPrompt()` and validates responses with `GeminiResponseValidator`. PDF exam recognition also uses the registered `UniversalExamRegistry`.
- Student providers implemented in the connector: Gemini, OpenAI, Groq, Anthropic Claude, Cerebras. Student credentials stay on-device; Admin connector remains separate and server-side.
- The registered exam catalogue contains 20+ exam configurations, but this does **not** mean every exam's syllabus is independently verified. `UniversalAIContext` explicitly requires an Admin-supplied, verified official syllabus for non-NEET exams and refuses to claim official scope from legacy catalogue entries.
- Admin PDF route is server-Gemini only; it is not yet a provider-neutral Admin multi-provider connector. It currently extracts PDF text and fails to review when no readable text is available; image-only/scanned PDF OCR and image/diagram-aware extraction are not yet integrated in this Admin route.
- Confirmed Universal Admin UI limitation: `ProcessPdfTab` currently exposes subjects from only the first stage and first paper. `UniversalPdfPipeline` also resolves sections without a selected paper and stamps the first configured paper ID. Multi-stage/multi-paper exams therefore need a selected stage/paper flow before this route can be called fully universal. Track as **UAI-001 / High / confirmed source-level**.
- Student and Admin use the shared Universal prompt/context policy, but Student PDF workflows do not use the Admin-only `processAdminUploadedPdf()` route; they use the existing local extraction/OCR + Student provider + universal generation/validation path.

### CI diagnosis and fix
- Integration CI run #222 failed because the workflow used JDK 17 while the project compiles against Android SDK 36.1 and Robolectric reports SDK 36 requires Java 21. This was an environment/toolchain mismatch, not a Kotlin compilation error.
- Updated `.github/workflows/universal-gemini-validation.yml` to JDK 21. Student branch CI run #39 passed Android unit tests and debug APK assembly on JDK 21.
- Added a second authenticated-UID check immediately before sending Student provider requests, preventing a key read for one account from being sent if the signed-in account changes during the operation.
- Integration CI reruns #223, #224 and #225 are pending/in progress at the time of this checkpoint; backend tests passed in the observed #223/#224 jobs. Do not mark integration Android tests or APK assembly passed until a full run completes successfully.

### Branch / duplicate check
- Main remains unchanged.
- Integration branch has the Student connector classes at the same canonical paths as the Student source branch; the overlap is intentional selective porting, not a second set of duplicate classes. The retired Gemini-only store is absent; the migration helper is retained only to clear legacy credentials.
- Branches remain substantially divergent. Continue selective integration only; do not merge either feature branch wholesale.

## Branch comparison checkpoint

At the time of this checkpoint:
- `main` SHA: `dabeeaec90ddfde4fa69f4b89e71a5f2ffe1fcb4` (unchanged by this work).
- Integration branch compared with Main: 385 commits ahead, 108 behind.
- Student source branch compared with Main: 56 commits ahead, 0 behind.
- Student source branch compared with the integration branch: 164 commits ahead, 385 behind.

Because the branches have substantial divergence, do not merge either feature branch wholesale. Student files were ported selectively and adapted to existing AI/Admin call sites. Main has not been merged or modified.

## Integration approach

The Student connector was developed separately, then its reviewed files and feature changes were selectively ported into the existing AI Connector branch. No wholesale merge of the Student branch or Main branch was performed.

## Consolidated Student implementation

- `StudentAIKeyStore.kt` is the single active Student key store for Gemini, OpenAI, Groq, Anthropic and Cerebras.
- Keys are encrypted with AES-GCM using UID/provider-scoped non-exportable AndroidKeyStore keys. Ciphertext and usage preferences are excluded from backup/device transfer.
- `StudentAIProviderClient.kt` is the one direct Student BYOK text transport. Keys are sent only in provider-specific headers; requests use no-store caching and do not put keys in URL parameters.
- `StudentAIProviderSettingsCard.kt` replaces the old Gemini-only key dialog/card in the existing Settings screen. It verifies provider catalogue access, discovers models, saves a preferred provider/model, supports disconnect and displays provider-reported usage.
- `StudentAIUsageStore.kt` stores successful request counts and actual last token metadata locally. It does not estimate monetary cost.
- `LegacyStudentGeminiKeyMigration.kt` is a one-time migration/cleanup helper only. It migrates the old Gemini-only encrypted key into the universal store and removes the old ciphertext/alias. It is not a second active key store.
- The old active `StudentGeminiKeyStore.kt` implementation was deleted after call sites were migrated.
- Student key and usage data are cleared on logout and account switch.

## Existing app feature integration

Student requests now route through the universal provider client in these existing ViewModel workflows:
- AI question generation from the existing PDF/text extraction pipeline.
- PDF metadata/exam recognition requests.
- Smart Doubt Resolver.
- Reported-question error analysis.

The app's existing parsing, validation and approval/publish workflow remains in place. This change does not add a second PDF pipeline or a second Settings screen.

## Admin separation preserved

- Admin requests continue through the existing server-side `callAdminGeminiContent()` callable path.
- Admin connector verification/model selection remain Admin-only.
- Admin secrets are not read by the Student key store and are not sent through Student BYOK requests.
- Student provider UI is hidden for Admin accounts.
- Community Doubts remain manual-only; no Student AI provider was connected to Community Doubts.

## Removed duplicate implementation

| Old implementation | Consolidation |
|---|---|
| `StudentGeminiKeyStore.kt` | Replaced by `StudentAIKeyStore.kt`; legacy encrypted data is migrated once. |
| Gemini-only Settings dialog and menu item | Replaced by one `StudentAIProviderSettingsCard`. |
| Student Gemini key state/connect/disconnect methods | Removed from `NEETViewModel`; provider state and key custody now use the universal store/card. |
| Student Gemini key list and direct Gemini HTTP fallback | Removed; Student generation now calls `StudentAIProviderClient.generatePreferred()`. |
| Student direct Gemini HTTP functions in ViewModel | Removed; one Student provider transport handles the supported providers. |

## Validation and status

| Gate | Status |
|---|---|
| Selective Student files ported | Complete |
| Old Gemini-only Student key store removed | Complete |
| Legacy key migration helper added | Complete; device-level migration test still required |
| Existing Settings screen consolidated | Complete |
| Student generation call sites routed to universal client | Complete for listed workflows |
| Admin PDF route server-only | Source-level change complete; CI pending | Both Admin PDF screens pass `viewModel.generateAiForQuestions`; pipeline refuses to process without the server callback. Legacy direct Gemini transport and fallback generator removed. Question drafts are parsed and returned to the existing review UI; publication still requires Admin approval. |
| Direct Student/legacy Gemini transport audit | Source-level scan | No direct provider HTTP transport remains in `NEETViewModel`; the retired `GeminiPdfExtractor` is fail-closed. Student generation goes through `StudentAIProviderClient`; Admin generation goes through the server connector. |
| Admin server route preserved | Source-level check only |
| Android unit tests on integration branch | Pending CI; earlier compile failure diagnosed and patched | Runs #212–#214 failed at the stale `activeGeminiKeyIndex` reference in `CommunityAdminUI.kt`; removed in later commits. Latest run #220 is in progress. |
| Debug APK build on integration branch | Pending CI | Earlier runs skipped APK assembly after compilation failure; latest run #220 will verify the corrected tree. |
| Real provider calls / AndroidKeyStore device test | Not run |
| Main branch changes | None |
| Production deployment / Firebase rules changes | None |

**Current integration-branch code checkpoint before this documentation update:** `765107b83a1068a75e7d534e876c8720323f4798`. CI run #221: `https://github.com/abhi952817-sudo/AK-TEST-SERIES-/actions/runs/43794194960` (Android unit tests and backend tests in progress). Runs #216–#220 are also in progress. Earlier runs #212–#214 failed Android compilation due a stale `CommunityAdminUI.kt` reference to the removed `activeGeminiKeyIndex`; that UI collection was removed. Backend tests passed in run #212. The cancel-in-progress setting has been removed so future commits will not cancel previous runs. CI is triggered on pushes. A workflow starting is not a pass; record the exact completed run and SHA before marking tests passed.

## Remaining work

1. Review the new integration diff and fix any compile/test failures reported by CI.
2. Verify the legacy Gemini key migration on an Android device/emulator.
3. Verify model catalogue access and one successful generation for each provider using test credentials supplied by the owner; do not commit or log keys.
4. Add provider-specific request limits/JSON mode where supported and bounded cancellation-safe retry only where safe.
5. Review all Student generation call sites to ensure no remaining Gemini-only transport or duplicate route exists.
6. Complete PDF fallback/multimodal integration and feature-level usage display without bypassing Admin approval.
7. Run the full security/regression checklist on this integration branch.
8. Only after review and explicit approval, selectively integrate the reviewed result into Main.
